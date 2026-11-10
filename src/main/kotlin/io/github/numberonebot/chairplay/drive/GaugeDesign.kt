package io.github.numberonebot.chairplay.drive

import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.sign

/**
 * gps.gauges.style: how the dials are graduated, each dial's [Sweep], the needles' colour as 0xRRGGBB and the fill of
 * the scale up to them as 0xAARRGGBB (null: the accent, and the accent faint). German, as Audi's: both clockwise over
 * the top from the lower left, the coordinates and the wheel under the hubs. Italian: both clockwise from 6 o'clock to
 * 3, the coordinates in a corner cut from the tachometer to the lower right and the pedals and wheel on an arc across
 * the speedometer's gap, yellow needles over an orange-red fill. British, as Aston Martin's: the
 * speedometer on the left, clockwise from 5 o'clock to 1, and the tachometer on the right, anticlockwise from 7 to 11,
 * the insets on screens facing each other as Aston Martin's displays (the coordinates on 9 o'clock, the wheel and
 * pedals on 3), racing green needles, today's teal, over a fill of a lighter shade.
 */
enum class GaugeDesign(
    val key: String, val tachometer: Sweep, val speedometer: Sweep, val needle: Int?, val fill: Int?,
    /** The red zone's colour, 0xAARRGGBB; null for the gauges' red. */
    val redZone: Int? = null,
    /** The size of the hubs' readings, in the face's radius. */
    val reading: Float = 0.40f,
    /** The scales' numbers' font: italic, as Audi's, unless given. */
    val numbersFont: String = GaugeStyle.ITALIC,
    /** Where the GPS car's own engine goes red, in rpm, while no game gives its own; null for the car's. */
    val redlineRpm: Double? = null,
) {
    GERMAN("german", Sweep.GERMAN, Sweep.GERMAN, null, null),
    // Ferrari's Giallo Modena, its shield's yellow (an on-screen match of the paint).
    // The numbers bold and upright; the car revving to 8500, its tachometer up to 9.
    ITALIAN("italian", Sweep(90.0, 270.0), Sweep(90.0, 270.0), 0xF9C80E, 0xB3FF7A3D.toInt(),
        numbersFont = GaugeStyle.BOLD, redlineRpm = 8500.0,
    ),
    // Aston Martin's racing green as its cars and team wear it today, a green teal, lit to read on the dark face; the
    // fill the same lightened towards white.
    // The red zone, its ticks and numbers a metallic grey; the readings smaller, clear of the screens either side; the
    // numbers bold and upright.
    BRITISH("british", Sweep(120.0, -240.0), Sweep(60.0, 240.0), 0x16A27E, 0xB396D5C5.toInt(), 0xD97A8087.toInt(), 0.34f,
        numbersFont = GaugeStyle.BOLD,
    );

    /** Whether the tachometer is the right-hand dial, as Aston Martin's, and the speedometer the left. */
    val tachometerOnRight: Boolean get() = this == BRITISH
}

/**
 * Where a dial's scale starts, in degrees as NanoVG turns (0 at 3 o'clock, clockwise, y down), and how far it runs:
 * [degrees] below zero runs anticlockwise.
 */
data class Sweep(val startDegrees: Double, val degrees: Double) {
    /** The part of the dial the scale leaves free, in degrees. */
    val gapDegrees: Double get() = 360 - abs(degrees)

    /** The middle of that gap: from the scale's end, half the gap on the way the scale runs. */
    val gapMiddleDegrees: Double get() = startDegrees + degrees + sign(degrees) * gapDegrees / 2

    companion object {
        val GERMAN = Sweep(GaugeScale.START_DEGREES, GaugeScale.SWEEP_DEGREES)
    }
}

/** An inset's middle, in the face's radius off the dial's centre, and the size it is drawn at. */
data class InsetPlace(val x: Float, val y: Float, val scale: Float)

/**
 * The British coordinates' box on 3 o'clock, from inside the hub ([BRITISH_POSITION_FROM]) out past the well's rim
 * ([BRITISH_POSITION_TO]), which cuts it; in the face's radius.
 */
const val BRITISH_POSITION_FROM = 0.35f
const val BRITISH_POSITION_TO = 1.15f

/** Which side of the dial [sweep]'s gap is on: 1 the right (3 o'clock), -1 the left (9 o'clock). */
fun gapSide(sweep: Sweep): Float = if (cos(Math.toRadians(sweep.gapMiddleDegrees)) >= 0) 1f else -1f
