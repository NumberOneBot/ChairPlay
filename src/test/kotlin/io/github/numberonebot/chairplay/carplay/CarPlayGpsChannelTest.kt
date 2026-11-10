package io.github.numberonebot.chairplay.carplay

import io.github.numberonebot.chairplay.drive.Driving
import io.github.numberonebot.chairplay.drive.GeoPoint
import io.github.numberonebot.chairplay.drive.GpsSettings
import io.github.numberonebot.chairplay.gps.ChannelState
import io.github.numberonebot.chairplay.gps.GpsMethod
import io.github.numberonebot.chairplay.gps.GpsReason
import io.github.numberonebot.chairplay.platform.LocationResult
import io.github.numberonebot.chairplay.session.desktopIdentification
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** CarPlay's GPS as one of the GPS output's channels (A4): it sends only while the coordinator lets it. */
class CarPlayGpsChannelTest {
    private val driving = Driving(GpsSettings(enabled = true, start = GeoPoint(50.45, 30.52)), locate = { LocationResult.Unknown })
    private var session = false
    private val channel = CarPlayGpsChannel({ session }, { driving.location })

    // V04, A4.2: the iPhone hears of the GPS only with it on and CarPlay chosen.
    @Test
    fun `a connection identifies with the GPS only for CarPlay's method`() {
        assertTrue(desktopIdentification("10:5A:95:75:B2:AA", gps = GpsSettings(enabled = true)).locationInformationEnabled)
        val usb = desktopIdentification("10:5A:95:75:B2:AA", gps = GpsSettings(enabled = true, method = GpsMethod.USB))
        assertFalse(usb.locationInformationEnabled)
        assertFalse(usb.vehicleSpeedEnabled)
    }

    @Test
    fun `its state says whether a session is there and identified with the GPS`() {
        assertEquals(ChannelState(GpsReason.CARPLAY_NOT_CONNECTED), channel.state())
        session = true
        channel.identified(false)
        assertEquals(ChannelState(GpsReason.CARPLAY_RECONNECT_NEEDED, sessionGps = false), channel.state())
        channel.identified(true)
        assertEquals(ChannelState(null, sessionGps = true), channel.state())
    }

    // One writer: closed, the location answers nothing; opened, it sends; closed again, nothing more goes.
    @Test
    fun `the location sends only while the channel is started`() {
        session = true
        channel.identified(true)
        val location = CarPlayLocation(driving, {}, gate = channel.gate)

        assertNull(location.latestNmea())
        assertNull(channel.start(GeoPoint(50.45, 30.52), null))
        assertTrue(channel.running)
        assertNotNull(location.latestNmea())
        channel.quiesce()
        assertFalse(channel.running)
        assertNull(location.latestNmea())
    }
}
