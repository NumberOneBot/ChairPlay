package io.github.numberonebot.chairplay.gps

import java.util.concurrent.Executors
import java.util.concurrent.ScheduledExecutorService
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean

/**
 * The GPS output's one owner (03 §2): the method chosen, the saved switch, the one writer, the operation holding the
 * app, and the cleanup of a USB simulation. Commands come from the page ([GpsCommands]) and the USB wizard's final
 * button ([UsbSetupPort.onFinish]); everything they start runs on one thread, as does [reconcile], which the channels'
 * changes and a tick each [GpsTiming.tickMillis] run: it keeps the selected channel sending when it can and the other
 * quiet, resumes USB when the iPhone comes back (L-06), and clears a simulation this app left (V49, V50).
 */
class GpsCoordinator(
    store: GpsSettingsStore,
    car: GpsCar,
    carplay: GpsChannel,
    usb: GpsChannel,
    setup: UsbSetupPort,
    locator: ReturnLocator,
    journal: CleanupJournal,
    hold: InputHold = InputHold.NONE,
    private val log: (String) -> Unit = {},
    private val clock: () -> Long = System::currentTimeMillis,
    private val timing: GpsTiming = GpsTiming(),
    private val worker: ScheduledExecutorService = daemonThread("gps-coordinator"),
    watchdog: ScheduledExecutorService = daemonThread("gps-watchdog"),
) : GpsCommands {
    /** Set by the app: what [output] or [usbSetup] returns changed. */
    @Volatile var changed: () -> Unit = {}

    private val notices = GpsNotices(clock)
    private val ops = GpsOperations(clock, hold, worker, watchdog, timing.operationMillis, log) { changed() }
    private val flows = GpsFlows(store, car, carplay, usb, setup, locator, journal, notices, ops, timing, log)
    private val queued = AtomicBoolean()
    private var wizardWasOpen = false

    init {
        carplay.onChanged = ::poke
        usb.onChanged = ::poke
        setup.onFinish = ::finishSetup
        setup.onChanged = { changed() }
    }

    /** Starts looking after the output: a reconcile now and each tick. */
    fun start() {
        worker.scheduleWithFixedDelay(::reconcile, 0, timing.tickMillis, TimeUnit.MILLISECONDS)
    }

    /** Asks for a reconcile soon, once however often asked meanwhile. */
    fun poke() {
        if (queued.compareAndSet(false, true)) worker.execute { queued.set(false); reconcile() }
    }

    override fun usbSetup(): UsbSetupView = flows.setup.view()

    override fun selectMethod(method: GpsMethod, generation: Long): CommandOutcome {
        val (from, operation) = synchronized(flows.lock) {
            if (ops.current != null) return CommandOutcome.BUSY
            if (generation != flows.generation) return CommandOutcome.STALE
            if (method == flows.selected) return CommandOutcome.NO_CHANGE
            val from = flows.selected
            // The choice is saved before anything that may fail or take long: nothing after vetoes it (V15).
            flows.selected = method
            flows.generation++
            flows.reason = null
            flows.store.save(mapOf("gps.method" to method.key))
            notices.drop(NoticeCode.USB_UNAVAILABLE, NoticeCode.START_FAILED)
            from to ops.begin(OperationKind.SWITCH, flows.generation, method)
        }
        log("GPS: method ${from.key} -> ${method.key}")
        operation?.let { ops.run(it) { flows.switch(it, from) } }
        return CommandOutcome.ACCEPTED
    }

    override fun setEnabled(enabled: Boolean): CommandOutcome {
        synchronized(flows.lock) {
            if (ops.current != null) return CommandOutcome.BUSY
            if (enabled == flows.enabled) return CommandOutcome.NO_CHANGE
        }
        if (enabled && flows.selected == GpsMethod.USB) return enableUsb()
        val operation = synchronized(flows.lock) {
            flows.enabled = enabled
            flows.reason = null
            flows.store.save(mapOf("gps.enabled" to enabled.toString()))
            val ours = flows.usb.running || flows.journal.dirty() != null
            if (!enabled && flows.selected == GpsMethod.USB && ours) ops.begin(OperationKind.DISABLE, flows.generation, null) else null
        }
        log("GPS: simulation ${if (enabled) "on" else "off"}")
        if (operation != null) ops.run(operation) { flows.stopUsb(operation) } else poke()
        return CommandOutcome.ACCEPTED
    }

    // Enable with USB selected: a never-prepared iPhone opens the wizard at once; else the iPhone is checked, and
    // opens the wizard unless it is ready. Until the wizard's final Enable nothing is saved (F-14, V56).
    private fun enableUsb(): CommandOutcome {
        if (flows.store.current().usbDevice == null) return wizard(SetupIntent.ENABLE)
        val operation = synchronized(flows.lock) { ops.begin(OperationKind.ENABLE, flows.generation, GpsMethod.USB) }
            ?: return CommandOutcome.BUSY
        ops.run(operation) {
            when (val ready = flows.setup.readiness()) {
                is UsbReadiness.NotReady -> flows.setup.open(SetupIntent.ENABLE)
                is UsbReadiness.Ready -> {
                    saveEnabled()
                    flows.enableUsb(operation, ready.device)
                }
            }
        }
        return CommandOutcome.ACCEPTED
    }

    private fun wizard(intent: SetupIntent): CommandOutcome =
        flows.setup.open(intent).let { if (it == CommandOutcome.ACCEPTED) CommandOutcome.SETUP_OPENED else it }

    override fun openUsbSetup(intent: SetupIntent): CommandOutcome {
        if (ops.current != null) return CommandOutcome.BUSY
        return flows.setup.open(intent)
    }

    override fun usbSetupAction(operationId: Long, action: SetupAction, step: SetupStep?): CommandOutcome =
        flows.setup.act(operationId, action, step)

    /** The wizard's ENABLE or DONE, its readiness just checked: the prepared iPhone is saved, and ENABLE starts USB. */
    private fun finishSetup(operationId: Long, device: UsbDevice, enable: Boolean): CommandOutcome {
        flows.store.save(mapOf("gps.usb.device" to device.udid))
        if (!enable) return CommandOutcome.ACCEPTED.also { poke() }
        val (from, operation) = synchronized(flows.lock) {
            val from = flows.selected
            val kind = if (from == GpsMethod.USB) OperationKind.ENABLE else OperationKind.SWITCH
            if (from != GpsMethod.USB) flows.generation++
            val operation = ops.begin(kind, flows.generation, GpsMethod.USB) ?: return CommandOutcome.BUSY
            flows.selected = GpsMethod.USB
            flows.store.save(mapOf("gps.method" to "usb"))
            from to operation
        }
        saveEnabled()
        log("GPS: USB setup finished with Enable USB GPS (wizard operation $operationId)")
        ops.run(operation) {
            if (from == GpsMethod.USB) flows.enableUsb(operation, device) else flows.switch(operation, from)
        }
        return CommandOutcome.ACCEPTED
    }

    private fun saveEnabled() = synchronized(flows.lock) {
        flows.enabled = true
        flows.store.save(mapOf("gps.enabled" to "true"))
    }

    /** On the coordinator's thread, between operations: see the class. */
    private fun reconcile() {
        if (ops.current != null) return
        val (selected, enabled) = synchronized(flows.lock) { flows.selected to flows.enabled }
        // One writer: the method not chosen never sends.
        flows.channel(if (selected == GpsMethod.USB) GpsMethod.CARPLAY else GpsMethod.USB).takeIf { it.running }?.quiesce()
        val usbState = flows.usb.state()
        val wizardOpen = flows.setup.view().open
        if (flows.cleanup == Cleanup.NEEDED && !flows.usb.running) synchronized(flows.lock) {
            // The cable or the helper went (L-03): the iPhone may still simulate, unconfirmed.
            flows.cleanup = Cleanup.UNCONFIRMED
            log("GPS: USB simulation lost; the switch stays on")
        }
        // A wizard closed without its final Enable does not start USB on the next tick (F-14); the next arrival may.
        if (wizardWasOpen && !wizardOpen) flows.resumeArmed = false
        wizardWasOpen = wizardOpen
        if (usbState.device == null) flows.resumeArmed = true
        val usbWanted = selected == GpsMethod.USB && enabled
        when {
            !wizardOpen && usbState.device != null && !flows.usb.running && flows.journal.dirty() != null && !usbWanted ->
                begin(OperationKind.RESTORE) { flows.restore(it) }
            usbWanted && !flows.usb.running && !wizardOpen -> resumeUsb(usbState)
            selected == GpsMethod.CARPLAY && enabled && !flows.carplay.running -> flows.startSelected(wizardBounds = true)
            !enabled && flows.channel(selected).running -> flows.channel(selected).quiesce()
        }
        keepNotices(selected, enabled)
        // The page hears of a change only: the tick alone tells it nothing.
        val now = output()
        if (now != lastOutput) changed()
        lastOutput = now
    }

    private var lastOutput: GpsOutput? = null

    private fun resumeUsb(state: ChannelState) {
        if (state.device == null) {
            synchronized(flows.lock) { flows.reason = GpsReason.USB_NOT_CONNECTED }
            return
        }
        if (!flows.resumeArmed) return
        flows.resumeArmed = false
        begin(OperationKind.RESUME) { flows.resume(it) }
    }

    private fun begin(kind: OperationKind, body: (GpsOperation) -> Unit) {
        val operation = synchronized(flows.lock) { ops.begin(kind, flows.generation, null) } ?: return
        ops.run(operation) { body(operation) }
    }

    private fun keepNotices(selected: GpsMethod, enabled: Boolean) {
        val session = flows.carplay.state().sessionGps
        // D4: a connected session that identified otherwise than the method needs waits for the user's reconnect.
        notices.keep(NoticeCode.RECONNECT_NEEDED, NoticeTone.INFO, session != null && session != (enabled && selected == GpsMethod.CARPLAY))
        val missing = selected == GpsMethod.USB && enabled && !flows.usb.running && flows.reason == GpsReason.USB_NOT_CONNECTED
        notices.keep(NoticeCode.USB_UNAVAILABLE, NoticeTone.WARN, missing)
    }

    override fun output(): GpsOutput = synchronized(flows.lock) { GpsOutputs.of(flows, ops.current, notices.current()) }

    /** The app closes: a USB simulation goes as an explicit stop's does, within [GpsTiming.shutdownMillis]; the switch stays saved. */
    fun close() {
        val stopping = Thread({
            runCatching {
                flows.carplay.quiesce()
                if (flows.usb.running || flows.journal.dirty() != null && flows.usb.state().device != null) {
                    val operation = synchronized(flows.lock) { ops.begin(OperationKind.SHUTDOWN, flows.generation, null) }
                    if (operation != null) flows.stopUsb(operation)
                }
            }.onFailure { log("GPS: closing failed: $it") }
        }, "gps-close").apply { isDaemon = true; start() }
        stopping.join(timing.shutdownMillis)
        if (stopping.isAlive) log("GPS: the USB simulation's end did not finish in ${timing.shutdownMillis} ms")
        worker.shutdownNow()
    }

    private companion object {
        fun daemonThread(name: String): ScheduledExecutorService =
            Executors.newSingleThreadScheduledExecutor { Thread(it, name).apply { isDaemon = true } }
    }
}
