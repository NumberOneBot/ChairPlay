package io.github.numberonebot.chairplay.drive

/**
 * Display units: outgoing GPS measurements keep theirs. The car keeps its physics too, but runs to the end of the
 * speedometer in these units ([GpsSettings.driven]).
 */
enum class SpeedUnits(val key: String, val symbol: String) {
    METRIC("metric", "km/h"),
    IMPERIAL("imperial", "mph");

    fun fromKmh(speed: Double): Double = if (this == IMPERIAL) speed / 1.609344 else speed

    fun toKmh(speed: Double): Double = if (this == IMPERIAL) speed * 1.609344 else speed
}
