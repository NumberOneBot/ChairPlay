package io.github.numberonebot.chairplay.drive.track

import io.github.numberonebot.chairplay.drive.GeoPoint
import org.junit.Assert.assertEquals
import org.junit.Test

class TrackGeoTest {
    @Test
    fun `distance along a parallel`() {
        assertEquals(71.5, distanceMeters(GeoPoint(50.0, 6.0), GeoPoint(50.0, 6.001)), 0.5)
    }

    @Test
    fun `bearing east and north`() {
        assertEquals(90.0, bearingDegrees(GeoPoint(50.0, 6.0), GeoPoint(50.0, 6.001)), 0.5)
        assertEquals(0.0, bearingDegrees(GeoPoint(50.0, 6.0), GeoPoint(50.001, 6.0)), 0.5)
    }

    @Test
    fun `bearing west and south stay in range`() {
        assertEquals(270.0, bearingDegrees(GeoPoint(50.0, 6.0), GeoPoint(50.0, 5.999)), 0.5)
        assertEquals(180.0, bearingDegrees(GeoPoint(50.0, 6.0), GeoPoint(49.999, 6.0)), 0.5)
    }
}
