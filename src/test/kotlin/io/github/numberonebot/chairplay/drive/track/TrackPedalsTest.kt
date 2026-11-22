package io.github.numberonebot.chairplay.drive.track

import io.github.numberonebot.chairplay.drive.GeoPoint
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** A recording's pedals: its own where it has them, else from how fast its speed changes. */
class TrackPedalsTest {
    private fun recording(vararg points: TrackPoint) = TrackRecording("t", listOf(points.toList()))

    /** A point [seconds] in, [speed] m/s, the car a little further north each second. */
    private fun point(seconds: Double, speed: Double, throttle: Double? = null, brake: Double? = null) =
        TrackPoint(seconds, GeoPoint(50.0 + seconds * 1e-4, 6.0), speed = speed, throttle = throttle, brake = brake)

    @Test
    fun `recorded pedals are played between their points`() {
        val track = recording(point(0.0, 30.0, 1.0, 0.0), point(2.0, 30.0, 0.0, 1.0))
        assertEquals(TrackPedals(0.5, 0.5), track.pedals(1.0))
    }

    @Test
    fun `without recorded pedals a gaining car has the throttle and a slowing one the brake`() {
        val gaining = recording(point(0.0, 10.0), point(10.0, 40.0))
        val slowing = recording(point(0.0, 60.0), point(10.0, 0.0))
        val steady = recording(point(0.0, 30.0), point(10.0, 30.0))

        with(gaining.pedals(5.0)) { assertTrue("$this", throttle > 0.5 && brake == 0.0) }
        with(slowing.pedals(5.0)) { assertTrue("$this", brake > 0.5 && throttle == 0.0) }
        assertEquals(TrackPedals(0.0, 0.0), steady.pedals(5.0))
    }

    @Test
    fun `a gentle slowing as off the throttle is no brake`() {
        val coasting = recording(point(0.0, 30.0), point(10.0, 22.0))
        assertEquals(TrackPedals(0.0, 0.0), coasting.pedals(5.0))
    }
}
