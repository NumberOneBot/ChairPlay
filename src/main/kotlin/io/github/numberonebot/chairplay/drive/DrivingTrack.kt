package io.github.numberonebot.chairplay.drive

import io.github.numberonebot.chairplay.drive.track.TrackPhase
import io.github.numberonebot.chairplay.drive.track.TrackPlayer
import io.github.numberonebot.chairplay.drive.track.TrackRecording

/**
 * The recording the GPS page loaded, as the car's source: while it plays or pauses it puts [drive] where the recording
 * is ([placed] tells the car it is somewhere), and the keys and the gamepad drive nothing. Once it stops, ends or gives
 * way to another file, the car stands where it is and [remember] saves its place, as a game's car is handed back. A car
 * that cannot send, or USB not in force ([live]), pauses it and hands the car back, as a GPS operation's hold pauses it;
 * coming back does not play it.
 */
internal class DrivingTrack(
    private val drive: Drive,
    private val nanoTime: () -> Long,
    private val placed: () -> Unit,
    private val remember: (DriveSnapshot) -> Unit,
) {
    /** The recording loaded last, stopped at its start until played; set from any thread, read on the window loop. */
    @Volatile var player: TrackPlayer? = null
        private set

    // Whether the track has the car; the window loop's thread only.
    private var tracking = false

    /**
     * Whether a track may drive the car, on top of the GPS being on: USB in force and sending, where the GPS page shows
     * track playback. Leaving USB counts as losing it: a playing track pauses (spec §1).
     */
    @Volatile var live: () -> Boolean = { true }

    fun load(recording: TrackRecording) {
        player = TrackPlayer(recording, nanoTime)
    }

    /** A GPS operation holds the car: a playing track pauses, so its clock does not run through the hold. */
    fun hold() {
        player?.pause()
    }

    /**
     * The window loop's look at the track, with the GPS [on] or not: the player that drives the car this pass, or
     * null when none does, a car the track had then standing where it is, its place saved.
     */
    fun driver(on: Boolean): TrackPlayer? {
        val player = player
        val drives = on && live()
        if (player != null && player.owns && !drives) player.pause()
        if (drives && player != null && player.owns) return player
        if (tracking) release()
        return null
    }

    /** One pass of [player]: the car where its recording is now, at frame pace while it plays and waiting while paused. */
    fun pass(player: TrackPlayer): Double? {
        player.advance()
        val (phase, time) = player.state()
        // Stopped at its start, the page stopped it since the last look: the car stands where it is. Stopped further on,
        // the recording ended: the car is at its last point.
        if (phase != TrackPhase.STOPPED || time > 0.0) {
            val sample = player.recording.at(time)
            drive.follow(sample.position, sample.heading, if (phase == TrackPhase.PLAYING) sample.speed else 0.0, null, null)
            placed()
        }
        tracking = true
        return when (phase) {
            TrackPhase.PLAYING -> DriveControl.FRAME_SECONDS
            TrackPhase.PAUSED -> null
            // One more pass hands the car to the keys and the gamepad.
            TrackPhase.STOPPED -> DriveControl.FRAME_SECONDS.also { release() }
        }
    }

    private fun release() {
        tracking = false
        drive.stop()
        remember(drive.snapshot())
    }
}
