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

    private var nowNanos = 0L
    private val driving = Driving(GpsSettings(true, start = GeoPoint(50.45, 30.52)), gamepad = { null }, nanoTime = { nowNanos },
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
    fun `the seek keys move the loaded recording by their seconds`() {
        assertFalse(commands.act("forward-10"))
        opened()
        assertTrue(commands.act("forward-10"))
        assertEquals(10.0, driving.track!!.time, 0.0)
        assertTrue(commands.act("back-10"))
        assertEquals(0.0, driving.track!!.time, 0.0)
        assertFalse(commands.act("forward-5"))
    }

    @Test
    fun `the speed keys set the loaded recording's pace`() {
        assertFalse(commands.act("speed-2"))
        opened()
        assertTrue(commands.act("speed-0.25"))
        assertEquals(0.25, driving.track!!.speed, 0.0)
        assertTrue(commands.act("speed-10"))
        assertEquals(10.0, driving.track!!.speed, 0.0)
        assertFalse(commands.act("speed-3"))
        assertEquals(10.0, driving.track!!.speed, 0.0)
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
    fun `Open plays VBO by named columns with recorded speed and a midnight rollover`() {
        opened("spa.VBO", """
            [header]
            longitude
            velocity mph
            time
            latitude
            heading
            height
            [column names]
            lng velocity time lat heading height
            [data]
            -360 30 235959.50 3000 90 420
            -100 90 235959.50 1000 1 5
            -360.06 50 000000.50 3000.06 100 422
        """.trimIndent())

        assertEquals(listOf("clear"), told)
        val track = driving.track!!.recording
        assertEquals(1.0, track.duration, 1e-9)
        assertEquals(2, track.segments.single().size)
        assertEquals(GeoPoint(50.0, 6.0), track.at(0.0).position)
        assertEquals(GeoPoint(50.001, 6.001), track.at(1.0).position)
        assertEquals(40 * 0.44704, track.at(0.5).speed, 1e-9)
        assertEquals(95.0, track.at(0.5).heading, 1e-9)
        assertEquals(420.0, track.segments.single().first().altitude!!, 1e-9)
        assertEquals(50 * 0.44704, track.gaugeLimits.peakSpeed!!, 1e-9)
        assertTrue(commands.act("play"))
        assertEquals(TrackPhase.PLAYING, driving.track!!.phase)
    }

    @Test
    fun `VBO throttle and brake in percent are read as shares of a full press`() {
        opened("pedals.vbo", """
            [column names]
            time lat long velocity throttle brake
            [data]
            000000.00 3000 -360 100 100 0
            000010.00 3000.06 -360 120 -5 104
        """.trimIndent())
        val (first, second) = driving.track!!.recording.segments.single()
        assertEquals(1.0 to 0.0, first.throttle to first.brake)
        assertEquals(null to null, second.throttle to second.brake)
    }

    @Test
    fun `VBO RPM and gear reach AltScreen and recorded RPM sets its tachometer scale`() {
        opened("telemetry.vbo", """
            [column names]
            time lat long velocity RPM Gear
            [data]
            000000.00 3000 -360 0 0.0e0 0.0e0
            000010.00 3000.06 -360 220 1.1585e4 8.0e0
            000020.00 3000.12 -360 230 Infinity 7.5
        """.trimIndent())

        val recording = driving.track!!.recording
        val last = recording.segments.single().last()
        assertNull(last.rpm)
        assertNull(last.gear)
        assertEquals(11_585.0, recording.gaugeLimits.peakRpm)
        driving.driveMode(true)
        commands.act("play")
        driving.pass()
        assertEquals(0.0, driving.gauges()!!.rpm, 0.0)
        assertEquals("N", driving.gauges()!!.gear)

        nowNanos += 10_000_000_000L
        driving.pass()
        val gauges = driving.gauges()!!
        assertEquals(11_585.0, gauges.rpm, 0.0)
        assertEquals("D8", gauges.gear)
        assertEquals(11.5, gauges.tachometer.redFrom!!, 0.0)
        assertEquals(12.0, gauges.tachometer.max, 0.0)
    }

    @Test
    fun `unreadable VBO identifies its format and keeps the previous track`() {
        opened()
        val loaded = driving.track
        told.clear()

        opened("broken.vbo", "[data]\n30 235959.50 3000")

        assertSame(loaded, driving.track)
        assertEquals(listOf("clear", "unreadable ${TrackFormatReason.NOT_VBO}"), told)
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
