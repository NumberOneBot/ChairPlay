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

    // GLFW's axes, null for no gamepad: the left stick's X first, the left and right triggers last, -1 let go.
    private var axes: FloatArray? = null

    private fun record(gear: Int? = 3, rpm: Double? = 4200.0, redline: Double? = null, topSpeed: Double? = null) = TelemetryRecord(
        counter = counter++, paused = false, gear = gear, speed = 20.0, headingDegrees = 135.0,
        latitude = 48.85, longitude = 2.29, rpm = rpm, source = "AC", redline = redline, topSpeed = topSpeed,
    )

    // Drive mode on, and a game found and driving.
    private fun driving(): Driving {
        val driving = Driving(
            GpsSettings(true, start = GeoPoint(50.45, 30.52), external = true),
            gamepad = { axes },
            nanoTime = { now },
            lookup = { it.run() },
            telemetry = { TelemetryInput(source, {}, { now }) },
        )
        driving.toggleKeyboard()
        source.record = record()
        driving.lookForGame()
        return driving
    }

    private fun Driving.sent(record: TelemetryRecord): GaugeView {
        now += 16_666_667L
        source.record = record
        lookForGame()
        pass()
        return gauges()!!
    }

    // The game's pedals on the gauges' bars, and its wheel turned as far as the game turns it, past a full turn too;
    // one it does not give shows at rest.
    @Test
    fun `the gauges show the game's pedals and wheel`() {
        val driving = driving()
        // As the file's 32-bit floats carry them.
        val given = driving.sent(record().copy(throttle = 0.8, brake = 0.1, steerDegrees = -450.0))
        assertEquals(0.8, given.throttle, 1e-6)
        assertEquals(0.1, given.brake, 1e-6)
        assertEquals(Math.toRadians(-450.0), wheelTurn(given.wheelMode, given.headingDegrees, given.steer), 1e-6)
        val none = driving.sent(record())
        assertEquals(listOf(0.0, 0.0, 0.0), listOf(none.throttle, none.brake, none.steer))
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

    // The speedometer runs to the game's top speed while it gives one (m/s: 100 is 360 km/h, a dial to 380).
    @Test
    fun `the speedometer takes the game's top speed while it gives one`() {
        val driving = driving()
        assertEquals(GaugeScale.speedometer(), driving.sent(record()).speedometer)
        assertEquals(380.0, driving.sent(record(topSpeed = 100.0)).speedometer.max, 0.0)
        assertEquals(GaugeScale.speedometer(), driving.sent(record(topSpeed = null)).speedometer)
    }

    // The game's dials stay through a pause and after the game is gone, until the gamepad or keys first drive the car;
    // the game back, its dials are back.
    @Test
    fun `the game's dials stay until our own controls drive`() {
        val driving = driving()
        driving.sent(record(redline = 9000.0, topSpeed = 100.0))
        now += 1_000_000_000L
        driving.pass()
        assertEquals(GaugeScale.tachometer(9000.0), driving.gauges()!!.tachometer)
        now += 5_000_000_000L
        driving.pass()
        assertEquals(GaugeScale.tachometer(9000.0), driving.gauges()!!.tachometer)
        assertEquals(380.0, driving.gauges()!!.speedometer.max, 0.0)

        axes = floatArrayOf(0f, 0f, 0f, 0f, -1f, 1f)
        now += 16_666_667L
        driving.pass()
        assertEquals(GaugeScale.tachometer(CarSpec()), driving.gauges()!!.tachometer)
        assertEquals(GaugeScale.speedometer(), driving.gauges()!!.speedometer)

        axes = floatArrayOf(0f, 0f, 0f, 0f, -1f, -1f)
        assertEquals(GaugeScale.tachometer(9000.0), driving.sent(record(redline = 9000.0)).tachometer)
    }

    // The GPS page's drive mode switch puts the gauges up while a game drives, not only once it is gone.
    @Test
    fun `drive mode turned on while a game drives shows the gauges`() {
        val driving = driving()
        driving.toggleKeyboard()
        assertTrue(driving.driveMode(true))
        now += 16_666_667L
        source.record = record()
        driving.lookForGame()
        driving.pass()
        assertNotNull(driving.gauges())
    }
}
