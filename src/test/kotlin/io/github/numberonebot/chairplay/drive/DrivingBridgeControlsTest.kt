package io.github.numberonebot.chairplay.drive

import io.github.numberonebot.chairplay.bridge.GameControls
import io.github.numberonebot.chairplay.bridge.TelemetryInput
import io.github.numberonebot.chairplay.bridge.TelemetryRecord
import io.github.numberonebot.chairplay.config.CarDisplay
import io.github.numberonebot.chairplay.input.Control
import io.github.numberonebot.chairplay.phone.KnobInput
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * A game's controls through the telemetry file while bridge.telemetry is on, whatever the GPS does (owner, 2026-11-24);
 * DrivingExternalTest covers the car a game's position drives.
 */
class DrivingBridgeControlsTest {
    private val kyiv = GeoPoint(50.4501, 30.5234)
    private val paris = GeoPoint(48.85, 2.29)
    private var now = 0L
    private val source = FakeTelemetrySource()
    private val sent = mutableListOf<Control>()

    private fun record(counter: Int, buttons: Int = 0, knob: Int = 0, position: Boolean = false, paused: Boolean = false) =
        TelemetryRecord(
            counter = counter, paused = paused, gear = null, speed = 20.0, headingDegrees = 135.0,
            latitude = paris.latitude, longitude = paris.longitude, rpm = null, source = "Bridge test",
            position = position, controls = GameControls(buttons, knob, 0, 0),
        )

    private val on = GpsSettings(true, start = kyiv, external = true)

    private fun driving(settings: GpsSettings = on) = Driving(
        settings,
        gamepad = { null },
        nanoTime = { now },
        lookup = { it.run() },
        telemetry = { TelemetryInput(source, {}, { now }) },
    ).also { it.bridgeControls.send = { control -> sent += control } }

    private fun at(seconds: Double) { now = (seconds * 1e9).toLong() }

    /** The status tick's look for a game, then a pass of the window loop. */
    private fun Driving.step(): Double? {
        lookForGame()
        return pass()
    }

    /** A game found and its first news read: where it stands, from which its next records count. */
    private fun Driving.found(): Driving = also {
        source.record = record(0)
        it.step()
        source.record = record(1)
        it.step()
    }

    @Test
    fun `a game's controls work with the GPS off and leave the car where it is`() {
        val driving = driving(on.copy(enabled = false)).found()
        source.record = record(2, buttons = SELECT, position = true)

        driving.step()
        assertEquals(listOf(Control.Knob(KnobInput(select = true), false)), sent)
        assertEquals(kyiv, driving.drive.snapshot().position)
    }

    @Test
    fun `a record with no position sends its controls and leaves the car alone`() {
        val driving = driving().found()
        source.record = record(2, knob = 2)

        driving.step()
        assertEquals(listOf(Control.Knob(KnobInput(wheel = 2), true)), sent)
        assertEquals(kyiv, driving.drive.snapshot().position)
        assertFalse(driving.drive.moving)
    }

    // In a game's pause menu the controls are what is wanted most: they are read 60 times a second there too.
    @Test
    fun `a paused game's controls are read every frame`() {
        val driving = driving().found()
        at(0.1)
        source.record = record(2, paused = true)

        assertEquals(DriveControl.FRAME_SECONDS, driving.step()!!, 0.0)
    }

    // A game gone quiet for half a second, its writer stopped or stuck, cannot leave select held.
    @Test
    fun `a game gone quiet lets go of the knob`() {
        val driving = driving().found()
        source.record = record(2, buttons = SELECT)
        driving.step()
        sent.clear()
        at(0.6)

        driving.step()
        assertEquals(listOf(Control.Knob(KnobInput(), false)), sent)
    }

    @Test
    fun `the switch off lets go of the knob`() {
        val driving = driving().found()
        source.record = record(2, buttons = BACK)
        driving.step()
        sent.clear()

        driving.saved(on.copy(external = false), setOf("bridge.telemetry"))
        driving.step()
        assertEquals(listOf(Control.Knob(KnobInput(), false)), sent)
        assertEquals(1, source.closes)
    }

    // The GPS turned off hands a car the game drove back, standing where it was, and the controls go on.
    @Test
    fun `the GPS off hands the car back and keeps reading the game`() {
        val driving = driving().found()
        source.record = record(2, position = true)
        driving.step()
        assertEquals(paris, driving.drive.snapshot().position)

        driving.saved(on.copy(enabled = false), setOf("gps.enabled"))
        driving.step()
        assertFalse(driving.drive.moving)
        assertEquals(0, source.closes)
        source.record = record(3, buttons = HOME, position = true)
        driving.step()
        assertTrue(Control.Knob(KnobInput(home = true), false) in sent)
    }

    // A game's car display goes to the app once per change, the GPS on or off: the app reconnects for each.
    @Test
    fun `a game's car display is told once per change`() {
        val shown = mutableListOf<CarDisplay>()
        val driving = driving(on.copy(enabled = false)).also { it.carDisplay = { display -> shown += display } }.found()
        source.record = record(2).copy(display = CarDisplay(800, 480, 180))
        driving.step()
        source.record = record(3).copy(display = CarDisplay(800, 480, 180))
        driving.step()
        source.record = record(4).copy(display = CarDisplay(1280, 720, 300))
        driving.step()

        assertEquals(listOf(CarDisplay(800, 480, 180), CarDisplay(1280, 720, 300)), shown)
    }

    private companion object {
        const val SELECT = 1 shl 4
        const val BACK = 1 shl 5
        const val HOME = 1 shl 6
    }
}
