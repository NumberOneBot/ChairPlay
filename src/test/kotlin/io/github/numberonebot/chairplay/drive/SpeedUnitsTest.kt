package io.github.numberonebot.chairplay.drive

import io.github.numberonebot.chairplay.config.configWith
import io.github.numberonebot.chairplay.config.Applies
import io.github.numberonebot.chairplay.config.EditableSettings
import org.junit.Assert.*
import org.junit.Test

class SpeedUnitsTest {
    @Test fun `speed units default to metric and accept imperial`() {
        assertEquals("metric", configWith().gps.units.key)
        assertEquals("imperial", configWith("gps.units=imperial").gps.units.key)
        assertEquals("metric", configWith("gps.units=unknown").gps.units.key)
        assertTrue(EditableSettings.validate(mapOf("gps.units" to "imperial"), emptyMap()).isEmpty())
        assertTrue(EditableSettings.validate(mapOf("gps.units" to "unknown"), emptyMap()).containsKey("gps.units"))
        assertEquals(Applies.NOW, EditableSettings.applies("gps.units"))
    }

    @Test fun `imperial converts the reading and scale together without changing the car`() {
        val driving = Driving(GpsSettings(true), gamepad = { null }, nanoTime = { 0L })
        driving.driveMode(true)
        driving.pass()
        val metric = driving.gauges()!!.copy(speedKmh = 160.9344)
        assertEquals(160.9344, metric.speed, 1e-6)
        val before = driving.drive.snapshot()
        driving.saved(driving.settings.copy(units = SpeedUnits.IMPERIAL), setOf("gps.units"))
        val imperial = driving.gauges()!!.copy(speedKmh = 160.9344)
        assertEquals(100.0, imperial.speed, 1e-6)
        // The car runs to the end of the speedometer in its units: 180 mph, not 300 km/h.
        assertEquals(imperial.speedometer.max, SpeedUnits.IMPERIAL.fromKmh(driving.drive.spec.limiterKmh), 1e-9)
        assertEquals(imperial.speedometer.angle(100.0), imperial.speedometer.angle(imperial.speed), 1e-6)
        assertEquals(before, driving.drive.snapshot())
    }
}
