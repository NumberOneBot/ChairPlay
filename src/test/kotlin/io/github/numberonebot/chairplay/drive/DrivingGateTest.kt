package io.github.numberonebot.chairplay.drive

import io.github.numberonebot.chairplay.platform.LocationResult
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** Driving under the GPS output: a car that sends nowhere does not drive (P-06), and an operation holds it (S-09, V52). */
class DrivingGateTest {
    private var now = 0L
    private val kyiv = GeoPoint(50.4501, 30.5234)
    private val driving = Driving(GpsSettings(true, start = kyiv), gamepad = { null }, nanoTime = { now },
        locate = { LocationResult.Unknown }, lookup = { it.run() })

    private fun drive(seconds: Double) {
        repeat((seconds * 60).toInt()) {
            now += 16_666_667
            driving.pass()
        }
    }

    @Test
    fun `a car whose channel cannot send does not drive`() {
        driving.live = { false }
        driving.control.scripted = CarInput(1.0, 0.0, 0.0)

        drive(1.0)

        assertEquals(kyiv, driving.drive.snapshot().position)
        assertEquals(0.0, driving.drive.snapshot().car.speed, 0.0)
    }

    @Test
    fun `an operation stops the car and lets go of its input, keeping drive mode, and the hold counts for nothing`() {
        driving.control.scripted = CarInput(1.0, 0.0, 0.0)
        driving.driveMode(true)
        drive(1.0)
        assertTrue(driving.drive.snapshot().car.speed > 0)

        var held = true
        driving.held = { held }
        drive(5.0)
        val there = driving.drive.snapshot()
        assertEquals(0.0, there.car.speed, 0.0)
        assertEquals(CarInput(), there.input)
        assertTrue(driving.control.keyboard)

        held = false
        now += 1_000_000_000
        driving.pass()
        assertTrue("one pass after the hold moves no further than a frame",
            distance(there.position, driving.drive.snapshot().position) < 1.0)
    }

    private fun distance(a: GeoPoint, b: GeoPoint) =
        kotlin.math.hypot(a.latitude - b.latitude, a.longitude - b.longitude) * 111_000
}
