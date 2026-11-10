package io.github.numberonebot.chairplay.gps

import io.github.numberonebot.chairplay.drive.GeoPoint

/**
 * The GPS output's mutable state, guarded by its own lock, and the bodies of its operations, which run on the
 * coordinator's one thread: switching the method (03 §5), turning USB on and off, getting it back, and letting an old
 * USB simulation go. [GpsCoordinator] takes the commands; this does what they start.
 */
internal class GpsFlows(
    val store: GpsSettingsStore,
    private val car: GpsCar,
    val carplay: GpsChannel,
    val usb: GpsChannel,
    val setup: UsbSetupPort,
    private val locator: ReturnLocator,
    val journal: CleanupJournal,
    val notices: GpsNotices,
    val ops: GpsOperations,
    private val timing: GpsTiming,
    private val log: (String) -> Unit,
) {
    val lock = Any()
    var selected: GpsMethod
    var enabled: Boolean
    var generation = 1L

    /** Why the selected channel did not start the last time it was asked to; null once it sends. */
    var reason: GpsReason? = null

    var cleanup: Cleanup

    /** A USB iPhone's arrival may resume the saved simulation once (L-10); armed again as it goes away. */
    var resumeArmed = true

    init {
        val saved = store.current()
        selected = saved.method
        enabled = saved.enabled
        // A record left by a run that ended without its clear (a crash): the iPhone may still simulate (V49).
        cleanup = if (journal.dirty() != null) Cleanup.UNCONFIRMED else Cleanup.CLEAN
    }

    fun channel(method: GpsMethod) = if (method == GpsMethod.USB) usb else carplay

    fun hasPosition() = car.position() != null

    /** S-01…S-09: the old writer stops first, the car goes to Windows' place when there is one, then the new one starts. */
    fun switch(operation: GpsOperation, from: GpsMethod) {
        val old = channel(from)
        val wasSending = old.running
        ops.stage(operation, OperationStage.QUIESCE)
        old.quiesce()
        ops.stage(operation, OperationStage.LOCATE)
        val back = locate()
        if (!ops.live(operation)) return
        if (back != null) car.place(back)
        if (from == GpsMethod.USB && (wasSending || journal.dirty() != null)) letGo(operation, back)
        if (!ops.live(operation)) return
        ops.stage(operation, OperationStage.START)
        startSelected(wizardBounds = true, operation = operation)
    }

    /** L-01: stream off, Windows' place once, clear, close; the car stays where it is (L-02). */
    fun stopUsb(operation: GpsOperation) {
        ops.stage(operation, OperationStage.QUIESCE)
        usb.quiesce()
        ops.stage(operation, OperationStage.LOCATE)
        letGo(operation, locate())
    }

    /** L-06: the old session's clear first, as an operation of its own, then the saved simulation again. */
    fun resume(operation: GpsOperation) {
        if (journal.dirty() != null) letGo(operation, null)
        if (!ops.live(operation)) return
        ops.stage(operation, OperationStage.START)
        startSelected(wizardBounds = true, operation = operation)
    }

    /** V49, V50: an iPhone this app left simulating is back while nothing of ours sends: its clear, nothing else. */
    fun restore(operation: GpsOperation) = letGo(operation, null)

    /** The explicit Enable: USB was checked ready just now (the wizard's final button, or the switch). */
    fun enableUsb(operation: GpsOperation, device: UsbDevice) {
        ops.stage(operation, OperationStage.START)
        val seed = car.position()
        synchronized(lock) { if (seed == null) reason = GpsReason.NO_POSITION }
        if (seed != null) startUsb(seed, device)
    }

    /**
     * Starts the selected channel when the switch is on and the car is somewhere; else records why not. With
     * [wizardBounds], an open USB wizard keeps USB from starting: its final button does (F-14).
     */
    fun startSelected(wizardBounds: Boolean, operation: GpsOperation? = null) {
        val (method, on) = synchronized(lock) { selected to enabled }
        if (!on) {
            synchronized(lock) { reason = null }
            return
        }
        val seed = car.position()
        if (seed == null) {
            synchronized(lock) { reason = GpsReason.NO_POSITION }
            return
        }
        if (method == GpsMethod.CARPLAY) {
            val why = carplay.state().reason ?: carplay.start(seed, null)
            synchronized(lock) { reason = why }
            return
        }
        if (wizardBounds && setup.view().open) return
        val ready = setup.readiness()
        // An operation the deadline ended while readiness was asked starts nothing more.
        if (operation != null && !ops.live(operation)) return
        when (ready) {
            is UsbReadiness.NotReady -> synchronized(lock) { reason = ready.reason }
            is UsbReadiness.Ready -> startUsb(seed, ready.device)
        }
    }

    private fun startUsb(seed: GeoPoint, device: UsbDevice) {
        val earlier = journal.dirty()
        earlier?.takeIf { it != device.udid }?.let { log("GPS: an older USB simulation's record gives way to this iPhone's") }
        // Written before the first set, so a crash right after still knows the iPhone may simulate (V49).
        journal.mark(device.udid)
        val why = usb.start(seed, device)
        synchronized(lock) {
            reason = why
            if (why == null) cleanup = Cleanup.NEEDED
        }
        // Nothing went (a start fails before its first set): the journal is as it was.
        if (why != null) if (earlier == null) journal.clear() else journal.mark(earlier)
        log(if (why == null) "GPS: USB simulation started" else "GPS: USB simulation did not start: ${why.key}")
    }

    /** Windows' place now, or null with the warning shown; a late answer is the locator's to drop (V47). */
    private fun locate(): GeoPoint? = when (val back = locator.locate(timing.locateMillis)) {
        is ReturnPoint.Found -> back.point.also { notices.drop(NoticeCode.WINDOWS_UNAVAILABLE) }
        is ReturnPoint.Unavailable -> null.also {
            notices.show(NoticeCode.WINDOWS_UNAVAILABLE, NoticeTone.WARN, mapOf("reason" to back.reason))
            log("GPS: no Windows position to return to (${back.reason}); the car stays where it is")
        }
    }

    /**
     * Return, clear and close of USB's simulation, each tried whatever the one before did (L-01). What it did stays,
     * even for an operation the deadline ended: it is what the iPhone was told.
     */
    private fun letGo(operation: GpsOperation, point: GeoPoint?) {
        synchronized(lock) { cleanup = Cleanup.ATTEMPTING }
        val release = usb.release(point) { ops.stage(operation, it) }
        log("GPS: USB let go: return ${release.returned}, clear ${release.cleared}, close ${release.closed}")
        synchronized(lock) {
            if (release.cleared == StepOutcome.DONE) {
                journal.clear()
                cleanup = Cleanup.CLEAN
                notices.drop(NoticeCode.CLEANUP_UNCONFIRMED)
                if (release.closed == StepOutcome.DONE) {
                    notices.show(NoticeCode.RESET_SENT, NoticeTone.INFO, forMillis = timing.resetNoticeMillis)
                }
            } else {
                cleanup = Cleanup.UNCONFIRMED
                notices.show(NoticeCode.CLEANUP_UNCONFIRMED, NoticeTone.WARN)
            }
        }
    }
}
