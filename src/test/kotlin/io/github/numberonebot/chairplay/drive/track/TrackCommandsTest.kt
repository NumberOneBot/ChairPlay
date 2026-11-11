package io.github.numberonebot.chairplay.drive.track

import io.github.numberonebot.chairplay.drive.Driving
import io.github.numberonebot.chairplay.drive.GeoPoint
import io.github.numberonebot.chairplay.drive.GpsSettings
import io.github.numberonebot.chairplay.platform.LocationResult
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotSame
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.nio.file.Path
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

/** The GPS page's track commands: Open… reads a file into the car's track, and the deck plays, pauses and stops it. */
class TrackCommandsTest {
    @get:Rule val folder = TemporaryFolder()

    private val driving = Driving(GpsSettings(true, start = GeoPoint(50.45, 30.52)), gamepad = { null }, nanoTime = { 0L },
        locate = { LocationResult.Unknown }, lookup = { it.run() })
    private val told = mutableListOf<String>()
    private val notices = object : TrackNotices {
        override fun unreadable(reason: TrackFormatReason) { told += "unreadable $reason" }
        override fun clear() { told += "clear" }
    }
    private var chosen: Path? = null
    private val commands = TrackCommands(driving, { chosen }, notices) {}

    private fun file(name: String, text: String): Path = folder.newFile(name).toPath().also { it.toFile().writeText(text) }

    private val good = "<?xml version=\"1.0\"?><gpx version=\"1.1\" xmlns=\"http://www.topografix.com/GPX/1/1\"><trk><trkseg>" +
        "<trkpt lat=\"50.0\" lon=\"6.0\"><time>2024-08-10T14:00:00Z</time></trkpt>" +
        "<trkpt lat=\"50.001\" lon=\"6.0\"><time>2024-08-10T14:00:10Z</time></trkpt>" +
        "</trkseg></trk></gpx>"
    private val noTrack = "<?xml version=\"1.0\"?><gpx version=\"1.1\" xmlns=\"http://www.topografix.com/GPX/1/1\"></gpx>"

    private fun opened(name: String = "spa.gpx", text: String = good) {
        chosen = file(name, text)
        assertTrue(commands.act("open"))
    }

    @Test
    fun `a cancelled Open changes neither the recording nor the notices`() {
        assertTrue(commands.act("open"))
        assertNull(driving.track)

        opened()
        val loaded = driving.track
        told.clear()
        chosen = null
        assertTrue(commands.act("open"))

        assertSame(loaded, driving.track)
        assertEquals(emptyList<String>(), told)
    }

    @Test
    fun `a file that cannot be played says why and keeps the recording loaded`() {
        opened()
        val loaded = driving.track
        told.clear()

        opened("empty.gpx", noTrack)

        assertSame(loaded, driving.track)
        assertEquals(listOf("clear", "unreadable ${TrackFormatReason.NO_TRACK}"), told)
    }

    @Test
    fun `a file that cannot be read is not a readable GPX file`() {
        chosen = folder.root.toPath().resolve("gone.gpx")

        assertTrue(commands.act("open"))

        assertNull(driving.track)
        assertEquals(listOf("clear", "unreadable ${TrackFormatReason.NOT_GPX}"), told)
    }

    @Test
    fun `an Open while the dialog shows opens no second one`() {
        val showing = CountDownLatch(1)
        val close = CountDownLatch(1)
        var dialogs = 0
        val blocking = TrackCommands(driving, {
            dialogs++
            showing.countDown()
            close.await(5, TimeUnit.SECONDS)
            null
        }, notices) {}
        val first = Thread { blocking.act("open") }.apply { start() }
        assertTrue(showing.await(5, TimeUnit.SECONDS))

        assertTrue(blocking.act("open"))

        close.countDown()
        first.join(5_000)
        assertEquals(1, dialogs)
        assertTrue(blocking.act("open"))
        assertEquals("a closed dialog opens again", 2, dialogs)
    }

    @Test
    fun `a good file after a bad one drops the notice and loads stopped at its start`() {
        opened("empty.gpx", noTrack)
        told.clear()

        opened("spa.gpx")

        assertEquals(listOf("clear"), told)
        assertEquals("spa.gpx", driving.track!!.recording.name)
        assertEquals(10.0, driving.track!!.recording.duration, 0.0)
        assertEquals(TrackPhase.STOPPED to 0.0, driving.track!!.state())
    }

    @Test
    fun `each Open reads its file anew`() {
        opened()
        val first = driving.track

        opened("again.gpx")

        assertNotSame(first, driving.track)
    }

    @Test
    fun `play, pause and stop reach the player and wake the window loop`() {
        var woke = 0
        opened()
        driving.wake = { woke++ }

        assertTrue(commands.act("play"))
        assertEquals(TrackPhase.PLAYING, driving.track!!.phase)
        assertTrue(commands.act("pause"))
        assertEquals(TrackPhase.PAUSED, driving.track!!.phase)
        assertTrue(commands.act("stop"))
        assertEquals(TrackPhase.STOPPED, driving.track!!.phase)
        assertEquals(3, woke)
    }

    @Test
    fun `the deck without a recording and an unknown action are refused`() {
        assertFalse(commands.act("play"))
        assertFalse(commands.act("pause"))
        assertFalse(commands.act("stop"))
        opened()
        assertFalse(commands.act("rewind"))
        assertEquals(TrackPhase.STOPPED, driving.track!!.phase)
    }
}
