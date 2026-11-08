package io.github.numberonebot.chairplay.drive

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class GaugesTest {
    // The speedometer as a fast car's: numbered by 20 to 140 and by 40 from there to 300, the numbers evenly apart, so
    // the scale changes at 140; each gap a middle tick and fine tenths.
    @Test
    fun `the speedometer is numbered as a fast car's`() {
        val speed = GaugeScale.speedometer()
        fun values(size: GaugeScale.TickSize) = speed.ticks().filter { it.size == size }.map { it.value }
        assertEquals(listOf(0.0, 20.0, 40.0, 60.0, 80.0, 100.0, 120.0, 140.0, 180.0, 220.0, 260.0, 300.0), values(GaugeScale.TickSize.MAJOR))
        assertEquals(listOf(10.0, 30.0, 50.0, 70.0, 90.0, 110.0, 130.0, 160.0, 200.0, 240.0, 280.0), values(GaugeScale.TickSize.MIDDLE))
        val fine = values(GaugeScale.TickSize.FINE)
        assertEquals(88, fine.size)
        assertEquals(listOf(2.0, 4.0, 6.0, 8.0, 12.0), fine.take(5).map { Math.round(it * 1e6) / 1e6 })
        assertTrue(fine.any { Math.abs(it - 144.0) < 1e-9 })
        val step = speed.angle(20.0) - speed.angle(0.0)
        assertEquals(step, speed.angle(140.0) - speed.angle(120.0), 1e-9)
        assertEquals(step, speed.angle(180.0) - speed.angle(140.0), 1e-9)
        assertEquals(step, speed.angle(260.0) - speed.angle(220.0), 1e-9)
        assertEquals(Math.toRadians(GaugeScale.START_DEGREES + GaugeScale.SWEEP_DEGREES), speed.angle(400.0), 1e-9)
        assertEquals(Math.toRadians(GaugeScale.START_DEGREES), speed.angle(-5.0), 1e-9)
    }

    // The default car's tachometer: up to 8000 rpm, red from 7000 and dashed red half a thousand before it, a middle
    // tick at each half thousand.
    @Test
    fun `the tachometer fits the car`() {
        val tach = GaugeScale.tachometer(CarSpec())
        assertEquals(8.0, tach.max, 0.0)
        assertEquals(7.0, tach.redFrom!!, 0.0)
        assertEquals(6.5, tach.warnFrom!!, 0.0)
        assertEquals((0..8).map { it.toDouble() }, tach.ticks().filter { it.size == GaugeScale.TickSize.MAJOR }.map { it.value })
        assertEquals(tach.angle(1.0) - tach.angle(0.0), tach.angle(8.0) - tach.angle(7.0), 1e-9)
        assertEquals(8, tach.ticks().count { it.size == GaugeScale.TickSize.MIDDLE })
        // A fine tick at each hundred rpm.
        assertEquals(64, tach.ticks().count { it.size == GaugeScale.TickSize.FINE })
    }

    // A game's redline: red from it, dashed half a thousand before, and numbered to the next whole thousand at least
    // half a thousand past it.
    @Test
    fun `the tachometer fits any redline`() {
        fun check(redline: Double, max: Double) {
            val tach = GaugeScale.tachometer(redline)
            assertEquals("$redline", max, tach.max, 0.0)
            assertEquals((0..max.toInt()).map { it.toDouble() }, tach.numbers)
            assertEquals(redline / 1000, tach.redFrom!!, 0.0)
            assertEquals(redline / 1000 - 0.5, tach.warnFrom!!, 0.0)
        }
        check(9000.0, 10.0)
        check(7500.0, 8.0)
        check(7600.0, 9.0)
        check(2500.0, 3.0)
        check(18500.0, 19.0)
        assertEquals(GaugeScale.tachometer(CarSpec()), GaugeScale.tachometer(CarSpec().redlineRpm))
    }

    // The gauges are drive mode's: on with it (F10 or the GPS page), gone with it, and never while the GPS is off.
    @Test
    fun `the gauges show exactly while drive mode is on`() {
        val settings = GpsSettings(true, start = GeoPoint(50.0, 30.0), headingDegrees = 135.0)
        val driving = Driving(settings, gamepad = { null }, nanoTime = { 0L })
        assertNull(driving.gauges())
        assertTrue(driving.driveMode(true))
        driving.pass()
        val view = driving.gauges()!!
        assertEquals(0.0, view.speedKmh, 0.0)
        // The car starts parked, its engine off.
        assertEquals("P", view.gear)
        assertEquals(0.0, view.rpm, 0.0)
        // The wheel turns to where the car faces, and the panel beside it shows where the car is.
        assertEquals(135.0, view.headingDegrees, 0.0)
        assertEquals(GeoPoint(50.0, 30.0), view.position)
        // Large unless gps.gauges says small.
        assertEquals(GaugeSize.LARGE, view.size)
        driving.saved(settings.copy(gauges = GaugeSize.SMALL), setOf("gps.gauges"))
        assertEquals(GaugeSize.SMALL, driving.gauges()!!.size)
        assertTrue(driving.driveMode(false))
        driving.pass()
        assertNull(driving.gauges())

        val off = Driving(GpsSettings(false, start = GeoPoint(50.0, 30.0)), gamepad = { null }, nanoTime = { 0L })
        assertFalse(off.driveMode(true))
        off.pass()
        assertNull(off.gauges())
    }
}
