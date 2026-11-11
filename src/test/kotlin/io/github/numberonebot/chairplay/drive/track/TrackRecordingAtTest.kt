package io.github.numberonebot.chairplay.drive.track

import io.github.numberonebot.chairplay.drive.GeoPoint
import org.junit.Test
import org.junit.Assert.assertEquals

class TrackRecordingAtTest {
    private fun point(seconds: Double, lat: Double, lon: Double, speed: Double? = null, heading: Double? = null) =
        TrackPoint(seconds, GeoPoint(lat, lon), speed = speed, heading = heading)

    private fun one(vararg points: TrackPoint) = TrackRecording("t", listOf(points.toList()))

    @Test
    fun `the middle between two points is the middle position`() {
        val s = one(point(0.0, 10.0, 20.0), point(10.0, 10.002, 20.004)).at(5.0)
        assertEquals(10.001, s.position.latitude, 1e-9)
        assertEquals(20.002, s.position.longitude, 1e-9)
    }

    @Test
    fun `speed without recorded speeds is distance over time`() {
        val a = point(0.0, 10.0, 20.0)
        val b = point(10.0, 10.001, 20.0)
        val s = one(a, b).at(4.0)
        assertEquals(distanceMeters(a.position, b.position) / 10.0, s.speed, 1e-9)
    }

    @Test
    fun `speed with recorded speeds is interpolated`() {
        val s = one(point(0.0, 10.0, 20.0, speed = 10.0), point(10.0, 10.001, 20.0, speed = 20.0)).at(5.0)
        assertEquals(15.0, s.speed, 1e-9)
    }

    @Test
    fun `heading without recorded headings is the bearing of the pair`() {
        val a = point(0.0, 10.0, 20.0)
        val b = point(10.0, 10.0, 20.001)
        assertEquals(bearingDegrees(a.position, b.position), one(a, b).at(3.0).heading, 1e-9)
    }

    @Test
    fun `recorded headings are interpolated by the short arc`() {
        val s = one(point(0.0, 10.0, 20.0, heading = 350.0), point(10.0, 10.001, 20.0, heading = 10.0)).at(5.0)
        assertEquals(0.0, s.heading, 1e-6)
    }

    @Test
    fun `a stopped pair keeps the heading of the nearest earlier moving pair`() {
        val s = one(
            point(0.0, 10.0, 20.0),
            point(10.0, 10.0, 20.001),
            point(20.0, 10.0, 20.001),
            point(30.0, 10.001, 20.001),
        ).at(15.0)
        assertEquals(90.0, s.heading, 1e-3)
    }

    @Test
    fun `a stopped start takes the heading of the nearest later moving pair`() {
        val s = one(
            point(0.0, 10.0, 20.0),
            point(10.0, 10.0, 20.0),
            point(20.0, 10.0, 20.001),
        ).at(5.0)
        assertEquals(90.0, s.heading, 1e-3)
    }

    @Test
    fun `a track that never moves has heading zero`() {
        val s = one(point(0.0, 10.0, 20.0), point(10.0, 10.0, 20.0)).at(5.0)
        assertEquals(0.0, s.heading, 0.0)
    }

    @Test
    fun `a time in the gap between segments is the last point of the first segment, stopped`() {
        val last = point(10.0, 10.0, 20.001)
        val r = TrackRecording(
            "t",
            listOf(
                listOf(point(0.0, 10.0, 20.0), last),
                listOf(point(100.0, 11.0, 21.0), point(110.0, 11.001, 21.0)),
            ),
        )
        val s = r.at(50.0)
        assertEquals(last.position, s.position)
        assertEquals(0.0, s.speed, 0.0)
        assertEquals(90.0, s.heading, 1e-3)
    }

    @Test
    fun `times outside the recording are clamped`() {
        val r = one(point(0.0, 10.0, 20.0), point(10.0, 10.001, 20.0), point(20.0, 10.002, 20.0))
        assertEquals(GeoPoint(10.0, 20.0), r.at(-5.0).position)
        assertEquals(GeoPoint(10.002, 20.0), r.at(r.duration + 5).position)
    }
}
