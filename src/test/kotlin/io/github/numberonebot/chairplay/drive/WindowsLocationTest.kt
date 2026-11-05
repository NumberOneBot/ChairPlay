package io.github.numberonebot.chairplay.drive

import org.junit.Assert.assertEquals
import org.junit.Test

class WindowsLocationTest {
    private val paris = 48.85837 to 2.294481

    @Test
    fun `a place Windows found is read`() {
        assertEquals(
            WindowsLocation.Found(GeoPoint(48.85837, 2.294481)),
            WindowsLocation.result(LocationApi.REPORT_RUNNING, paris),
        )
    }

    // Location turned off, or desktop apps not let use it.
    @Test
    fun `a refusal is told apart from no fix`() {
        assertEquals(WindowsLocation.Denied, WindowsLocation.result(LocationApi.REPORT_ACCESS_DENIED, null))
        assertEquals(WindowsLocation.Unknown, WindowsLocation.result(LocationApi.REPORT_INITIALIZING, null))
    }

    // Anything else (no sensor, an error, a place off the globe) is no place.
    @Test
    fun `anything else is no fix`() {
        assertEquals(WindowsLocation.Unknown, WindowsLocation.result(LocationApi.REPORT_NOT_SUPPORTED, null))
        assertEquals(WindowsLocation.Unknown, WindowsLocation.result(LocationApi.REPORT_ERROR, null))
        assertEquals(WindowsLocation.Unknown, WindowsLocation.result(LocationApi.REPORT_RUNNING, 95.0 to 10.0))
        assertEquals(WindowsLocation.Unknown, WindowsLocation.result(LocationApi.REPORT_RUNNING, null))
    }
}
