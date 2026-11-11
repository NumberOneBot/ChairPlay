package io.github.numberonebot.chairplay.drive.track

import io.github.numberonebot.chairplay.drive.Driving
import java.io.IOException
import java.nio.file.Files
import java.nio.file.Path
import java.util.concurrent.atomic.AtomicBoolean

/** The GPS page's word that the file Open… chose cannot be played, and its end; the app shows it in Track playback. */
interface TrackNotices {
    fun unreadable(reason: String)
    fun clear()
}

/**
 * Track playback's commands from the GPS page, on its HTTP thread: Open… asks [openFile] for a GPX file and loads it
 * into [driving], and the deck plays, pauses and stops what is loaded. A file that cannot be played leaves the
 * recording loaded as it was and says why through [notices]; the next file chosen takes the notice away.
 */
class TrackCommands(
    private val driving: Driving,
    private val openFile: () -> Path?,
    private val notices: TrackNotices,
    private val log: (String) -> Unit,
) {
    // Whether an Open dialog shows: another Open… meanwhile opens no second one.
    private val opening = AtomicBoolean()

    /** Does [action] ("open", "play", "pause" or "stop"); false for any other, and for the deck with nothing loaded. */
    fun act(action: String): Boolean {
        if (action == "open") {
            if (!opening.compareAndSet(false, true)) return true
            val path = try {
                openFile()
            } finally {
                opening.set(false)
            }
            path?.let(::open)
            return true
        }
        val player = driving.track ?: return false
        when (action) {
            "play" -> player.play()
            "pause" -> player.pause()
            "stop" -> player.stop()
            else -> return false
        }
        // A paused or stopped car's window loop may be waiting for an event.
        driving.wake()
        return true
    }

    private fun open(path: Path) {
        notices.clear()
        val name = path.fileName.toString()
        val recording = try {
            Files.newInputStream(path).use { GpxReader.read(name, it) }
        } catch (e: TrackFormatException) {
            return unreadable(name, e.reason)
        } catch (e: IOException) {
            log("Track $name not read: $e")
            return unreadable(name, GpxReader.UNREADABLE)
        }
        driving.load(recording)
        log("Track $name loaded: ${recording.duration.toInt()} s, ${recording.distanceMeters.toInt()} m")
    }

    private fun unreadable(name: String, reason: String) {
        log("Track $name not played: $reason")
        notices.unreadable(reason)
    }
}
