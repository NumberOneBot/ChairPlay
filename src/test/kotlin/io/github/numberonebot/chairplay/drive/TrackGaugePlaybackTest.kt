package io.github.numberonebot.chairplay.drive

import io.github.numberonebot.chairplay.bridge.TelemetryInput
import io.github.numberonebot.chairplay.bridge.TelemetryRecord
import io.github.numberonebot.chairplay.drive.track.GpxReader
import io.github.numberonebot.chairplay.drive.track.TrackPhase
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
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

    private fun load(gauges: String = "", first: String = "", second: String = "") {
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
        load(gauges = "<cp:redline>12000</cp:redline><cp:top_speed>100</cp:top_speed>")
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
        pass()
        assertScales(380.0, 13.0)

        load()
        assertScales(300.0, 8.0)
    }

    @Test
    fun `recorded peaks expand each scale while explicit settings take precedence`() {
        val fast = "<cp:speed>96</cp:speed><cp:rpm>11585</cp:rpm>"
        val slow = "<cp:speed>50</cp:speed><cp:rpm>7800</cp:rpm>"
        load(first = slow, second = fast)
        assertScales(380.0, 12.0)
        assertNull(driving.gauges()!!.tachometer.redFrom)

        load(gauges = "<cp:top_speed>50</cp:top_speed>", first = fast, second = slow)
        assertScales(180.0, 12.0)

        load(gauges = "<cp:redline>9500</cp:redline>", first = fast, second = slow)
        assertScales(380.0, 10.0)
        assertEquals(9.5, driving.gauges()!!.tachometer.redFrom!!, 0.0)

        load(first = slow, second = slow)
        assertScales(300.0, 8.0)
        assertEquals(7.0, driving.gauges()!!.tachometer.redFrom!!, 0.0)
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
