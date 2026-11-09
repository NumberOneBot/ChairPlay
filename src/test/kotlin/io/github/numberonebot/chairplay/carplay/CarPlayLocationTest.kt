package io.github.numberonebot.chairplay.carplay

import io.github.numberonebot.chairplay.drive.CarInput
import io.github.numberonebot.chairplay.drive.CarSpec
import io.github.numberonebot.chairplay.drive.Drive
import io.github.numberonebot.chairplay.drive.GeoPoint
import io.github.numberonebot.chairplay.drive.GpsSettings
import io.github.numberonebot.chairplay.drive.LocationStatus
import io.github.numberonebot.chairplay.drive.NmeaDialect
import io.github.numberonebot.chairplay.drive.NmeaFraming
import io.github.numberonebot.chairplay.drive.SatelliteGeometry
import io.github.numberonebot.chairplay.drive.VisibleSatellite
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class CarPlayLocationTest {
    private val kyiv = GeoPoint(50.4501, 30.5234)
    private var now = 1_000_000_000L
    private val frame = 16_666_667L
    private val wallClock = 1_761_300_000_000L // 2025-10-24 10:00:00 UTC

    private fun drive(heading: Double = 0.0) = Drive(CarSpec(), kyiv, heading) { now }

    private fun location(drive: Drive, satellites: Boolean = false) =
        CarPlayLocation(drive, satellites, nanoTime = { now }, wallMillis = { wallClock }, satelliteSentences = { _, _ -> sky() })

    private fun sky() = SatelliteGeometry.gsv((1..10).map { VisibleSatellite(it, 45, it * 30) })

    private fun Drive.tick(seconds: Double) {
        repeat((seconds * 60).toInt()) { now += frame; advance() }
    }

    private fun sentence(nmea: String, type: String) = nmea.split("\r\n").firstOrNull { it.startsWith("$$type,") }

    // The parked car: where it stands, no speed, and no course (0 would say north).
    @Test
    fun `a parked car is sent where it stands, with no course`() {
        val location = location(drive())
        location.onRequested(setOf(1, 2))
        assertTrue(location.start())
        val nmea = location.latestNmea()!!

        assertEquals("\$GPGGA,100000.00,5027.0060,N,03031.4040,E,1,08,0.5,0.0,M,0.0,M,,*", sentence(nmea, "GPGGA")!!.dropLast(2))
        val rmc = sentence(nmea, "GPRMC")!!.split(',')
        assertEquals("0.00", rmc[7])
        assertEquals("", rmc[8])
        assertNull(sentence(nmea, "PASCD"))
        assertNull(sentence(nmea, "GPGSV"))
    }

    // On the move: the speed in knots and the course over the ground, opposite the heading when backing up.
    @Test
    fun `a moving car is sent with its speed and course`() {
        val drive = drive(heading = 90.0)
        drive.input = CarInput(throttle = 1.0)
        drive.tick(5.0)
        val location = location(drive)
        location.start()
        val rmc = sentence(location.latestNmea()!!, "GPRMC")!!.split(',')
        assertEquals(drive.snapshot().car.speed * 1.94384449, rmc[7].toDouble(), 0.01)
        assertEquals(90.0, rmc[8].toDouble(), 0.5)

        val back = Drive(CarSpec(reverse = true), kyiv, 90.0) { now }
        back.input = CarInput(brake = 1.0)
        back.tick(3.0)
        val reversing = location(back)
        reversing.start()
        assertEquals(270.0, sentence(reversing.latestNmea()!!, "GPRMC")!!.split(',')[8].toDouble(), 0.5)
    }

    // Heading sending off: the course field stays empty even on the move, for the iPhone to reckon it from the places.
    @Test
    fun `with heading sending off, a moving car is sent with no course`() {
        val drive = drive(heading = 90.0)
        drive.input = CarInput(throttle = 1.0)
        drive.tick(5.0)
        val location = CarPlayLocation(drive, satellites = false, nanoTime = { now }, wallMillis = { wallClock },
            settings = { GpsSettings(true, sendHeading = false) })
        location.onRequested(setOf(1, 2))
        location.start()
        val rmc = sentence(location.latestNmea()!!, "GPRMC")!!.split(',')
        assertTrue(rmc[7].toDouble() > 0.0)
        assertEquals("", rmc[8])
    }

    // $PASCD when asked (selector 4): the samples since the last sentence, with the gear.
    @Test
    fun `wheel speed goes with the position when asked`() {
        val drive = drive()
        drive.input = CarInput(throttle = 1.0)
        val location = location(drive)
        location.onRequested(setOf(1, 2, 4))
        location.start()
        drive.tick(1.0)
        val pascd = sentence(location.latestNmea()!!, "PASCD")!!.split(',')
        assertEquals("D", pascd[3])
        assertTrue(pascd[5].toInt() in 9..11)

        // Parked with nothing ticking: still one sample, at zero, in park.
        val parked = location(drive())
        parked.onRequested(setOf(1, 2, 4))
        parked.start()
        val still = sentence(parked.latestNmea()!!, "PASCD")!!.split(',')
        assertEquals("P", still[3])
        assertEquals("1", still[5])
        assertEquals("0.000", still[7].substringBefore('*'))
    }

    @Test
    fun `backing up says reverse in the wheel speed`() {
        val drive = Drive(CarSpec(reverse = true), kyiv, 0.0) { now }
        drive.input = CarInput(brake = 1.0)
        val location = location(drive)
        location.onRequested(setOf(1, 2, 4))
        location.start()
        drive.tick(3.0)
        assertEquals("R", sentence(location.latestNmea()!!, "PASCD")!!.split(',')[3])
    }

    // $GPGSV when asked (selector 3) and turned on: a sky of strong satellites, in sentences of four.
    @Test
    fun `satellites in view are sent when asked and turned on`() {
        val location = location(drive(), satellites = true)
        location.onRequested(setOf(1, 2, 3))
        location.start()
        val nmea = location.latestNmea()!!
        val gsv = nmea.split("\r\n").filter { it.startsWith("\$GPGSV,") }
        assertEquals(3, gsv.size)
        assertEquals(listOf("3", "1", "10"), gsv[0].split(',').subList(1, 4))
        for (line in gsv) {
            val body = line.substring(1, line.indexOf('*'))
            val checksum = body.fold(0) { sum, c -> sum xor c.code }
            assertEquals(String.format("%02X", checksum), line.substringAfter('*'))
        }
        assertTrue(nmea.endsWith("\r\n"))

        val off = location(drive(), satellites = false)
        off.onRequested(setOf(1, 2, 3))
        off.start()
        assertNull(sentence(off.latestNmea()!!, "GPGSV"))
    }

    /** A schedule that runs nothing by itself: the test calls [tick], and reads the [period] asked for. */
    private class FakeSchedule {
        var tick: (() -> Unit)? = null
        var period: (() -> Long)? = null
        var closed = false

        fun start(period: () -> Long, action: () -> Unit): AutoCloseable {
            this.period = period
            tick = action
            return AutoCloseable { closed = true }
        }
    }

    // Attached to the tunnel, the GPS sends on its own at the rate set (the iPhone asks for 250 ms), each fix with its
    // own time; DiPlay's once-a-second update then sends nothing.
    @Test
    fun `attached to the tunnel, the GPS sends at the rate set`() {
        var wall = wallClock
        var settings = GpsSettings(true, satellites = true, rate = 4)
        val schedule = FakeSchedule()
        val drive = drive(heading = 90.0)
        val location = CarPlayLocation(drive, satellites = true, nanoTime = { now }, wallMillis = { wall },
            settings = { settings }, schedule = schedule::start, satelliteSentences = { _, _ -> sky() })
        val sent = mutableListOf<String>()
        location.attach { parameters -> sent += parameters.joinToString("") }
        location.onRequested(setOf(1, 2, 3, 4))
        location.start()

        assertEquals(250L, schedule.period!!())
        assertNull(location.latestNmea())
        repeat(4) {
            drive.input = CarInput(throttle = 1.0)
            drive.tick(0.25)
            wall += 250
            schedule.tick!!()
        }
        assertEquals(4, sent.size)
        assertEquals(listOf("100000.25", "100000.50", "100000.75", "100001.00"), sent.map { sentence(it, "GPGGA")!!.split(',')[1] })
        assertTrue(sent.all { sentence(it, "PASCD") != null })
        // The sky goes once a second, not with every fix.
        assertEquals(1, sent.count { sentence(it, "GPGSV") != null })
        assertEquals(4L, location.status().sent)

        settings = GpsSettings(true, rate = 10, nmea = NmeaDialect.MODERN)
        assertEquals(100L, schedule.period!!())
        schedule.tick!!()
        assertTrue(sentence(sent.last(), "GNGGA") != null)

        location.stop()
        assertTrue(schedule.closed)
    }

    // gps.framing: all of an update's sentences as one string in one LocationInformation, as DiPlay sends them; each in
    // a message of its own; or each a parameter of one message.
    @Test
    fun `an update's sentences go joined, a message each, or a parameter each`() {
        var settings = GpsSettings(true, rate = 4, framing = NmeaFraming.JOINED)
        val schedule = FakeSchedule()
        val location = CarPlayLocation(drive(), satellites = false, nanoTime = { now }, wallMillis = { wallClock },
            settings = { settings }, schedule = schedule::start)
        val messages = mutableListOf<List<String>>()
        location.attach { messages += it }
        location.onRequested(setOf(1, 2, 4))
        location.start()

        schedule.tick!!()
        assertEquals(1, messages.size)
        assertEquals(listOf("\$GPGGA", "\$GPRMC", "\$PASCD"), messages[0].single().split("\r\n").filter(String::isNotEmpty).map { it.substringBefore(',') })

        settings = GpsSettings(true, rate = 4, framing = NmeaFraming.MESSAGES)
        messages.clear()
        schedule.tick!!()
        assertEquals(listOf(listOf("\$GPGGA"), listOf("\$GPRMC"), listOf("\$PASCD")), messages.map { message -> message.map { it.substringBefore(',') } })
        assertTrue(messages.all { message -> message.single().endsWith("\r\n") })

        settings = GpsSettings(true, rate = 4, framing = NmeaFraming.PARAMETERS)
        messages.clear()
        schedule.tick!!()
        assertEquals(listOf(listOf("\$GPGGA", "\$GPRMC", "\$PASCD")), messages.map { message -> message.map { it.substringBefore(',') } })
        // Each update counts once, however it is framed.
        assertEquals(3L, location.status().sent)
    }

    // A tunnel that fails a send ends the sending; the next subscription starts it again.
    @Test
    fun `a failed send stops the sending`() {
        val schedule = FakeSchedule()
        val location = CarPlayLocation(drive(), satellites = false, nanoTime = { now }, wallMillis = { wallClock },
            schedule = schedule::start)
        location.attach { throw java.io.IOException("link ended") }
        location.onRequested(setOf(1, 2))
        location.start()
        schedule.tick!!()
        assertTrue(schedule.closed)
    }

    // For the GPS page: what the iPhone asked for, whether it listens, and how many updates went.
    @Test
    fun `the link reports what the iPhone asked for and what was sent`() {
        val location = location(drive())
        assertEquals(false, location.status().listening)
        location.onRequested(setOf(1, 2, 4))
        location.start()
        location.latestNmea()
        location.latestNmea()
        assertEquals(LocationStatus(listening = true, requested = setOf(1, 2, 4), sent = 2), location.status())
        location.stop()
        assertEquals(false, location.status().listening)
    }
}
