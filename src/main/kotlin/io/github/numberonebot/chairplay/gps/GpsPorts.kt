package io.github.numberonebot.chairplay.gps

import io.github.numberonebot.chairplay.drive.GeoPoint
import io.github.numberonebot.chairplay.drive.GpsSettings

// What the GPS output's coordinator drives and reads: the two channels to the iPhone, the car, the settings, Windows'
// return point when USB stops, and the car's hold. The coordinator owns these calls; the app implements the ports, the
// tests fake them.

/** One step of letting a USB simulation go: done, failed (or no reply), or not tried (no channel to try it on). */
enum class StepOutcome { DONE, FAILED, SKIPPED }

data class Release(val returned: StepOutcome, val cleared: StepOutcome, val closed: StepOutcome) {
    companion object {
        val NOTHING = Release(StepOutcome.SKIPPED, StepOutcome.SKIPPED, StepOutcome.SKIPPED)
    }
}

/**
 * Whether a channel could start now ([reason] null when it could). [sessionGps]: for CarPlay, whether the connected
 * session identified with the GPS (null without a session). [device]: for USB, the iPhone attached.
 */
data class ChannelState(
    val reason: GpsReason?,
    val sessionGps: Boolean? = null,
    val device: UsbDeviceView? = null,
)

/** A channel to the iPhone; at most one writer runs in all, and only the coordinator starts and stops it. */
interface GpsChannel {
    val method: GpsMethod

    /** Cheap, no probe: CarPlay's session, USB's attached device as last seen. */
    fun state(): ChannelState

    /** Starts the writer from [seed]; [device] is USB's prepared iPhone. Null when it started, else why not. */
    fun start(seed: GeoPoint, device: UsbDevice?): GpsReason?

    /** Stops the writer and waits, bounded, for a send in flight: once it returns nothing more goes (the barrier). */
    fun quiesce()

    /**
     * USB: sets [returnTo] once (when given), clears and closes its session, each bounded and tried whatever the one
     * before did; with no session, [Release.NOTHING] (D12). CarPlay: [Release.NOTHING].
     */
    fun release(returnTo: GeoPoint?, onStage: (OperationStage) -> Unit): Release

    val running: Boolean

    /** Commands the channel took this run. */
    val sends: Long

    /** Set by the coordinator: the channel's state changed on its own (a session came, a cable went). */
    var onChanged: () -> Unit
}

/** The one car both channels send. */
interface GpsCar {
    /** Where the car is; null while it is nowhere (placed false: 0,0 then is a placeholder, not a place). */
    fun position(): GeoPoint?
}

/** The gps.* settings as saved; the coordinator's own keys are saved through [save] alone. */
interface GpsSettingsStore {
    fun current(): GpsSettings
    fun save(changes: Map<String, String>)
}

sealed interface ReturnPoint {
    data class Found(val point: GeoPoint) : ReturnPoint
    /** [reason]: denied, unknown or timeout. */
    data class Unavailable(val reason: String) : ReturnPoint
}

/** Where Windows locates the PC, asked anew each time, within [deadlineMillis]; a later answer is dropped. */
fun interface ReturnLocator {
    fun locate(deadlineMillis: Long): ReturnPoint
}

/** The car's input while an operation runs: it stands (D9); the window stays live. */
interface InputHold {
    fun hold()
    fun release()

    companion object {
        val NONE = object : InputHold {
            override fun hold() {}
            override fun release() {}
        }
    }
}
