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
    val ops: GpsOperations,
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

    /** L-01: stream off, then close; nothing clears the simulated position (D14), the car stays where it is (L-02). */
    fun stopUsb(operation: GpsOperation) {
        ops.stage(operation, OperationStage.QUIESCE)
        usb.quiesce()
        letGo(operation)
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

    /** The session's close, and no more (D14). */
    private fun letGo(operation: GpsOperation) {
        val closed = usb.release { ops.stage(operation, it) }
        log("GPS: USB let go: close $closed")
    }
}
