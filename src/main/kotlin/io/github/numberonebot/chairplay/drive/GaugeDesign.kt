package io.github.numberonebot.chairplay.drive

import kotlin.math.abs
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.hypot
import kotlin.math.sign
import kotlin.math.sin

/**
 * gps.gauges.style: how the dials are graduated, each dial's [Sweep], the needles' colour as 0xRRGGBB and the fill of
 * the scale up to them as 0xAARRGGBB (null: the accent, and the accent faint). German, as Audi's: both clockwise over
 * the top from the lower left, the coordinates and the wheel under the hubs. Italian: both clockwise from 6 o'clock to
 * 3, the insets in the gap to the lower right, yellow needles over an orange-red fill. British, as Aston Martin's: the
 * speedometer on the left, clockwise from 5 o'clock to 2, and the tachometer on the right, anticlockwise from 7 to 10,
 * the insets on screens facing each other as Aston Martin's displays (the coordinates on 9 o'clock, the wheel and
 * pedals on 3), racing green needles over a fill of a lighter green.
 */
enum class GaugeDesign(
    val key: String, val tachometer: Sweep, val speedometer: Sweep, val needle: Int?, val fill: Int?,
    /** The red zone's colour, 0xAARRGGBB; null for the gauges' red. */
    val redZone: Int? = null,
    /** The size of the hubs' readings, in the face's radius. */
    val reading: Float = 0.40f,
    /** The size of the hubs' units under their readings, in the face's radius. */
    val caption: Float = 0.114f,
) {
    GERMAN("german", Sweep.GERMAN, Sweep.GERMAN, null, null),
    // Ferrari's Giallo Modena, its shield's yellow (an on-screen match of the paint).
    ITALIAN("italian", Sweep(90.0, 270.0), Sweep(90.0, 270.0), 0xF9C80E, 0xB3FF7A3D.toInt()),
    // British racing green, lightened to read on the dark face.
    // The red zone a muted burgundy-ruby; the readings and their units smaller, clear of the screens either side.
    BRITISH("british", Sweep(120.0, -270.0), Sweep(60.0, 270.0), 0x2FA05A, 0xB3A6DCB8.toInt(), 0xD99B1B30.toInt(), 0.34f, 0.088f);

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

/** An inset's middle in a dial's gap, in the face's radius off the dial's centre, and the size it is drawn at. */
data class InsetPlace(val x: Float, val y: Float, val scale: Float)

/**
 * The British coordinates' box on 3 o'clock, from inside the hub ([BRITISH_POSITION_FROM]) out past the well's rim
 * ([BRITISH_POSITION_TO]), which cuts it; in the face's radius.
 */
const val BRITISH_POSITION_FROM = 0.35f
const val BRITISH_POSITION_TO = 1.15f

/** Which side of the dial [sweep]'s gap is on: 1 the right (3 o'clock), -1 the left (9 o'clock). */
fun gapSide(sweep: Sweep): Float = if (cos(Math.toRadians(sweep.gapMiddleDegrees)) >= 0) 1f else -1f

/** The hub's edge and the face's, in the face's radius, which an inset keeps clear of; and its gap from the scale's ends. */
private const val INSET_INNER = GaugeStyle.HUB + 0.03f
private const val INSET_OUTER = 0.94f
private const val INSET_CLEAR_DEGREES = 14.0

/**
 * Where an inset [width] x [height] (in the face's radius, at full size) sits in [sweep]'s gap: on the gap's middle
 * line, as large as fits up to full size, its corners clear of the hub, inside the face, and short of the scale's end
 * numbers; at the middle of the room it has there.
 */
fun insetPlace(sweep: Sweep, width: Float, height: Float): InsetPlace {
    val middle = Math.toRadians(sweep.gapMiddleDegrees)
    val half = Math.toRadians(sweep.gapDegrees / 2 - INSET_CLEAR_DEGREES)
    val dx = cos(middle).toFloat()
    val dy = sin(middle).toFloat()
    fun fits(radius: Float, scale: Float): Boolean {
        val x = dx * radius
        val y = dy * radius
        return listOf(-1, 1).all { sx ->
            listOf(-1, 1).all { sy ->
                val cx = x + sx * width * scale / 2
                val cy = y + sy * height * scale / 2
                val r = hypot(cx, cy)
                val off = abs(Math.IEEEremainder(atan2(cy.toDouble(), cx.toDouble()) - middle, 2 * Math.PI))
                r in INSET_INNER..INSET_OUTER && off <= half
            }
        }
    }
    var scale = 1f
    while (scale > 0.3f) {
        val room = (0..200).map { INSET_INNER + (INSET_OUTER - INSET_INNER) * it / 200f }.filter { fits(it, scale) }
        if (room.isNotEmpty()) {
            val radius = (room.first() + room.last()) / 2
            return InsetPlace(dx * radius, dy * radius, scale)
        }
        scale -= 0.01f
    }
    return InsetPlace(dx * (INSET_INNER + INSET_OUTER) / 2, dy * (INSET_INNER + INSET_OUTER) / 2, scale)
}
