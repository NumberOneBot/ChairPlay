package io.github.numberonebot.chairplay.carplay

import io.github.numberonebot.chairplay.drive.Driving
import io.github.numberonebot.chairplay.drive.GeoPoint
import io.github.numberonebot.chairplay.drive.GpsSettings
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** CarPlay's location made from the app's car, as each connection makes it: the car's place, and the GPS page's link. */
class CarPlayLocationDrivingTest {
    private val kyiv = GeoPoint(50.4501, 30.5234)

    private fun driving(settings: GpsSettings) = Driving(settings, gamepad = { null }, nanoTime = { 0L }, lookup = {})

    // Without a start, the car is nowhere: the iPhone gets no position, only the wheel speed.
    @Test
    fun `a car with no start sends no position`() {
        val driving = driving(GpsSettings(true))
        val location = CarPlayLocation(driving, false, {})
        location.onRequested(setOf(1, 2))
        location.start()
        assertNull(location.latestNmea())

        driving.saved(GpsSettings(true, start = kyiv), setOf("gps.start"))
        assertTrue(location.latestNmea()!!.startsWith("\$GPGGA"))
    }

    // The GPS page: what the iPhone asked the connection's location for, and the updates it sent.
    @Test
    fun `the GPS page reads the location the last connection made`() {
        val driving = driving(GpsSettings(true, start = kyiv))
        CarPlayLocation(driving, false, {})
        val location = CarPlayLocation(driving, false, {})
        location.onRequested(setOf(1, 2, 4))
        location.start()
        location.latestNmea()
        val state = driving.state()
        assertEquals(true, state["listening"])
        assertEquals(listOf("GGA", "RMC", "PASCD"), state["sentences"])
        assertEquals(1L, state["sent"])
    }
}
