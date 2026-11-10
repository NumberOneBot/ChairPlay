package io.github.numberonebot.chairplay.config

import io.github.numberonebot.chairplay.drive.CarSpec
import io.github.numberonebot.chairplay.drive.Coordinates
import io.github.numberonebot.chairplay.drive.GaugeDesign
import io.github.numberonebot.chairplay.drive.GaugeSize
import io.github.numberonebot.chairplay.drive.GpsSettings
import io.github.numberonebot.chairplay.drive.Handling
import io.github.numberonebot.chairplay.drive.NmeaDialect
import io.github.numberonebot.chairplay.drive.NmeaFraming
import io.github.numberonebot.chairplay.drive.WheelMode
import io.github.numberonebot.chairplay.drive.SpeedUnits
import java.util.Properties

/** gps.*: the spoofed GPS and the car that drives it; the iPhone learns of the GPS when it connects. */
internal fun Properties.gpsSettings() = GpsSettings(
    enabled = booleanSetting("gps.enabled", false),
    wheelSpeed = booleanSetting("gps.wheel.speed", true),
    start = getProperty("gps.start")?.trim()?.ifEmpty { null }?.let { text ->
        requireNotNull(Coordinates.parse(text)) { "gps.start must be a latitude and longitude or a map link, not $text" }
    },
    headingDegrees = getProperty("gps.heading")?.trim()?.ifEmpty { null }?.let { text ->
        requireNotNull(GpsSettings.heading(text)) { "gps.heading must be a number of degrees from 0 to 359, not $text" }
    } ?: 0.0,
    sendHeading = booleanSetting("gps.heading.send", true),
    car = CarSpec(
        massKg = decimalSetting("gps.car.mass.kg", CarSpec().massKg, GpsSettings.MASS_KG),
        powerHp = decimalSetting("gps.car.power.hp", CarSpec().powerHp, GpsSettings.POWER_HP),
        handling = choiceSetting("gps.car.handling", Handling.NORMAL, Handling.entries.associateBy { it.key }),
    ),
    rate = wholeSetting("gps.rate", 4, GpsSettings.RATE),
    nmea = choiceSetting("gps.nmea", NmeaDialect.CLASSIC, NmeaDialect.entries.associateBy { it.key }),
    framing = choiceSetting("gps.framing", NmeaFraming.PARAMETERS, NmeaFraming.entries.associateBy { it.key }),
    gauges = choiceSetting("gps.gauges", GaugeSize.LARGE, GaugeSize.entries.associateBy { it.key }),
    design = choiceSetting("gps.gauges.style", GaugeDesign.GERMAN, GaugeDesign.entries.associateBy { it.key }),
    wheel = choiceSetting("gps.wheel", WheelMode.STEERING, WheelMode.entries.associateBy { it.key }),
    units = choiceSetting("gps.units", SpeedUnits.METRIC, SpeedUnits.entries.associateBy { it.key }),
    external = booleanSetting("gps.external", false),
)
