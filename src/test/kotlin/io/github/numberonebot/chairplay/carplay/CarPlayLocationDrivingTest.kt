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
        val location = CarPlayLocation(driving, {})
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
        CarPlayLocation(driving, {})
        val location = CarPlayLocation(driving, {})
        location.onRequested(setOf(1, 2, 4))
        location.start()
        location.latestNmea()
        val state = driving.state()
        assertEquals(true, state["listening"])
        assertEquals(listOf("GGA", "RMC", "PASCD"), state["sentences"])
        assertEquals(1L, state["sent"])
    }

    // The GPS page hears of the car on the status tick alone, and only when what it shows changed: the fixes sent at
    // gps.rate tell it nothing.
    @Test
    fun `the GPS page is told on the status tick, only of a change`() {
        val driving = driving(GpsSettings(true, start = kyiv, rate = 10))
        var send: (() -> Unit)? = null
        val location = CarPlayLocation(driving, {}, schedule = { _, action -> send = action; AutoCloseable {} })
        location.attach {}
        location.onRequested(setOf(1))
        location.start()
        var told = 0
        driving.showPage { told++ }
        assertEquals(1, told)

        repeat(10) { send!!() }
        assertEquals(1, told)
        driving.showPage { told++ }
        assertEquals(2, told)
        driving.showPage { told++ }
        assertEquals(2, told)
    }
}
