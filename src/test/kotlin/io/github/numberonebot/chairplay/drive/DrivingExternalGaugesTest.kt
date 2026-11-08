package io.github.numberonebot.chairplay.drive

import io.github.numberonebot.chairplay.bridge.TelemetryInput
import io.github.numberonebot.chairplay.bridge.TelemetryRecord
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** The gauges while a game's telemetry drives the GPS car: its gear, its rpm, and its tachometer. */
class DrivingExternalGaugesTest {
    private var now = 0L
    private val source = FakeTelemetrySource()
    private var counter = 0

    private fun record(gear: Int? = 3, rpm: Double? = 4200.0, redline: Double? = null) = TelemetryRecord(
        counter = counter++, paused = false, gear = gear, speed = 20.0, headingDegrees = 135.0,
        latitude = 48.85, longitude = 2.29, rpm = rpm, source = "AC", redline = redline,
    )

    // Drive mode on, and a game found and driving.
    private fun driving(): Driving {
        val driving = Driving(
            GpsSettings(true, start = GeoPoint(50.45, 30.52), external = true),
            gamepad = { null },
            nanoTime = { now },
            lookup = { it.run() },
            telemetry = { TelemetryInput(source, {}, { now }) },
        )
        driving.toggleKeyboard()
        source.record = record()
        driving.pass()
        return driving
    }

    private fun Driving.sent(record: TelemetryRecord): GaugeView {
        now += 16_666_667L
        source.record = record
        pass()
        return gauges()!!
    }

    @Test
    fun `the gauges show the game's gear, whatever the GPS car's gearbox has`() {
        val driving = driving()
        assertEquals("R", driving.sent(record(gear = -1)).gear)
        assertEquals("N", driving.sent(record(gear = 0)).gear)
        assertEquals("D1", driving.sent(record(gear = 1)).gear)
        assertEquals("D12", driving.sent(record(gear = 12)).gear)
    }

    @Test
    fun `the tachometer takes the game's redline while it gives one`() {
        val driving = driving()
        val own = GaugeScale.tachometer(CarSpec())
        assertEquals(own, driving.sent(record()).tachometer)

        val game = driving.sent(record(rpm = 8800.0, redline = 9000.0))
        assertEquals(GaugeScale.tachometer(9000.0), game.tachometer)
        // Past the GPS car's own red line, as the game revs.
        assertEquals(8800.0, game.rpm, 0.0)

        assertEquals(own, driving.sent(record(redline = null)).tachometer)
    }

    // A pause keeps the game's dial; only the game gone for good gives the GPS car's back.
    @Test
    fun `the game's tachometer stays until the game is gone`() {
        val driving = driving()
        driving.sent(record(redline = 9000.0))
        now += 1_000_000_000L
        driving.pass()
        assertEquals(GaugeScale.tachometer(9000.0), driving.gauges()!!.tachometer)
        now += 5_000_000_000L
        driving.pass()
        assertEquals(GaugeScale.tachometer(CarSpec()), driving.gauges()!!.tachometer)
    }

    // The GPS page's drive mode switch puts the gauges up while a game drives, not only once it is gone.
    @Test
    fun `drive mode turned on while a game drives shows the gauges`() {
        val driving = driving()
        driving.toggleKeyboard()
        assertTrue(driving.driveMode(true))
        now += 16_666_667L
        source.record = record()
        driving.pass()
        assertNotNull(driving.gauges())
    }
}
