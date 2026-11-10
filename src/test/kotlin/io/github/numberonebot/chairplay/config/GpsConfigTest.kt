package io.github.numberonebot.chairplay.config

import io.github.numberonebot.chairplay.drive.CarSpec
import io.github.numberonebot.chairplay.drive.GaugeSize
import io.github.numberonebot.chairplay.drive.Handling
import io.github.numberonebot.chairplay.drive.GeoPoint
import io.github.numberonebot.chairplay.drive.GpsSettings
import io.github.numberonebot.chairplay.drive.NmeaDialect
import io.github.numberonebot.chairplay.drive.NmeaFraming
import io.github.numberonebot.chairplay.drive.WheelMode
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

/** DesktopConfig: the spoofed GPS and its car. */
class GpsConfigTest {
    // The spoofed GPS: off until asked for; then the iPhone gets the car's wheel speed too.
    @Test
    fun `the spoofed GPS is off unless asked for, with an ordinary car`() {
        val off = configWith().gps
        assertFalse(off.enabled)
        assertTrue(off.wheelSpeed)
        assertTrue(off.sendHeading)
        assertNull(off.start)
        assertEquals(0.0, off.headingDegrees, 0.0)
        assertEquals(CarSpec(), off.car)
        // The experiments: the rate the iPhone asks for, and DiPlay's sentences.
        assertEquals(4, off.rate)
        assertEquals(NmeaDialect.CLASSIC, off.nmea)
        // Any whole number of fixes a second from 1 to 20.
        for (rate in listOf(1, 7, 10, 20)) assertEquals(rate, configWith("gps.rate=$rate").gps.rate)
        assertEquals(NmeaDialect.MODERN, configWith("gps.nmea=modern").gps.nmea)
        assertEquals(NmeaFraming.PARAMETERS, off.framing)
        assertEquals(NmeaFraming.JOINED, configWith("gps.framing=joined").gps.framing)

        val on = configWith(
            "gps.enabled=true", "gps.wheel.speed=false", "gps.heading.send=false",
            "gps.start=https://maps.apple.com/?ll=50.4501,30.5234",
            "gps.heading=270.5", "gps.car.power.hp=300", "gps.car.mass.kg=1600",
        ).gps
        assertEquals(
            GpsSettings(true, wheelSpeed = false, start = GeoPoint(50.4501, 30.5234), headingDegrees = 270.5,
                sendHeading = false, car = CarSpec(massKg = 1600.0, powerHp = 300.0)),
            on,
        )
    }

    // The car's handling, the gauges' size and the wheel's mode, as the GPS page sets them: normal, large and the
    // steering input unless set.
    @Test
    fun `the car's handling, the gauges' size and the wheel's mode`() {
        val plain = configWith().gps
        assertEquals(Handling.NORMAL, plain.car.handling)
        assertEquals(GaugeSize.LARGE, plain.gauges)
        assertEquals(WheelMode.STEERING, plain.wheel)
        assertEquals(WheelMode.HEADING, configWith("gps.wheel=heading").gps.wheel)
        val set = configWith("gps.car.handling=sharp", "gps.gauges=small", "gps.wheel=steering").gps
        assertEquals(Handling.SHARP, set.car.handling)
        assertEquals(GaugeSize.SMALL, set.gauges)
        assertEquals(WheelMode.STEERING, set.wheel)
    }

    @Test
    fun `a game drives the car only when asked`() {
        assertFalse(configWith().gps.external)
        assertTrue(configWith("gps.external=true").gps.external)
    }

    @Test
    fun `rejects GPS values the car cannot have`() {
        for (bad in listOf(
            "gps.start=Kyiv", "gps.heading=north", "gps.heading=360", "gps.car.power.hp=5", "gps.car.mass.kg=50",
            "gps.rate=0", "gps.rate=21", "gps.rate=2.5", "gps.rate=fast",
        )) {
            val key = bad.substringBefore('=')
            val error = assertThrows(IllegalArgumentException::class.java) { configWith(bad).gps }
            assertTrue(error.message, key in error.message.orEmpty())
        }
    }

    // A choice the app does not know reads as the default.
    @Test
    fun `unknown GPS choices read as their defaults`() {
        assertEquals(
            configWith().gps,
            configWith("gps.nmea=ancient", "gps.framing=pigeons", "gps.car.handling=wild", "gps.gauges=huge", "gps.wheel=sideways").gps,
        )
    }
}
