package io.github.numberonebot.chairplay.drive

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** The Experiments page's gauges demo: the gauges, large, in the style set, over a made-up run of the car's engine. */
class GaugeDemoTest {
    private var now = 0L
    private val frame = 16_666_667L
    private val driving = Driving(
        GpsSettings(true, start = GeoPoint(50.45, 30.52), headingDegrees = 90.0, design = GaugeDesign.ITALIAN, gauges = GaugeSize.SMALL),
        gamepad = { null }, nanoTime = { now },
    )
    private val demo = GaugeDemo(driving, nanoTime = { now })

    /** [seconds] of the demo's frames. */
    private fun play(seconds: Double): List<GaugeView> = (0 until (seconds * 60).toInt()).map {
        now += frame
        demo.step()
        demo.view()!!
    }

    @Test
    fun `nothing shows until it plays, and nothing after`() {
        assertNull(demo.view())
        demo.begin()
        assertTrue(demo.view() != null)
        demo.end()
        assertNull(demo.view())
    }

    // Large whatever size is set, in the style set, at the GPS car's place and heading, which stays where it is.
    @Test
    fun `it shows the gauges large in the style set, the car staying put`() {
        val before = driving.drive.snapshot()
        demo.begin()
        val view = play(2.0).last()
        assertEquals(GaugeSize.LARGE, view.size)
        assertEquals(GaugeDesign.ITALIAN, view.design)
        assertEquals(GaugeDesign.ITALIAN.speedometer, view.speedometer.sweep)
        assertEquals(before.position, view.position)
        assertEquals(90.0, view.headingDegrees, 0.0)
        assertEquals(before, driving.drive.snapshot())
    }

    // Full throttle up the gears, then the brakes to a stop, a pause, and the same again.
    @Test
    fun `the run goes up on the throttle, down on the brakes, and again`() {
        demo.begin()
        val up = play(GaugeDemo.THROTTLE_SECONDS - 0.1)
        assertTrue(up.all { it.throttle == 1.0 && it.brake == 0.0 })
        assertTrue("fast by the end: ${up.last().speedKmh}", up.last().speedKmh > 100)
        assertTrue("through the gears", up.map { it.gear }.distinct().size >= 3)
        val down = play(12.0)
        assertTrue(down.any { it.brake == 1.0 })
        assertTrue(down.any { it.speedKmh == 0.0 })
        val stopped = down.indexOfFirst { it.speedKmh == 0.0 }
        assertTrue("and again", down.drop(stopped).any { it.throttle == 1.0 && it.speedKmh > 0 })
    }
}
