package io.github.numberonebot.chairplay.drive

import io.github.numberonebot.chairplay.gps.GpsMethod

/**
 * gps.*: whether the iPhone navigates by the spoofed car, and what it is told besides its place ([wheelSpeed] for
 * $PASCD); where the car is ([start], null until set; the car saves it as it stops) and which
 * way it faces ([headingDegrees], sent as the course when [sendHeading]); the car itself; and the experiments in how
 * the fixes go ([rate], [nmea], [framing]).
 */
data class GpsSettings(
    val enabled: Boolean,
    val wheelSpeed: Boolean = true,
    val start: GeoPoint? = null,
    val headingDegrees: Double = 0.0,
    /** gps.heading.send: whether the car's heading goes as the course in the position sentences. */
    val sendHeading: Boolean = true,
    val car: CarSpec = CarSpec(),
    /** Experiments: fixes a second on the tunnel (the iPhone asks for 4), and which sentences carry them. */
    val rate: Int = 4,
    val nmea: NmeaDialect = NmeaDialect.CLASSIC,
    val framing: NmeaFraming = NmeaFraming.PARAMETERS,
    /** How large drive mode's gauges show, gps.gauges. */
    val gauges: GaugeSize = GaugeSize.LARGE,
    /** How the gauges are graduated and their needles coloured, gps.gauges.style. */
    val design: GaugeDesign = GaugeDesign.GERMAN,
    /** What the gauges' steering wheel shows, gps.wheel: the car's heading, or its steering input. */
    val wheel: WheelMode = WheelMode.STEERING,
    /** gps.units: the speedometer scale and speed readouts; applied immediately. */
    val units: SpeedUnits = SpeedUnits.METRIC,
    /** gps.external: whether a game's telemetry drives the car while it sends, instead of the gamepad and keys. */
    val external: Boolean = false,
    /** gps.method: the channel to the iPhone, CarPlay's location messages or USB developer simulation. */
    val method: GpsMethod = GpsMethod.CARPLAY,
    /** gps.usb.device: the UDID of the iPhone USB setup prepared; null before one was. */
    val usbDevice: String? = null,
    /** gps.usb.rate: positions a second over USB, a hand edit only. */
    val usbRate: Int = 10,
) {
    /**
     * The car as it drives: [car], running to the end of the speedometer in its [units] and no further, and revving as
     * far as its [design] has it.
     */
    val driven: CarSpec get() = car.copy(
        limiterKmh = units.toKmh(GaugeScale.speedometer(units).max),
        redlineRpm = design.redlineRpm ?: car.redlineRpm,
    )

    companion object {
        /** Fixes a second the car's position may go at: the iPhone asks for 4, and 20 is one each 50 ms. */
        val RATE = 1..20
        val POWER_HP = 20.0..2000.0
        val MASS_KG = 300.0..10000.0

        /** A heading in degrees, from 0 up to but not 360. */
        fun heading(text: String): Double? = text.trim().toDoubleOrNull()?.takeIf { it >= 0.0 && it < 360.0 }
    }
}

/** How large drive mode's gauges show: as tall as the window, or half that, down in its corners. */
enum class GaugeSize(val key: String) {
    LARGE("large"),
    SMALL("small");

    fun other(): GaugeSize = if (this == LARGE) SMALL else LARGE
}

/**
 * What the gauges' steering wheel shows: the car's heading with north up ([HEADING], the red mark pointing where the car
 * faces, to match a north-up map like Apple Maps on the AltScreen), or the car's steering input with the car up
 * ([STEERING], the wheel turning with the input like a real wheel, to match a track-up map like Google Maps).
 */
enum class WheelMode(val key: String) {
    HEADING("heading"),
    STEERING("steering"),
}
