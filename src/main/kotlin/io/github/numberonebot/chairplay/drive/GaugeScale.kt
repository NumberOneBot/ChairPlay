package io.github.numberonebot.chairplay.drive

import kotlin.math.ceil

/**
 * A round dial's scale, as Audi's: 280 degrees from the lower left, clockwise over the top to the lower right, its
 * [numbers] evenly apart with a tick halfway between each two and fine ones at the tenths, and a red zone from [redFrom],
 * dashed from [warnFrom]. Numbers that do not step evenly change the scale between them, as a fast car's speedometer
 * does past 140.
 */
data class GaugeScale(val numbers: List<Double>, val redFrom: Double? = null, val warnFrom: Double? = null) {
    /** A numbered tick, the one halfway between two numbers, or one of the fine tenths between them. */
    enum class TickSize { MAJOR, MIDDLE, FINE }

    data class Tick(val value: Double, val size: TickSize) {
        val major: Boolean get() = size == TickSize.MAJOR
    }

    val max: Double get() = numbers.last()

    /** Where the needle points for [value], in radians as NanoVG turns (0 to the right, clockwise, y down). */
    fun angle(value: Double): Double {
        val v = value.coerceIn(numbers.first(), max)
        val i = numbers.indices.last { it == 0 || numbers[it] <= v }.coerceAtMost(numbers.size - 2)
        val along = i + (v - numbers[i]) / (numbers[i + 1] - numbers[i])
        return Math.toRadians(START_DEGREES + SWEEP_DEGREES * along / (numbers.size - 1))
    }

    fun ticks(): List<Tick> = numbers.zipWithNext { a, b ->
        (0 until 10).map { tenth ->
            val size = when (tenth) {
                0 -> TickSize.MAJOR
                5 -> TickSize.MIDDLE
                else -> TickSize.FINE
            }
            Tick(a + (b - a) * tenth / 10, size)
        }
    }.flatten() + Tick(max, TickSize.MAJOR)

    companion object {
        const val START_DEGREES = 130.0
        const val SWEEP_DEGREES = 280.0

        /** Dense at low speeds: km/h by 20 to 140, then 40 to 300; mph by 10 to 60, then 20 to 180. */
        fun speedometer(units: SpeedUnits = SpeedUnits.METRIC): GaugeScale = GaugeScale(
            if (units == SpeedUnits.IMPERIAL) listOf(0.0, 10.0, 20.0, 30.0, 40.0, 50.0, 60.0, 80.0, 100.0, 120.0, 140.0, 160.0, 180.0)
            else listOf(0.0, 20.0, 40.0, 60.0, 80.0, 100.0, 120.0, 140.0, 180.0, 220.0, 260.0, 300.0),
        )

        fun tachometer(spec: CarSpec): GaugeScale = tachometer(spec.redlineRpm)

        /**
         * The tachometer of an engine red from [redlineRpm], the GPS car's or a game's: thousands of rpm, half a thousand
         * past the red line and up to the next whole, the red zone from the red line, dashed half a thousand before it.
         */
        fun tachometer(redlineRpm: Double): GaugeScale {
            val redline = redlineRpm / 1000
            return GaugeScale((0..ceil(redline + 0.5).toInt()).map { it.toDouble() }, redFrom = redline, warnFrom = redline - 0.5)
        }
    }
}

/** What the gauges show at a moment: the speed, the engine, the gear, the pedals, the heading, the place, and the scales. */
data class GaugeView(
    val speedKmh: Double,
    val rpm: Double,
    val gear: String,
    val throttle: Double,
    val brake: Double,
    /** Where the car faces: 0 north, 90 east. */
    val headingDegrees: Double,
    val speedometer: GaugeScale,
    val tachometer: GaugeScale,
    val position: GeoPoint = GeoPoint(0.0, 0.0),
    val size: GaugeSize = GaugeSize.LARGE,
    /** The steering input now, -1 full left to 1 full right, for the wheel in steering mode. */
    val steer: Double = 0.0,
    val wheelMode: WheelMode = WheelMode.STEERING,
    val units: SpeedUnits = SpeedUnits.METRIC,
) {
    val speed: Double get() = units.fromKmh(speedKmh)
}

/** Full steering lock, how far the wheel turns at [CarInput.steer] of 1: enough to read as a hard turn. */
private val STEER_LOCK = Math.toRadians(115.0)

/**
 * How far the gauges' steering wheel is turned, in radians (NanoVG: 0 at rest pointing up, positive clockwise). In
 * [WheelMode.HEADING] it points along the car's [headingDegrees] (north up); in [WheelMode.STEERING] it turns with the
 * [steer] input (-1 left to 1 right) like a real wheel, centred going straight, whatever the heading.
 */
fun wheelTurn(mode: WheelMode, headingDegrees: Double, steer: Double): Double = when (mode) {
    WheelMode.HEADING -> Math.toRadians(headingDegrees)
    WheelMode.STEERING -> steer.coerceIn(-1.0, 1.0) * STEER_LOCK
}
