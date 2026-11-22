package io.github.numberonebot.chairplay.drive

import io.github.numberonebot.chairplay.bridge.TelemetryInput
import io.github.numberonebot.chairplay.bridge.TelemetryRecord
import io.github.numberonebot.chairplay.drive.track.GpxReader
import io.github.numberonebot.chairplay.drive.track.TrackPhase
import org.junit.Assert.assertEquals
import org.junit.Test

class TrackGaugePlaybackTest {
    private var now = 0L
    private val source = FakeTelemetrySource()
    private val driving = Driving(
        GpsSettings(true, start = GeoPoint(50.0, 6.0), external = true),
        gamepad = { null }, nanoTime = { now },
        telemetry = { TelemetryInput(source, {}, { now }) },
    )

    init {
        driving.driveMode(true)
        pass()
    }

    private fun pass(nanos: Long = 16_666_667L) {
        now += nanos
        driving.pass()
    }

    private fun load(gauges: String = "", first: String = "", second: String = "", third: String? = null) {
        val lastPoint = third?.let {
            """
              <trkpt lat="50.002" lon="6">
                <time>2024-11-23T07:20:56Z</time>
                <extensions><cp:telemetry>$it</cp:telemetry></extensions>
              </trkpt>
            """.trimIndent()
        }.orEmpty()
        val gpx = """
            <gpx xmlns="http://www.topografix.com/GPX/1/1" xmlns:cp="urn:chairplay:gpx:telemetry:1"
                 version="1.1" creator="ChairPlay">
              <trk>
                <extensions><cp:gauges>$gauges</cp:gauges></extensions>
                <trkseg>
                  <trkpt lat="50" lon="6">
                    <time>2024-11-23T07:20:36Z</time>
                    <extensions><cp:telemetry>$first</cp:telemetry></extensions>
                  </trkpt>
                  <trkpt lat="50.001" lon="6">
                    <time>2024-11-23T07:20:46Z</time>
                    <extensions><cp:telemetry>$second</cp:telemetry></extensions>
                  </trkpt>
                  $lastPoint
                </trkseg>
              </trk>
            </gpx>
        """.trimIndent()
        driving.load(GpxReader.read("track.gpx", gpx.byteInputStream()))
    }

    private fun assertScales(speed: Double, tach: Double) {
        val gauges = driving.gauges()!!
        assertEquals(speed, gauges.speedometer.max, 0.0)
        assertEquals(tach, gauges.tachometer.max, 0.0)
    }

    @Test
    fun `opening sets scales that outlast playback until manual driving starts`() {
        load(
            gauges = "<cp:redline>12000</cp:redline><cp:top_speed>100</cp:top_speed>",
            second = "<cp:rpm>11000</cp:rpm><cp:gear>8</cp:gear>",
        )
        assertScales(380.0, 13.0)
        assertEquals(12.0, driving.gauges()!!.tachometer.redFrom!!, 0.0)

        driving.track!!.play()
        pass()
        driving.track!!.pause()
        pass()
        assertScales(380.0, 13.0)

        driving.track!!.stop()
        pass()
        assertScales(380.0, 13.0)
        driving.control.scripted = CarInput(throttle = 1.0)
        pass()
        assertScales(300.0, 8.0)

        driving.control.scripted = CarInput()
        driving.track!!.play()
        pass()
        assertScales(380.0, 13.0)
        pass(10_000_000_000L)
        assertEquals(TrackPhase.STOPPED, driving.track!!.phase)
        assertReadings(0.0, 0.0, "P")
        pass()
        assertReadings(0.0, 0.0, "P")
        assertScales(380.0, 13.0)

        load()
        assertScales(300.0, 8.0)
    }

    @Test
    fun `a recording ending during a GPS hold parks at the last point and clears its readings`() {
        val stationary = "<cp:speed>0</cp:speed><cp:rpm>11000</cp:rpm><cp:gear>8</cp:gear>"
        load(first = stationary, second = stationary)
        driving.track!!.play()
        pass(5_000_000_000L)
        assertReadings(0.0, 11000.0, "D8")

        driving.held = { true }
        pass(5_000_000_000L)

        assertEquals(TrackPhase.STOPPED, driving.track!!.phase)
        assertReadings(0.0, 0.0, "P")
        assertEquals(GeoPoint(50.001, 6.0), driving.drive.snapshot().position)
        pass()
        assertReadings(0.0, 0.0, "P")
    }

    @Test
    fun `recorded peaks expand each scale while explicit settings take precedence`() {
        val fast = "<cp:speed>96</cp:speed><cp:rpm>11585</cp:rpm>"
        val slow = "<cp:speed>50</cp:speed><cp:rpm>7800</cp:rpm>"
        load(first = slow, second = fast)
        assertScales(380.0, 12.0)
        assertEquals(11.5, driving.gauges()!!.tachometer.redFrom!!, 0.0)

        load(gauges = "<cp:top_speed>50</cp:top_speed>", first = fast, second = slow)
        assertScales(180.0, 12.0)

        load(gauges = "<cp:redline>9500</cp:redline>", first = fast, second = slow)
        assertScales(380.0, 10.0)
        assertEquals(9.5, driving.gauges()!!.tachometer.redFrom!!, 0.0)

        load(first = slow, second = slow)
        assertScales(300.0, 8.0)
        assertEquals(7.0, driving.gauges()!!.tachometer.redFrom!!, 0.0)

        load(second = "<cp:rpm>11800</cp:rpm>")
        assertScales(300.0, 13.0)
        assertEquals(12.0, driving.gauges()!!.tachometer.redFrom!!, 0.0)
    }

    @Test
    fun `GPX point telemetry drives the speed and RPM needles and changes gears at each point`() {
        load(
            first = "<cp:speed>20</cp:speed><cp:rpm>0</cp:rpm><cp:gear>0</cp:gear>",
            second = "<cp:speed>30</cp:speed><cp:rpm>8000</cp:rpm><cp:gear>3</cp:gear>",
            third = "<cp:speed>40</cp:speed><cp:rpm>9000</cp:rpm><cp:gear>4</cp:gear>",
        )
        driving.track!!.play()
        pass(0)
        assertReadings(72.0, 0.0, "N")
        pass(5_000_000_000)
        assertReadings(90.0, 4000.0, "N")
        pass(5_000_000_000)
        assertReadings(108.0, 8000.0, "D3")
        driving.track!!.pause()
        pass()
        assertReadings(0.0, 8000.0, "D3")
    }

    @Test
    fun `the recording's pedals reach the gauges while it plays and rest while it pauses`() {
        val pressed = "<cp:speed>30</cp:speed><cp:throttle>60</cp:throttle><cp:brake>20</cp:brake>"
        load(first = pressed, second = pressed)
        driving.track!!.play()
        pass()
        pass()
        assertEquals(0.6 to 0.2, driving.gauges()!!.run { throttle to brake })

        driving.track!!.pause()
        pass()
        assertEquals(0.0 to 0.0, driving.gauges()!!.run { throttle to brake })
    }

    private fun assertReadings(speedKmh: Double, rpm: Double, gear: String) {
        val gauges = driving.gauges()!!
        assertEquals(speedKmh, gauges.speedKmh, 1e-6)
        assertEquals(rpm, gauges.rpm, 1e-6)
        assertEquals(gear, gauges.gear)
    }

    @Test
    fun `ignores invalid fields and similarly named fields outside the ChairPlay namespace`() {
        load(
            gauges = "<redline>12000</redline><cp:redline><cp:value>12000</cp:value></cp:redline><cp:top_speed>100</cp:top_speed>",
            second = "<cp:rpm>NaN</cp:rpm>",
        )
        assertScales(380.0, 8.0)
    }

    @Test
    fun `an external app taking the car replaces the track scales`() {
        load(gauges = "<cp:redline>12000</cp:redline><cp:top_speed>100</cp:top_speed>")
        assertScales(380.0, 13.0)
        val record = TelemetryRecord(
            counter = 1, paused = false, gear = 2, speed = 10.0, headingDegrees = 0.0,
            latitude = 50.0, longitude = 6.0, rpm = 4000.0, source = "Game", redline = 9000.0, topSpeed = 80.0,
        )
        source.record = record
        driving.lookForGame()
        source.record = record.copy(counter = 2)
        driving.lookForGame()
        pass()
        assertScales(300.0, 10.0)

        driving.track!!.play()
        pass()
        assertScales(380.0, 13.0)
    }
}
