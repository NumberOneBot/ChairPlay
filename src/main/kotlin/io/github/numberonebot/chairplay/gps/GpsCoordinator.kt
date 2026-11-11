package io.github.numberonebot.chairplay.gps

import java.util.concurrent.Executors
import java.util.concurrent.ScheduledExecutorService
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean

/**
 * The GPS output's one owner (03 §2): the method chosen and the one in force, the saved switch, the one writer, and
 * the operation the switch waits for. The method the user chooses is only saved; the next CarPlay connection puts it in
 * force ([connected], D8). Commands come from the page ([GpsCommands]) and the USB wizard's final button
 * ([UsbSetupPort.onFinish]); what they start runs on one thread, as does [reconcile], which the channels' changes and a
 * tick each [GpsTiming.tickMillis] run: it keeps the method in force sending when it can and the other quiet, and
 * resumes USB when the iPhone comes back (L-06).
 */
class GpsCoordinator(
    store: GpsSettingsStore,
    car: GpsCar,
    carplay: GpsChannel,
    usb: GpsChannel,
    setup: UsbSetupPort,
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
    // An operation's every step tells the page; its end also asks for a reconcile, which waits while one runs.
    private val ops = GpsOperations(clock, hold, worker, watchdog, timing.operationMillis, log) { changed(); poke() }
    private val flows = GpsFlows(store, car, carplay, usb, setup, ops, log)
    private val queued = AtomicBoolean()
    @Volatile private var connecting = false
    private var wizardWasOpen = false
    private var lastOutput: GpsOutput? = null

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

    /** A CarPlay bring-up read the settings: the method chosen since goes in force with it (D8). */
    fun connected() {
        connecting = true
        poke()
    }

    override fun usbSetup(): UsbSetupView = flows.setup.view()

    /** D8: only saved; the next connection puts it in force, and the page says a reconnect is due. */
    override fun selectMethod(method: GpsMethod, generation: Long): CommandOutcome {
        synchronized(flows.lock) {
            if (generation != flows.generation) return CommandOutcome.STALE
            if (method == flows.selected) return CommandOutcome.NO_CHANGE
            flows.selected = method
            flows.generation++
            flows.store.save(mapOf("gps.method" to method.key))
        }
        log("GPS: method ${method.key} chosen; in force from the next connection")
        poke()
        return CommandOutcome.ACCEPTED
    }

    override fun setEnabled(enabled: Boolean): CommandOutcome {
        synchronized(flows.lock) {
            if (ops.current != null) return CommandOutcome.BUSY
            if (enabled == flows.enabled) return CommandOutcome.NO_CHANGE
        }
        if (enabled && flows.applied == GpsMethod.USB) return enableUsb()
        val operation = synchronized(flows.lock) {
            flows.enabled = enabled
            flows.reason = null
            flows.store.save(mapOf("gps.enabled" to enabled.toString()))
            if (!enabled && flows.usb.running) ops.begin(OperationKind.DISABLE, flows.generation, null) else null
        }
        log("GPS: simulation ${if (enabled) "on" else "off"}")
        if (operation != null) ops.run(operation) { flows.stopUsb(operation) } else poke()
        return CommandOutcome.ACCEPTED
    }

    // Enable with USB in force: a never-prepared iPhone opens the wizard at once; else the iPhone is checked, and
    // opens the wizard unless it is ready. Until the wizard's final Enable nothing is saved (F-14, V56).
    private fun enableUsb(): CommandOutcome {
        if (flows.store.current().usbDevice == null) {
            return flows.setup.open(SetupIntent.ENABLE).let { if (it == CommandOutcome.ACCEPTED) CommandOutcome.SETUP_OPENED else it }
        }
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

    override fun openUsbSetup(intent: SetupIntent): CommandOutcome {
        if (ops.current != null) return CommandOutcome.BUSY
        return flows.setup.open(intent)
    }

    override fun usbSetupAction(operationId: Long, action: SetupAction, step: SetupStep?): CommandOutcome =
        flows.setup.act(operationId, action, step)

    /**
     * The wizard's ENABLE or DONE, its readiness just checked: the prepared iPhone is saved; ENABLE saves the switch on
     * and USB chosen, and starts USB when it is in force (else the next connection does).
     */
    private fun finishSetup(operationId: Long, device: UsbDevice, enable: Boolean): CommandOutcome {
        flows.store.save(mapOf("gps.usb.device" to device.udid))
        synchronized(flows.lock) { flows.reason = null }
        if (!enable) return CommandOutcome.ACCEPTED.also { poke() }
        val operation = synchronized(flows.lock) {
            if (flows.selected != GpsMethod.USB) {
                flows.selected = GpsMethod.USB
                flows.generation++
                flows.store.save(mapOf("gps.method" to "usb"))
            }
            if (flows.applied == GpsMethod.USB) ops.begin(OperationKind.ENABLE, flows.generation, GpsMethod.USB) ?: return CommandOutcome.BUSY
            else null
        }
        saveEnabled()
        log("GPS: USB setup finished with Enable USB GPS (wizard operation $operationId)")
        if (operation != null) ops.run(operation) { flows.enableUsb(operation, device) } else poke()
        return CommandOutcome.ACCEPTED
    }

    private fun saveEnabled() = synchronized(flows.lock) {
        flows.enabled = true
        flows.store.save(mapOf("gps.enabled" to "true"))
    }

    /** On the coordinator's thread, between operations: see the class. */
    private fun reconcile() {
        if (ops.current != null) return
        val (selected, applied, enabled) = synchronized(flows.lock) { Triple(flows.selected, flows.applied, flows.enabled) }
        if (connecting) {
            connecting = false
            if (selected != applied) return begin(OperationKind.APPLY) { flows.apply(it, applied) }
        }
        // Off, nothing sends whatever the method: one chosen is in force at once, with no reconnect.
        if (!enabled && selected != applied && !flows.carplay.running && !flows.usb.running) {
            synchronized(flows.lock) { flows.applied = selected }
            log("GPS: ${selected.key} in force at once, the simulation being off")
            return reconcile()
        }
        // One writer: the method not in force never sends.
        flows.channel(if (applied == GpsMethod.USB) GpsMethod.CARPLAY else GpsMethod.USB).takeIf { it.running }?.quiesce()
        val usbState = flows.usb.state()
        val wizardOpen = flows.setup.view().open
        // A wizard closed without its final Enable does not start USB on the next tick (F-14); the next arrival may.
        if (wizardWasOpen && !wizardOpen) flows.resumeArmed = false
        wizardWasOpen = wizardOpen
        if (usbState.device == null) flows.resumeArmed = true
        when {
            applied == GpsMethod.USB && enabled && !flows.usb.running && !wizardOpen -> resumeUsb(usbState)
            applied == GpsMethod.CARPLAY && enabled && !flows.carplay.running -> flows.startSelected(wizardBounds = true)
            !enabled && flows.channel(applied).running -> flows.channel(applied).quiesce()
        }
        keepNotices(applied, enabled)
        // The page hears of a change only: the tick alone tells it nothing.
        val now = output()
        if (now != lastOutput) changed()
        lastOutput = now
    }

    private fun resumeUsb(state: ChannelState) {
        if (state.device == null) {
            synchronized(flows.lock) { flows.reason = GpsReason.USB_NOT_CONNECTED }
            return
        }
        // An explicit Enable that waited for the car's place goes once the user places it (L-09), armed or not.
        val placedSince = flows.reason == GpsReason.NO_POSITION && flows.hasPosition()
        if (!flows.resumeArmed && !placedSince) return
        flows.resumeArmed = false
        begin(OperationKind.RESUME) { flows.resume(it) }
    }

    private fun begin(kind: OperationKind, body: (GpsOperation) -> Unit) {
        val operation = synchronized(flows.lock) { ops.begin(kind, flows.generation, null) } ?: return
        ops.run(operation) { body(operation) }
    }

    // A reconnect that waits is the settings' own mark (UiSettings' reconnect pending), not a notice of this.
    private fun keepNotices(applied: GpsMethod, enabled: Boolean) {
        val missing = applied == GpsMethod.USB && enabled && !flows.usb.running && flows.reason == GpsReason.USB_NOT_CONNECTED
        notices.keep(NoticeCode.USB_UNAVAILABLE, NoticeTone.WARN, missing)
    }

    /** Whether the car may drive: the switch on, and CarPlay in force or USB sending; USB on without a cable stands (P-06). */
    fun carLive(): Boolean = synchronized(flows.lock) {
        flows.enabled && (flows.applied == GpsMethod.CARPLAY || flows.usb.running)
    }

    /** Whether an operation runs: the switch waits and the car stands (D9). */
    val holding: Boolean get() = ops.current != null

    override fun output(): GpsOutput = synchronized(flows.lock) { GpsOutputs.of(flows, ops.current, notices.current()) }

    /** The app closes: a USB simulation goes as an explicit stop's does, within [GpsTiming.shutdownMillis]; the switch stays saved. */
    fun close() {
        val stopping = Thread({
            runCatching {
                flows.carplay.quiesce()
                if (flows.usb.running) {
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
