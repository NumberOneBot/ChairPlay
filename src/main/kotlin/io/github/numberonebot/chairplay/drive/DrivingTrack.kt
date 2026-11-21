package io.github.numberonebot.chairplay.drive

import io.github.numberonebot.chairplay.drive.track.TrackPhase
import io.github.numberonebot.chairplay.drive.track.TrackPlayer
import io.github.numberonebot.chairplay.drive.track.TrackRecording
import java.util.concurrent.atomic.AtomicReference

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
    private data class Loaded(val player: TrackPlayer, val showGauges: Boolean = true)
    private val loaded = AtomicReference<Loaded?>()

    val player: TrackPlayer? get() = loaded.get()?.player
    val gaugeLimits: GaugeLimits? get() = loaded.get()?.takeIf { it.showGauges }?.player?.recording?.gaugeLimits

    // Whether the track has the car; the window loop's thread only.
    private var tracking = false

    /**
     * Whether a track may drive the car, on top of the GPS being on: USB in force and sending, where the GPS page shows
     * track playback. Leaving USB counts as losing it: a playing track pauses (spec §1).
     */
    @Volatile var live: () -> Boolean = { true }

    fun load(recording: TrackRecording) {
        loaded.set(Loaded(TrackPlayer(recording, nanoTime)))
    }

    /** The next source actually drove the car. A simultaneous Open can still select its new track. */
    fun releaseGauges() {
        val current = loaded.get() ?: return
        if (current.showGauges) loaded.compareAndSet(current, current.copy(showGauges = false))
    }

    /** A GPS operation holds the car: a playing track pauses, so its clock does not run through the hold. */
    fun hold() {
        val current = player ?: return
        current.pause()
        if (tracking && current.phase == TrackPhase.STOPPED) release(current)
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
        if (tracking) release(player)
        return null
    }

    /** One pass of [player]: the car where its recording is now, at frame pace while it plays and waiting while paused. */
    fun pass(player: TrackPlayer): Double? {
        if (player.phase == TrackPhase.PLAYING) {
            val current = loaded.get()
            if (current?.player === player && !current.showGauges) {
                loaded.compareAndSet(current, current.copy(showGauges = true))
            }
        }
        player.advance()
        val (phase, time) = player.state()
        if (phase == TrackPhase.STOPPED) {
            release(player, time)
            return DriveControl.FRAME_SECONDS
        }
        val sample = player.recording.at(time)
        val playing = phase == TrackPhase.PLAYING
        val speed = if (playing) sample.speed else 0.0
        drive.follow(sample.position, sample.heading, speed, sample.rpm, sample.gear, preserveRpm = true)
        placed()
        tracking = true
        return if (playing) DriveControl.FRAME_SECONDS else null
    }

    private fun release(player: TrackPlayer?, time: Double? = player?.time) {
        tracking = false
        val finished = player?.recording?.takeIf { time == it.duration }
        if (finished != null) {
            val last = finished.at(finished.duration)
            drive.input = CarInput()
            drive.place(last.position, last.heading)
            placed()
        } else {
            drive.stop()
        }
        remember(drive.snapshot())
    }
}
