package io.github.numberonebot.chairplay.carplay

import io.github.numberonebot.chairplay.bridge.TelemetryInput
import io.github.numberonebot.chairplay.bridge.TelemetryRecord
import io.github.numberonebot.chairplay.bridge.TelemetrySource
import io.github.numberonebot.chairplay.drive.CarSpec
import io.github.numberonebot.chairplay.drive.Driving
import io.github.numberonebot.chairplay.drive.GeoPoint
import io.github.numberonebot.chairplay.drive.GpsAlmanac
import io.github.numberonebot.chairplay.drive.GpsSettings
import io.github.numberonebot.chairplay.drive.LocationStatus
import io.github.numberonebot.chairplay.drive.NmeaDialect
import io.github.numberonebot.chairplay.drive.NmeaFraming
import io.github.numberonebot.chairplay.drive.SatelliteGeometry
import io.github.numberonebot.chairplay.drive.WindowsLocation
import io.github.numberonebot.chairplay.drive.almanacFixture
import io.github.numberonebot.chairplay.drive.almanacTime
import org.junit.Assert.assertEquals
import org.junit.Test
import java.nio.ByteBuffer

/**
 * Characterization: one scripted drive of the GPS car, nowhere and then placed, by the gamepad through park, drive and
 * reverse, then by a game's telemetry forward, backing up and paused, and what its location sends the iPhone: each
 * update's NMEA parameters on the tunnel as framed, the once-a-second answer unattached, its status, and the log. The
 * expected transcript (iap2/location-transcript.txt) was recorded from DriveLocation, built as Driving.location built
 * it, before it moved here as CarPlayLocation: the move changed nothing the iPhone is sent.
 */
class CarPlayLocationTranscriptTest {
    private val kyiv = GeoPoint(50.4501, 30.5234)
    private val paris = GeoPoint(48.85, 2.29)
    private val frame = 16_666_667L
    private var now = 0L
    private val wall: Long get() = almanacTime + now / 1_000_000
    private val lines = ArrayList<String>()
    private val log: (String) -> Unit = { lines += "  log $it" }
    // GLFW's axes: the left stick's X first, the left and right triggers last, -1 let go.
    private var axes = floatArrayOf(0f, 0f, 0f, 0f, -1f, -1f)
    private val source = FakeSource()
    private val almanac = GpsAlmanac.parse(almanacFixture())
    private val sky: (GeoPoint, Long) -> String? = { point, millis -> SatelliteGeometry.gsv(SatelliteGeometry.visible(almanac, point, millis)) }
    private var settings = GpsSettings(true, satellites = true, headingDegrees = 30.0, car = CarSpec(reverse = true))
    private val driving = Driving(settings, log, gamepad = { axes }, nanoTime = { now }, satelliteSentences = sky,
        locate = { WindowsLocation.Found(kyiv) }, lookup = { it.run() }, telemetry = { TelemetryInput(source, log) { now } })
    private var told = 0
    private val schedule = FakeSchedule()
    private var failing = false

    /** The game: each read a new record counter, its car where [game] says, a little further north each time. */
    private class FakeSource : TelemetrySource {
        var game: TelemetryRecord? = null
        private var reads = 0

        override fun open(): String? = null

        override fun read(into: ByteBuffer): Boolean {
            val record = game ?: return false
            reads++
            TelemetryRecord.write(record.copy(counter = reads, latitude = record.latitude + reads * 1e-5), into)
            return true
        }

        override fun close() {}
    }

    /** A schedule that runs nothing by itself: the script calls [tick]. */
    private class FakeSchedule {
        var tick: (() -> Unit)? = null
        var period: (() -> Long)? = null
        var closed = false

        fun start(period: () -> Long, action: () -> Unit): AutoCloseable {
            this.period = period
            tick = action
            closed = false
            return AutoCloseable { closed = true }
        }
    }

    // The location as the app makes it from the car, with the wall clock and the schedule the script's.
    private fun location() = CarPlayLocation(driving, true, log, wallMillis = { wall }, schedule = schedule::start)

    private fun record(speed: Double, gear: Int?, paused: Boolean = false) = TelemetryRecord(
        counter = 0, paused = paused, gear = gear, speed = speed, headingDegrees = 135.0,
        latitude = paris.latitude, longitude = paris.longitude, rpm = 3000.0, source = "AC",
    )

    private fun save(now: GpsSettings, vararg keys: String) {
        settings = now
        driving.saved(now, keys.toSet())
    }

    /** A quarter second of the window loop at 60 passes a second, then one update of the GPS sender, [times] over. */
    private fun ticks(name: String, times: Int = 1) {
        lines += "> $name"
        repeat(times) {
            repeat(15) { now += frame; driving.pass() }
            schedule.tick!!()
            lines += "  car ${driving.drive.snapshot().car}"
        }
    }

    private fun script() {
        driving.onSent = { told++ }
        val location = location()
        location.attach { parameters ->
            if (failing) throw java.io.IOException("link ended")
            lines += "  sent " + parameters.joinToString(" | ") { it.replace("\r\n", "\\r\\n") }
        }
        lines += "> start"
        location.onRequested(setOf(1, 2, 3, 4))
        location.start()
        lines += "  period ${schedule.period!!()}"
        ticks("nowhere", 2)
        lines += "> placed where Windows locates the PC"
        driving.saveTo {}
        ticks("parked", 2)
        axes = floatArrayOf(0f, 0f, 0f, 0f, -1f, 1f)
        ticks("full throttle", 12)
        axes = floatArrayOf(-1f, 0f, 0f, 0f, -1f, 0f)
        ticks("half throttle, steering left", 4)
        axes = floatArrayOf(0f, 0f, 0f, 0f, 1f, -1f)
        ticks("brake held, to a stop and backing up", 24)
        axes = floatArrayOf(0f, 0f, 0f, 0f, -1f, -1f)
        ticks("let go", 8)
        now += 50_000_000_000L
        ticks("a minute on")
        save(settings.copy(framing = NmeaFraming.JOINED), "gps.framing")
        ticks("framed joined")
        save(settings.copy(framing = NmeaFraming.MESSAGES), "gps.framing")
        ticks("framed a message each")
        save(settings.copy(framing = NmeaFraming.PARAMETERS, nmea = NmeaDialect.MODERN, rate = 10), "gps.nmea", "gps.rate")
        lines += "  period ${schedule.period!!()}"
        ticks("modern sentences")
        save(settings.copy(nmea = NmeaDialect.CLASSIC, rate = 4, sendHeading = false), "gps.nmea", "gps.rate", "gps.heading.send")
        ticks("no heading sent")
        save(settings.copy(sendHeading = true, external = true), "gps.heading.send", "gps.external")
        source.game = record(speed = 20.0, gear = 3)
        ticks("a game drives", 4)
        source.game = record(speed = -3.0, gear = -1)
        ticks("the game backs up", 2)
        source.game = record(speed = 0.0, gear = 1, paused = true)
        ticks("the game paused", 3)
        save(settings.copy(external = false), "gps.external")
        ticks("the game let go", 2)

        lines += "> once a second, unattached"
        val once = location()
        once.onRequested(setOf(1, 2, 3, 4))
        once.start()
        repeat(2) {
            now += 1_000_000_000L
            lines += "  answer " + once.latestNmea()?.replace("\r\n", "\\r\\n")
        }
        status("unattached", once.status())
        status("attached", location.status())
        failing = true
        ticks("the tunnel fails")
        lines += "  closed ${schedule.closed}"
        status("attached", location.status())
        location.stop()
        once.stop()
        status("unattached, stopped", once.status())
        lines += "  told $told"
    }

    private fun status(name: String, status: LocationStatus) {
        lines += "  status $name listening=${status.listening} requested=${status.requested} sent=${status.sent}"
    }

    @Test
    fun `a scripted drive gives the recorded transcript`() {
        script()
        val expected = javaClass.getResourceAsStream("/iap2/location-transcript.txt")!!.use { String(it.readBytes(), Charsets.UTF_8) }
        assertEquals(expected.replace("\r\n", "\n").trimEnd(), lines.joinToString("\n"))
    }
}
