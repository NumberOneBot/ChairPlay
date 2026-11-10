package io.github.numberonebot.chairplay.gps

import io.github.numberonebot.chairplay.drive.GeoPoint

/**
 * The GPS output's mutable state, guarded by its own lock, and the bodies of its operations, which run on the
 * coordinator's one thread: putting the chosen method in force at a connection (D8), turning USB on and off, and
 * getting it back. [GpsCoordinator] takes the commands; this does what they start.
 */
internal class GpsFlows(
    val store: GpsSettingsStore,
    private val car: GpsCar,
    val carplay: GpsChannel,
    val usb: GpsChannel,
    val setup: UsbSetupPort,
    private val locator: ReturnLocator,
    val notices: GpsNotices,
    val ops: GpsOperations,
    private val timing: GpsTiming,
    private val log: (String) -> Unit,
) {
    val lock = Any()

    /** The method the user chose, saved as gps.method. */
    var selected: GpsMethod

    /** The method in force: the one the app started with, or the last connection put in force (D8). */
    var applied: GpsMethod

    var enabled: Boolean
    var generation = 1L

    /** Why the method in force did not start the last time it was asked to; null once it sends. */
    var reason: GpsReason? = null

    /** A USB iPhone's arrival may resume the saved simulation once (L-10); armed again as it goes away. */
    var resumeArmed = true

    init {
        val saved = store.current()
        selected = saved.method
        applied = saved.method
        enabled = saved.enabled
    }

    fun channel(method: GpsMethod) = if (method == GpsMethod.USB) usb else carplay

    fun hasPosition() = car.position() != null

    /** D8: a connection puts the chosen method in force: the old one stops as an explicit stop does, the new one may start. */
    fun apply(operation: GpsOperation, from: GpsMethod) {
        if (from == GpsMethod.USB && usb.running) stopUsb(operation) else {
            ops.stage(operation, OperationStage.QUIESCE)
            channel(from).quiesce()
        }
        synchronized(lock) {
            applied = selected
            reason = null
        }
        log("GPS: ${from.key} -> ${selected.key} in force from this connection")
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

    /** L-06: the saved USB simulation again, the iPhone back. */
    fun resume(operation: GpsOperation) {
        ops.stage(operation, OperationStage.START)
        startSelected(wizardBounds = true, operation = operation)
    }

    /** The explicit Enable: USB was checked ready just now (the wizard's final button, or the switch). */
    fun enableUsb(operation: GpsOperation, device: UsbDevice) {
        ops.stage(operation, OperationStage.START)
        val seed = car.position()
        if (seed == null) synchronized(lock) { reason = GpsReason.NO_POSITION } else startUsb(seed, device)
    }

    /**
     * Starts the method in force when the switch is on and the car is somewhere; else records why not. With
     * [wizardBounds], an open USB wizard keeps USB from starting: its final button does (F-14).
     */
    fun startSelected(wizardBounds: Boolean, operation: GpsOperation? = null) {
        val (method, on) = synchronized(lock) { applied to enabled }
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
        val why = usb.start(seed, device)
        synchronized(lock) { reason = why }
        log(if (why == null) "GPS: USB simulation started" else "GPS: USB simulation did not start: ${why.key}")
    }

    /** Windows' place now, or null with the warning shown; a late answer is the locator's to drop (V47). */
    private fun locate(): GeoPoint? = when (val back = locator.locate(timing.locateMillis)) {
        is ReturnPoint.Found -> back.point.also { notices.drop(NoticeCode.WINDOWS_UNAVAILABLE) }
        is ReturnPoint.Unavailable -> null.also {
            notices.show(NoticeCode.WINDOWS_UNAVAILABLE, NoticeTone.WARN, mapOf("reason" to back.reason))
            log("GPS: no Windows position to return to (${back.reason})")
        }
    }

    /** Return, clear and close, each tried whatever the one before did (L-01); a clear sent is all there is to know (D10). */
    private fun letGo(operation: GpsOperation, point: GeoPoint?) {
        val release = usb.release(point) { ops.stage(operation, it) }
        log("GPS: USB let go: return ${release.returned}, clear ${release.cleared}, close ${release.closed}")
        if (release.cleared == StepOutcome.DONE) {
            notices.show(NoticeCode.RESET_SENT, NoticeTone.INFO, forMillis = timing.resetNoticeMillis)
        }
    }
}
