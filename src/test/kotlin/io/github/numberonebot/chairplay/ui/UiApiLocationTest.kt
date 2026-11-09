package io.github.numberonebot.chairplay.ui

import io.github.numberonebot.chairplay.drive.GeoPoint
import io.github.numberonebot.chairplay.platform.LocationResult
import org.junit.Assert.assertEquals
import org.junit.Test

class UiApiLocationTest {
    @Test
    fun `location results give today's page fields`() {
        assertEquals(
            mapOf("latitude" to 48.85, "longitude" to 2.29),
            LocationResult.Found(GeoPoint(48.85, 2.29)).toPage(),
        )
        assertEquals(mapOf("error" to "denied"), LocationResult.Denied.toPage())
        assertEquals(mapOf("error" to "unknown"), LocationResult.Unknown.toPage())
    }
}
