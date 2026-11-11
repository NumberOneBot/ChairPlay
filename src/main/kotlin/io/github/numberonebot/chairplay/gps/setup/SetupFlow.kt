package io.github.numberonebot.chairplay.gps.setup

import io.github.numberonebot.chairplay.gps.AppleComponents
import io.github.numberonebot.chairplay.gps.DeveloperModeStatus
import io.github.numberonebot.chairplay.gps.SetupStep
import io.github.numberonebot.chairplay.gps.StepState
import io.github.numberonebot.chairplay.gps.StepStatus
import io.github.numberonebot.chairplay.gps.UsbDevice
import io.github.numberonebot.chairplay.gps.UsbErrorCode
import io.github.numberonebot.chairplay.gps.UsbHelper
import io.github.numberonebot.chairplay.gps.UsbProbe
import io.github.numberonebot.chairplay.gps.UsbResult
import io.github.numberonebot.chairplay.platform.LocationResult

/** What a run starts from: Retry re-checks one step, not the ones before it. */
internal enum class Stage { USB, PROBE, TOOLS, READY }

/**
 * The checks of the wizard, in order. Each asks the helper for the real state of one prerequisite, writes the step
 * (only while the run is current and the phone is the one asked about) and says whether the next one may be checked.
 * A check lost to a passing disconnect never turns a finished step into a missing one (unknown is not missing).
 */
internal class SetupFlow(
    private val helper: UsbHelper,
    private val store: SetupStore,
    private val executor: SetupExecutor,
    private val locate: () -> LocationResult,
    private val positionValid: () -> Boolean,
    private val locateDeadlineMillis: Long,
) {
    /** True when every check passed and Ready was reached; false when one stopped the run (its step says why). */
    fun run(s: SetupSession, from: Stage): Boolean {
        var stage = from
        while (!s.cancelled) {
            val go = when (stage) {
                Stage.USB -> checkUsb(s)
                Stage.PROBE -> checkProbe(s)
                Stage.TOOLS -> checkTools(s)
                Stage.READY -> { checkReady(s); return !s.cancelled }
            }
            if (!go) return false
            stage = Stage.entries[stage.ordinal + 1]
        }
        return false
    }

    /** Which stage re-checks [step]. */
    fun stageOf(step: SetupStep) = when (step) {
        SetupStep.USB -> Stage.USB
        SetupStep.TRUST, SetupStep.DEVELOPER_MODE -> Stage.PROBE
        SetupStep.TOOLS -> Stage.TOOLS
        SetupStep.READY -> Stage.READY
    }

    private fun set(s: SetupSession, step: SetupStep, state: StepState, udid: String? = null) =
        store.mutate(s, udid) { steps[step] = state }

    private fun stop(s: SetupSession, step: SetupStep, state: StepState, udid: String? = null): Boolean {
        set(s, step, state, udid)
        return false
    }

    /** A failure that says nothing about the prerequisite (timeout, helper trouble): a finished step stays finished. */
    private fun soft(s: SetupSession, step: SetupStep, state: StepState, udid: String? = null): Boolean {
        if (store.read(s) { isDone(step) }) return false
        return stop(s, step, state, udid)
    }

    private fun usbError(code: UsbErrorCode) = StepState(StepStatus.ERROR, null, code.key)

    // ---- USB ----

    private fun checkUsb(s: SetupSession): Boolean {
        when (helper.components()) {
            AppleComponents.MISSING -> return stop(s, SetupStep.USB, StepState(StepStatus.ACTION, "drivers-missing"))
            AppleComponents.NOT_RUNNING -> return stop(s, SetupStep.USB, StepState(StepStatus.ACTION, "apple-devices-closed"))
            else -> {}
        }
        return when (val r = helper.devices()) {
            is UsbResult.Ok -> when {
                r.value.isEmpty() -> noDevice(s)
                // Several iPhones never really happen (D11): the first one.
                else -> adopt(s, r.value.first())
            }
            is UsbResult.Failed -> when (r.code) {
                UsbErrorCode.NO_DEVICE, UsbErrorCode.LINK_LOST -> noDevice(s)
                UsbErrorCode.DRIVERS_MISSING -> stop(s, SetupStep.USB, StepState(StepStatus.ACTION, "drivers-missing"))
                UsbErrorCode.USBMUX_UNAVAILABLE -> stop(s, SetupStep.USB, StepState(StepStatus.ACTION, "apple-devices-closed"))
                UsbErrorCode.LOCKED -> soft(s, SetupStep.USB, StepState(StepStatus.WAITING, "locked"))
                else -> soft(s, SetupStep.USB, usbError(r.code))
            }
        }
    }

    private fun noDevice(s: SetupSession): Boolean {
        if (store.read(s) { device } != null) return lost(s)
        return stop(s, SetupStep.USB, StepState(StepStatus.WAITING, "no-device"))
    }

    /** The one phone is [d]. A phone other than the one the results are for starts over. */
    private fun adopt(s: SetupSession, d: UsbDevice): Boolean {
        store.mutate(s) {
            if (device != null && device?.udid != d.udid) {
                for (step in SetupStep.entries.drop(1)) steps[step] = StepState(StepStatus.UNKNOWN)
                revealed = false
                download = null
            }
            device = d
            steps[SetupStep.USB] = StepState(StepStatus.DONE)
        }
        return !s.cancelled
    }

    /**
     * The link to the known phone is gone. Developer Mode waits in place with "link-lost" (never "rebooting": a lost
     * link does not say why); any other step falls back to waiting for the cable, and finished steps stay finished.
     */
    fun lost(s: SetupSession): Boolean {
        store.mutate(s) {
            download = null
            if (current() == SetupStep.DEVELOPER_MODE) {
                steps[SetupStep.DEVELOPER_MODE] = StepState(StepStatus.WAITING, "link-lost")
            } else {
                steps[SetupStep.USB] = StepState(StepStatus.WAITING, "no-device")
                if (state(SetupStep.TOOLS).status == StepStatus.WORKING) steps[SetupStep.TOOLS] = StepState(StepStatus.UNKNOWN)
            }
        }
        return false
    }

    // ---- Trust and Developer Mode, from one probe ----

    private fun checkProbe(s: SetupSession): Boolean {
        val udid = store.read(s) { device }?.udid ?: return false
        return when (val r = helper.probe(udid)) {
            is UsbResult.Ok -> applyProbe(s, udid, r.value)
            is UsbResult.Failed -> when (r.code) {
                UsbErrorCode.NO_DEVICE, UsbErrorCode.LINK_LOST -> lost(s)
                UsbErrorCode.NOT_TRUSTED -> stop(s, SetupStep.TRUST, StepState(StepStatus.WAITING, "trust-prompt"), udid)
                UsbErrorCode.LOCKED -> locked(s, udid)
                else -> soft(s, if (store.read(s) { isDone(SetupStep.TRUST) }) SetupStep.DEVELOPER_MODE else SetupStep.TRUST, usbError(r.code), udid)
            }
        }
    }

    /** Trust is lost only when the phone says so: a locked phone keeps a trust that was seen. */
    private fun locked(s: SetupSession, udid: String): Boolean {
        val trusted = store.read(s) { isDone(SetupStep.TRUST) }
        if (!trusted) return stop(s, SetupStep.TRUST, StepState(StepStatus.WAITING, "locked"), udid)
        if (store.read(s) { isDone(SetupStep.DEVELOPER_MODE) }) return true
        return stop(s, SetupStep.DEVELOPER_MODE, StepState(StepStatus.WAITING, if (store.read(s) { revealed }) "revealed" else null), udid)
    }

    private fun applyProbe(s: SetupSession, udid: String, p: UsbProbe): Boolean {
        if (p.trusted == false) return stop(s, SetupStep.TRUST, StepState(StepStatus.WAITING, "trust-prompt"), udid)
        if (p.trusted == null && p.locked == true) return locked(s, udid)
        set(s, SetupStep.TRUST, StepState(StepStatus.DONE), udid)
        val before = store.read(s) { isDone(SetupStep.DEVELOPER_MODE) }
        if (p.developerMode == DeveloperModeStatus.ON || (p.developerMode == DeveloperModeStatus.UNKNOWN && before)) {
            set(s, SetupStep.DEVELOPER_MODE, StepState(StepStatus.DONE), udid)
            return true
        }
        val revealed = store.read(s) { revealed }
        val waiting = p.locked == true || revealed
        val detail = if (revealed) "revealed" else if (p.locked == true) null else "hidden"
        set(s, SetupStep.DEVELOPER_MODE, StepState(if (waiting) StepStatus.WAITING else StepStatus.ACTION, detail), udid)
        return false
    }

    // ---- Developer tools ----

    private fun checkTools(s: SetupSession): Boolean {
        val udid = store.read(s) { device }?.udid ?: return false
        return when (val r = helper.toolsStatus(udid)) {
            is UsbResult.Ok -> {
                val t = r.value
                // Mounted alone is not ready: the service must answer (V26).
                if (t.imageMounted && t.serviceReady) {
                    set(s, SetupStep.TOOLS, StepState(StepStatus.DONE), udid)
                } else {
                    val known = if (t.packageCached) "cached" else "missing"
                    // Mounted without an answering service: Connect again would loop, so it is an error to re-check.
                    val state = if (t.imageMounted) StepState(StepStatus.ERROR, known, UsbErrorCode.SERVICE_UNAVAILABLE.key)
                    else StepState(StepStatus.ACTION, known)
                    stop(s, SetupStep.TOOLS, state, udid)
                }
            }
            is UsbResult.Failed -> toolsFailed(s, udid, r.code)
        }
    }

    /** A failed tools call keeps the step, its detail and every check before it. */
    fun toolsFailed(s: SetupSession, udid: String, code: UsbErrorCode, known: String? = null): Boolean = when (code) {
        UsbErrorCode.NO_DEVICE, UsbErrorCode.LINK_LOST -> lost(s)
        UsbErrorCode.UNSUPPORTED_IOS -> stop(s, SetupStep.TOOLS, StepState(StepStatus.ERROR, "unsupported-ios", code.key), udid)
        else -> soft(s, SetupStep.TOOLS, StepState(StepStatus.ERROR, known, code.key), udid)
    }

    // ---- Ready ----

    private fun checkReady(s: SetupSession) {
        // A late Windows answer is dropped: no return point is a warning, never a wait.
        val found = executor.within(locateDeadlineMillis) { locate() } is LocationResult.Found
        val valid = positionValid()
        store.mutate(s) {
            windowsWarning = !found
            positionValid = valid
            steps[SetupStep.READY] = StepState(StepStatus.ACTION)
        }
    }

    /**
     * One cheap look at the cable while the user acts on the phone: anything but the same phone is re-checked, and so
     * is usbmux gone silent (Apple Devices closed) or the drivers gone, which the USB step words.
     */
    fun watch(s: SetupSession) {
        val udid = store.read(s) { device }?.udid
        when (val r = helper.devices()) {
            is UsbResult.Ok -> if (r.value.firstOrNull()?.udid != udid) run(s, Stage.USB)
            is UsbResult.Failed -> if (r.code in RECHECK_USB) run(s, Stage.USB)
        }
    }

    fun refreshPosition(s: SetupSession) {
        val valid = positionValid()
        if (store.read(s) { positionValid } != valid) store.mutate(s) { positionValid = valid }
    }

    private companion object {
        /** What [watch] sends back to the USB step: the phone gone, or the way to it (usbmux, the drivers). */
        val RECHECK_USB = setOf(UsbErrorCode.NO_DEVICE, UsbErrorCode.LINK_LOST, UsbErrorCode.USBMUX_UNAVAILABLE, UsbErrorCode.DRIVERS_MISSING)
    }
}
