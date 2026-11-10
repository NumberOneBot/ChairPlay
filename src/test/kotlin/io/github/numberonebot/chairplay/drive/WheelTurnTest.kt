package io.github.numberonebot.chairplay.drive

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** wheelTurn: how far the gauges' steering wheel is turned, by the mode it is shown in. */
class WheelTurnTest {
    // Heading mode (north up, for Apple Maps): the wheel points along the car's heading, whatever the steering input.
    @Test
    fun `heading mode turns to the heading and ignores the steering`() {
        assertEquals(Math.toRadians(90.0), wheelTurn(WheelMode.HEADING, headingDegrees = 90.0, steer = 0.0), 1e-9)
        assertEquals(Math.toRadians(90.0), wheelTurn(WheelMode.HEADING, headingDegrees = 90.0, steer = 1.0), 1e-9)
    }

    // Steering mode (track up, for Google Maps): straight ahead the wheel is centred, whatever the heading.
    @Test
    fun `steering mode is centred going straight, whatever the heading`() {
        assertEquals(0.0, wheelTurn(WheelMode.STEERING, headingDegrees = 0.0, steer = 0.0), 1e-9)
        assertEquals(0.0, wheelTurn(WheelMode.STEERING, headingDegrees = 215.0, steer = 0.0), 1e-9)
    }

    // Steering right turns the wheel clockwise (a positive angle as NanoVG turns), left the mirror of it.
    @Test
    fun `steering turns right clockwise and left its mirror`() {
        val right = wheelTurn(WheelMode.STEERING, headingDegrees = 0.0, steer = 1.0)
        val left = wheelTurn(WheelMode.STEERING, headingDegrees = 0.0, steer = -1.0)
        assertTrue(right > 0.0)
        assertEquals(-right, left, 1e-9)
    }

    // The wheel turns more the further the input is pushed; a game's wheel, past full lock and past a turn, as far.
    @Test
    fun `steering grows with the input, past full lock too`() {
        val half = wheelTurn(WheelMode.STEERING, headingDegrees = 0.0, steer = 0.5)
        val full = wheelTurn(WheelMode.STEERING, headingDegrees = 0.0, steer = 1.0)
        assertTrue(half > 0.0 && half < full)
        assertEquals(2 * full, wheelTurn(WheelMode.STEERING, headingDegrees = 0.0, steer = 2.0), 1e-9)
    }
}
