package io.github.numberonebot.chairplay.drive.track

/** The pedals at a moment of a recording, 0 at rest to 1 pressed fully. */
data class TrackPedals(val throttle: Double, val brake: Double)

// The speed's change is taken over a second around the moment, which smooths a recording's jitter.
private const val HALF_WINDOW_SECONDS = 0.5
// m/s²: a gain this quick shows the throttle fully pressed, and a slowing this quick the brake.
private const val FULL_THROTTLE = 3.0
private const val FULL_BRAKE = 8.0
// m/s²: a slowing up to this is the car coasting off the throttle, drag and rolling, with no brake.
private const val COASTING = 1.5

/**
 * The pedals [seconds] into the recording: its own where it has them, a missing one at rest; else from how fast its
 * speed changes there, a gain as the throttle and a slowing past coasting as the brake.
 */
fun TrackRecording.pedals(seconds: Double): TrackPedals {
    val sample = at(seconds)
    if (sample.throttle != null || sample.brake != null) return TrackPedals(sample.throttle ?: 0.0, sample.brake ?: 0.0)
    val from = (seconds - HALF_WINDOW_SECONDS).coerceAtLeast(0.0)
    val to = (seconds + HALF_WINDOW_SECONDS).coerceAtMost(duration)
    if (to <= from) return TrackPedals(0.0, 0.0)
    // shortcut: a gap between segments reads as a stop around it, so its edges show the brake; fine for the samples.
    val gain = (at(to).speed - at(from).speed) / (to - from)
    return TrackPedals(
        throttle = (gain / FULL_THROTTLE).coerceIn(0.0, 1.0),
        brake = ((-gain - COASTING) / (FULL_BRAKE - COASTING)).coerceIn(0.0, 1.0),
    )
}
