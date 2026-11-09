package io.github.numberonebot.chairplay.platform.windows

import io.github.numberonebot.chairplay.drive.GeoPoint
import io.github.numberonebot.chairplay.platform.LocationResult
import org.junit.Assert.assertEquals
import org.junit.Test

class WindowsLocationTest {
    private val paris = 48.85837 to 2.294481

    @Test
    fun `a place Windows found is read`() {
        assertEquals(
            LocationResult.Found(GeoPoint(48.85837, 2.294481)),
            WindowsLocation.result(LocationApi.REPORT_RUNNING, paris),
        )
    }

    // Location turned off, or desktop apps not let use it.
    @Test
    fun `a refusal is told apart from no fix`() {
        assertEquals(LocationResult.Denied, WindowsLocation.result(LocationApi.REPORT_ACCESS_DENIED, null))
        assertEquals(LocationResult.Unknown, WindowsLocation.result(LocationApi.REPORT_INITIALIZING, null))
    }

    // Anything else (no sensor, an error, a place off the globe) is no place.
    @Test
    fun `anything else is no fix`() {
        assertEquals(LocationResult.Unknown, WindowsLocation.result(LocationApi.REPORT_NOT_SUPPORTED, null))
        assertEquals(LocationResult.Unknown, WindowsLocation.result(LocationApi.REPORT_ERROR, null))
        assertEquals(LocationResult.Unknown, WindowsLocation.result(LocationApi.REPORT_RUNNING, 95.0 to 10.0))
        assertEquals(LocationResult.Unknown, WindowsLocation.result(LocationApi.REPORT_RUNNING, null))
    }
}
