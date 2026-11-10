package io.github.numberonebot.chairplay.drive

import io.github.numberonebot.chairplay.config.Applies
import io.github.numberonebot.chairplay.config.EditableSettings
import io.github.numberonebot.chairplay.config.configWith
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.atan2
import kotlin.math.hypot

/** gps.gauges.style: where each dial's scale starts, which way it runs, and where its insets sit in the gap it leaves. */
class GaugeDesignTest {
    /** A clock hour as NanoVG turns: 3 o'clock is 0 degrees, clockwise, 12 o'clock is -90. */
    private fun clock(hour: Double) = Math.toRadians(hour * 30 - 90)

    private fun assertAt(hour: Double, radians: Double) =
        assertEquals("at $hour o'clock", 0.0, Math.IEEEremainder(radians - clock(hour), 2 * Math.PI), 1e-9)

    @Test
    fun `German dials run clockwise over the top from the lower left, as before`() {
        val tach = GaugeScale.tachometer(CarSpec().redlineRpm, GaugeDesign.GERMAN.tachometer)
        assertEquals(GaugeScale.tachometer(CarSpec()), tach)
        assertEquals(Math.toRadians(GaugeScale.START_DEGREES), tach.angle(0.0), 1e-9)
        assertEquals(Math.toRadians(GaugeScale.START_DEGREES + GaugeScale.SWEEP_DEGREES), tach.angle(tach.max), 1e-9)
    }

    @Test
    fun `Italian dials run clockwise from 6 o'clock to 3`() {
        for (scale in listOf(
            GaugeScale.speedometer(SpeedUnits.METRIC, GaugeDesign.ITALIAN.speedometer),
            GaugeScale.tachometer(CarSpec().redlineRpm, GaugeDesign.ITALIAN.tachometer),
        )) {
            assertAt(6.0, scale.angle(0.0))
            assertAt(3.0, scale.angle(scale.max))
        }
        // Clockwise, over 9 and 12 o'clock: the evenly numbered tachometer is at 9 a third of the way.
        val tach = GaugeScale.tachometer(CarSpec().redlineRpm, GaugeDesign.ITALIAN.tachometer)
        assertAt(9.0, tach.angle(tach.max / 3))
        assertAt(12.0, tach.angle(tach.max * 2 / 3))
    }

    // Aston Martin's: the speedometer on the left, the tachometer on the right, their needles turning apart.
    @Test
    fun `British dials run apart, the speedometer from 5 to 2 o'clock and the tachometer from 7 to 10`() {
        val tach = GaugeScale.tachometer(CarSpec().redlineRpm, GaugeDesign.BRITISH.tachometer)
        val speed = GaugeScale.speedometer(SpeedUnits.METRIC, GaugeDesign.BRITISH.speedometer)
        assertTrue(GaugeDesign.BRITISH.tachometerOnRight)
        assertTrue(GaugeDesign.entries.filter { it.tachometerOnRight } == listOf(GaugeDesign.BRITISH))
        assertAt(5.0, speed.angle(0.0))
        assertAt(2.0, speed.angle(speed.max))
        assertTrue("the speedometer turns clockwise", speed.angle(20.0) > speed.angle(0.0))
        assertAt(7.0, tach.angle(0.0))
        assertAt(10.0, tach.angle(tach.max))
        assertTrue("the tachometer turns anticlockwise", tach.angle(1.0) < tach.angle(0.0))
        // Both over the bottom: the evenly numbered tachometer passes 6 o'clock a ninth of the way, 30 of 270 degrees.
        assertAt(6.0, tach.angle(tach.max / 9))
    }

    // Each gap's middle: below the hub (German), to the lower right (Italian), or facing the other dial (British).
    @Test
    fun `the gaps lie where the scales leave the dial free`() {
        assertAt(6.0, Math.toRadians(GaugeDesign.GERMAN.tachometer.gapMiddleDegrees))
        assertAt(4.5, Math.toRadians(GaugeDesign.ITALIAN.tachometer.gapMiddleDegrees))
        assertAt(4.5, Math.toRadians(GaugeDesign.ITALIAN.speedometer.gapMiddleDegrees))
        assertAt(3.5, Math.toRadians(GaugeDesign.BRITISH.speedometer.gapMiddleDegrees))
        assertAt(8.5, Math.toRadians(GaugeDesign.BRITISH.tachometer.gapMiddleDegrees))
    }

    // An inset in a gap: clear of the hub, inside the face, and within the gap, short of the scale's end numbers.
    @Test
    fun `insets fit their gaps`() {
        for (design in GaugeDesign.entries) for (sweep in listOf(design.tachometer, design.speedometer)) {
            for ((width, height) in listOf(0.5f to 0.3f, 0.38f to 0.34f)) {
                val place = insetPlace(sweep, width, height)
                assertTrue("$design $sweep scale ${place.scale}", place.scale > 0.5f && place.scale <= 1f)
                val gap = Math.toRadians(sweep.gapMiddleDegrees)
                val half = Math.toRadians(sweep.gapDegrees / 2)
                for (sx in listOf(-1, 1)) for (sy in listOf(-1, 1)) {
                    val x = place.x + sx * width * place.scale / 2
                    val y = place.y + sy * height * place.scale / 2
                    val r = hypot(x, y)
                    assertTrue("$design corner at $r clears the hub", r >= GaugeStyle.HUB + 0.02f)
                    assertTrue("$design corner at $r is inside the face", r <= 0.95f)
                    val off = Math.abs(Math.IEEEremainder(atan2(y.toDouble(), x.toDouble()) - gap, 2 * Math.PI))
                    assertTrue("$design corner within the gap", off < half)
                }
            }
        }
    }

    // British, the coordinates sit on 3 o'clock, from inside the hub out past the well's rim.
    @Test
    fun `the British coordinates reach from the hub out past the well`() {
        assertTrue(BRITISH_POSITION_FROM < GaugeStyle.HUB)
        assertTrue(BRITISH_POSITION_TO > 1 / GaugeStyle.FACE)
    }

    // The red zone's dashed start has as many dashes whichever way its scale runs, each as long.
    @Test
    fun `the red zone's dashes do not depend on the scale's way`() {
        val clockwise = redZoneDashes(1.0, 1.3, 100f, 3f)
        val anticlockwise = redZoneDashes(1.3, 1.0, 100f, 3f)
        assertTrue(clockwise.size > 2)
        assertEquals(clockwise.size, anticlockwise.size)
        assertEquals(clockwise.first().second - clockwise.first().first, anticlockwise.first().first - anticlockwise.first().second, 1e-9)
    }

    // On the side of the dial its gap is: the right-hand tachometer's at 9 o'clock, a gap to the right at 3.
    @Test
    fun `the British coordinates sit on their gap's side`() {
        assertEquals(-1f, gapSide(GaugeDesign.BRITISH.tachometer), 0f)
        assertEquals(1f, gapSide(GaugeDesign.BRITISH.speedometer), 0f)
    }

    @Test
    fun `the style is German unless set, and applies at once`() {
        assertEquals(GaugeDesign.GERMAN, configWith().gps.design)
        assertEquals(GaugeDesign.ITALIAN, configWith("gps.gauges.style=italian").gps.design)
        assertEquals(GaugeDesign.BRITISH, configWith("gps.gauges.style=British").gps.design)
        assertTrue(EditableSettings.validate(mapOf("gps.gauges.style" to "british"), emptyMap()).isEmpty())
        assertTrue("gps.gauges.style" in EditableSettings.validate(mapOf("gps.gauges.style" to "french"), emptyMap()))
        assertEquals(Applies.NOW, EditableSettings.applies("gps.gauges.style"))
    }

    // Drive mode's gauges take the style's scales and say which style they are, from the next frame.
    @Test
    fun `the gauges follow the style saved`() {
        val driving = Driving(GpsSettings(true), gamepad = { null }, nanoTime = { 0L })
        driving.driveMode(true)
        driving.pass()
        driving.saved(driving.settings.copy(design = GaugeDesign.BRITISH), setOf("gps.gauges.style"))
        val view = driving.gauges()!!
        assertEquals(GaugeDesign.BRITISH, view.design)
        assertAt(5.0, view.speedometer.angle(0.0))
        assertAt(7.0, view.tachometer.angle(0.0))
    }
}
