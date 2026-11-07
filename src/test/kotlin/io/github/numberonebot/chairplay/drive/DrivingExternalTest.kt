package io.github.numberonebot.chairplay.drive

import io.github.numberonebot.chairplay.bridge.TelemetryInput
import io.github.numberonebot.chairplay.bridge.TelemetryRecord
import io.github.numberonebot.chairplay.bridge.TelemetrySource
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.nio.ByteBuffer

/** A game's telemetry driving the GPS car while gps.external is on (DrivingTest covers the gamepad and keys). */
class DrivingExternalTest {
    private val kyiv = GeoPoint(50.4501, 30.5234)
    private val paris = GeoPoint(48.85, 2.29)
    private var now = 0L
    private val placeSaved = mutableListOf<Map<String, String>>()
    private val source = FakeSource()
    private var inputs = 0
    // The gamepad's right trigger, -1 let go.
    private var throttle = -1f

    private class FakeSource : TelemetrySource {
        var record: TelemetryRecord? = null
        var opens = 0
        var reads = 0
        var closes = 0

        override fun open(): Boolean { opens++; return true }

        override fun read(into: ByteBuffer): Boolean {
            reads++
            TelemetryRecord.write(record ?: return false, into)
            return true
        }

        override fun close() { closes++ }
    }

    private fun record(counter: Int, speed: Double = 20.0, paused: Boolean = false, gear: Int? = 3) = TelemetryRecord(
        counter = counter, paused = paused, gear = gear, speed = speed, headingDegrees = 135.0,
        latitude = paris.latitude, longitude = paris.longitude, rpm = 4200.0, source = "AC",
    )

    private val on = GpsSettings(true, start = kyiv, external = true)

    private fun driving(settings: GpsSettings = on) = Driving(
        settings,
        gamepad = { floatArrayOf(0f, 0f, 0f, 0f, -1f, throttle) },
        nanoTime = { now },
        lookup = { it.run() },
        telemetry = { inputs++; TelemetryInput(source, {}, { now }) },
    ).also { it.savePlace = { place -> placeSaved += place } }

    private fun at(seconds: Double) { now = (seconds * 1e9).toLong() }

    @Test
    fun `telemetry drives instead of the gamepad`() {
        val driving = driving()
        throttle = 1f
        source.record = record(1)

        assertEquals(DriveControl.FRAME_SECONDS, driving.pass()!!, 0.0)
        at(0.1)
        source.record = record(2)
        driving.pass()

        val snapshot = driving.drive.snapshot()
        assertEquals(paris, snapshot.position)
        assertEquals(135.0, snapshot.car.headingDegrees, 0.0)
        assertEquals(20.0, snapshot.car.speed, 0.0)
        assertEquals(4200.0, snapshot.car.rpm, 0.0)
        assertEquals(3, snapshot.car.gear)
        assertTrue(driving.placed)
    }

    @Test
    fun `a halted game stops the car where it is`() {
        val driving = driving()
        source.record = record(1)
        driving.pass()
        at(0.6)

        assertEquals(DriveControl.GAMEPAD_POLL_SECONDS, driving.pass()!!, 0.0)
        val snapshot = driving.drive.snapshot()
        assertFalse(driving.drive.moving)
        assertEquals(paris, snapshot.position)
        assertEquals(135.0, snapshot.car.headingDegrees, 0.0)
        assertTrue(placeSaved.isEmpty())
    }

    @Test
    fun `a paused game stops the car`() {
        val driving = driving()
        source.record = record(1)
        driving.pass()
        at(0.1)
        source.record = record(2, paused = true)

        driving.pass()
        assertFalse(driving.drive.moving)
    }

    @Test
    fun `when the game goes the place is saved`() {
        val driving = driving()
        source.record = record(1)
        driving.pass()
        at(0.6)
        driving.pass()
        at(5.0)

        driving.pass()
        assertEquals(mapOf("gps.start" to "48.85000, 2.29000", "gps.heading" to "135"), placeSaved.single())
        at(5.1)
        driving.pass()
        assertEquals(1, placeSaved.size)
    }

    @Test
    fun `the gamepad drives again once the game is gone`() {
        val driving = driving()
        source.record = record(1, speed = 0.0)
        driving.pass()
        at(5.0)
        driving.pass()
        throttle = 1f
        repeat(60) { now += 16_666_667L; driving.pass() }

        assertTrue(driving.drive.moving)
        assertTrue(driving.drive.snapshot().position.latitude < paris.latitude)
    }

    // A game standing in neutral, then gone: the gamepad drives the car on from there.
    @Test
    fun `a game gone in neutral hands the car back to the gamepad`() {
        val driving = driving()
        source.record = record(1, speed = 0.0, gear = 0)
        driving.pass()
        at(0.6)
        driving.pass()
        at(5.0)
        driving.pass()
        throttle = 1f
        repeat(60) { now += 16_666_667L; driving.pass() }

        assertTrue(driving.drive.moving)
    }

    // A truck in its twelfth gear, gone with no halted pass between (the loop stalled): the gamepad drives on.
    @Test
    fun `a game gone in a gear the car does not have hands the car back`() {
        val driving = driving()
        source.record = record(1, gear = 12)
        driving.pass()
        at(6.0)
        driving.pass()
        throttle = 1f
        repeat(60) { now += 16_666_667L; driving.pass() }

        assertTrue(driving.drive.moving)
        assertTrue(driving.drive.snapshot().car.gear in 1..CarSpec().gearRatios.size)
    }

    @Test
    fun `a game's gear shows while it drives`() {
        val driving = driving()
        source.record = record(1, gear = 12)
        driving.pass()
        at(0.1)
        source.record = record(2, gear = 12)
        driving.pass()

        assertEquals(12, driving.drive.snapshot().car.gear)
    }

    @Test
    fun `an idle loop keeps polling while external driving is on`() {
        val driving = Driving(on, gamepad = { null }, nanoTime = { now }, lookup = { it.run() },
            telemetry = { TelemetryInput(source, {}, { now }) })

        val due = driving.pass()
        assertTrue("due $due", due != null && due <= DriveControl.GAMEPAD_POLL_SECONDS)
        assertEquals(1, source.reads)
    }

    @Test
    fun `without the switch no telemetry is read`() {
        val driving = driving(on.copy(external = false))
        source.record = record(1)
        driving.pass()
        driving.pass()

        assertEquals(0, inputs)
        assertEquals(0, source.opens)
        assertEquals(kyiv.latitude, driving.drive.snapshot().position.latitude, 1e-3)
    }

    @Test
    fun `the GPS off reads no telemetry`() {
        val driving = driving(on.copy(enabled = false))
        source.record = record(1)
        driving.pass()

        assertEquals(0, inputs)
        assertEquals(0, source.opens)
    }

    // The switch turned off closes the file; turned on again, a fresh input reads it, knowing nothing of the last one.
    @Test
    fun `the switch off closes the telemetry and on again starts afresh`() {
        val driving = driving()
        source.record = record(1)
        driving.pass()

        driving.saved(on.copy(external = false), setOf("gps.external"))
        driving.pass()
        assertEquals(1, source.closes)
        assertFalse(driving.drive.moving)
        assertEquals("48.85000, 2.29000", placeSaved.single()["gps.start"])

        at(10.0)
        driving.saved(on, setOf("gps.external"))
        assertEquals(DriveControl.FRAME_SECONDS, driving.pass()!!, 0.0)
        assertEquals(2, inputs)
    }

    @Test
    fun `the GPS off closes the telemetry`() {
        val driving = driving()
        source.record = record(1)
        driving.pass()

        driving.saved(on.copy(enabled = false), setOf("gps.enabled"))
        driving.pass()
        assertEquals(1, source.closes)
        assertFalse(driving.drive.moving)
    }

    // A switch saved on the settings thread wakes the window loop, which may be waiting for an event.
    @Test
    fun `turning external driving on wakes the window loop`() {
        val driving = driving(on.copy(external = false))
        var woken = 0
        driving.wake = { woken++ }

        driving.saved(on, setOf("gps.external"))
        assertEquals(1, woken)
        driving.saved(on.copy(headingDegrees = 90.0), setOf("gps.heading"))
        assertEquals(1, woken)
    }
}
