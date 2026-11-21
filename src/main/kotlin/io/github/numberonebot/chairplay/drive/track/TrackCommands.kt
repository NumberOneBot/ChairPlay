package io.github.numberonebot.chairplay.drive.track

import io.github.numberonebot.chairplay.drive.Driving
import java.io.IOException
import java.nio.file.Files
import java.nio.file.Path
import java.util.concurrent.atomic.AtomicBoolean

/** The GPS page's word that the file Open… chose cannot be played, and its end; the app shows it in Track playback. */
interface TrackNotices {
    fun unreadable(reason: TrackFormatReason)
    fun clear()
}

/**
 * Track playback's commands from the GPS page, on its HTTP thread: Open… asks [openFile] for a recording and loads it
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

    /**
     * Does [action] ("open", "play", "pause", "stop", a seek of [SEEKS], or a speed of [SPEEDS]); false for any other,
     * and for the deck with nothing loaded.
     */
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
            in SEEKS -> player.seek(SEEKS.getValue(action))
            in SPEEDS -> player.speed = SPEEDS.getValue(action)
            else -> return false
        }
        // A paused or stopped car's window loop may be waiting for an event.
        driving.wake()
        return true
    }

    private fun open(path: Path) {
        notices.clear()
        val name = path.fileName.toString()
        val vbo = name.endsWith(".vbo", ignoreCase = true)
        val recording = try {
            Files.newInputStream(path).use { input ->
                if (vbo) VboReader.read(name, input) else GpxReader.read(name, input)
            }
        } catch (e: TrackFormatException) {
            return unreadable(name, e.reason)
        } catch (e: IOException) {
            log("Track $name not read: $e")
            return unreadable(name, if (vbo) TrackFormatReason.NOT_VBO else TrackFormatReason.NOT_GPX)
        }
        driving.load(recording)
        log("Track $name loaded: ${recording.duration.toInt()} s, ${recording.distanceMeters.toInt()} m")
    }

    private fun unreadable(name: String, reason: TrackFormatReason) {
        log("Track $name not played: ${reason.key}")
        notices.unreadable(reason)
    }

    private companion object {
        /** The deck's seek keys and the seconds each moves the recording (webui/src/gps/trackApi.ts). */
        val SEEKS = mapOf("back-60" to -60.0, "back-10" to -10.0, "forward-10" to 10.0, "forward-60" to 60.0)

        /** The speed key's choices, as "speed-<pace>" (webui/src/gps/trackApi.ts). */
        val SPEEDS = listOf(0.25, 0.5, 1.0, 2.0, 5.0, 10.0).associateBy { "speed-" + it.toString().removeSuffix(".0") }
    }
}
