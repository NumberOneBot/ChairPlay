package io.github.numberonebot.chairplay.drive

import io.github.numberonebot.chairplay.drive.GaugeStyle.CAPTION
import io.github.numberonebot.chairplay.drive.GaugeStyle.GREEN
import io.github.numberonebot.chairplay.drive.GaugeStyle.RED
import io.github.numberonebot.chairplay.drive.GaugeStyle.REGULAR
import org.lwjgl.nanovg.NanoVG.*
import java.util.Locale
import kotlin.math.cos
import kotlin.math.roundToInt
import kotlin.math.sin
import kotlin.math.sqrt

// The Italian dials' insets, in the gap both scales leave to the lower right: the tachometer's coordinates in a corner
// cut from the dial, and the speedometer's pedals and wheel on one arc across its gap.

/** The corner cut from the Italian tachometer, in the face's radius off its centre: right of X and below Y, as wide as deep. */
internal const val ITALIAN_SEGMENT_X = 0.325f
internal const val ITALIAN_SEGMENT_Y = 0.325f

/**
 * The cut's corners, its inner one and the two where its edges meet its circle, rounded alike and softly; and the
 * lines' room from its edges.
 */
private const val SEGMENT_CORNER = 0.06f
private const val SEGMENT_PAD = 0.07f

/** The circle that closes the cut: inside the dial's grey face, short of the knurled ring round it. */
internal const val ITALIAN_SEGMENT_RADIUS = 0.95f

/** The lines' text and their step down, as the boxes' are, and the compass arrow as large as beside the British lines. */
private const val LINE_SIZE = 0.072f
private const val LINE_STEP = 0.087f
private const val ARROW_SIZE = LINE_SIZE * BESIDE_ARROW

/**
 * The speedometer's pedals and wheel: on the rays to 4:10 and 5 o'clock (in degrees as NanoVG turns), in the gap
 * between 3 and 6 o'clock, half-way across the dial's ring from the hub to the face's edge, in the face's radius; sized
 * as in every style.
 */
private const val PEDALS_DEGREES = 35.0
private const val WHEEL_DEGREES = 60.0
private const val ARC_RADIUS = (GaugeStyle.HUB + 1f) / 2

/**
 * The Italian tachometer's coordinates: a corner cut from the dial to the lower right, from its straight edges out to
 * a circle just inside the face's edge, and in it four lines to the left: latitude, longitude, the heading, and the
 * compass arrow.
 */
internal fun italianPosition(pen: NanoPen, view: GaugeView, cx: Float, cy: Float, face: Float, height: Float) {
    val (width, depth) = italianSegmentSize()
    insetBox(
        pen, cx + ITALIAN_SEGMENT_X * face, cy + ITALIAN_SEGMENT_Y * face, width * face, depth * face,
        corner = SEGMENT_CORNER * face, feather = height * 0.3f,
        outline = { _, _, _, _ -> trace(pen.vg, italianSegmentOutline().map { (x, y) -> (cx + x * face) to (cy + y * face) }) },
    )
    // The lines at the coordinates' size in every style, as far in from the cut's top as from its left.
    val size = LINE_SIZE * face
    val left = cx + (ITALIAN_SEGMENT_X + SEGMENT_PAD) * face
    val top = cy + (ITALIAN_SEGMENT_Y + SEGMENT_PAD) * face
    val degrees = (view.headingDegrees.roundToInt() % 360 + 360) % 360
    val lines = listOf(
        String.format(Locale.ROOT, "%.5f", view.position.latitude),
        String.format(Locale.ROOT, "%.5f", view.position.longitude),
    )
    // A minus hangs left of the lines' edge, so every line's first digit, and the heading's, start on it.
    for ((i, line) in lines.withIndex()) {
        val y = top + face * (LINE_SIZE / 2 + LINE_STEP * i)
        val digits = line.removePrefix("-")
        if (digits != line) pen.text("-", left, y, size, CAPTION, REGULAR, NVG_ALIGN_RIGHT)
        pen.text(digits, left, y, size, CAPTION, REGULAR, NVG_ALIGN_LEFT)
    }
    // The heading as the other styles set it: the degrees, then the compass point smaller.
    val angle = "$degrees°"
    val headingY = top + face * (LINE_SIZE / 2 + LINE_STEP * 2)
    pen.text(angle, left, headingY, size, CAPTION, REGULAR, NVG_ALIGN_LEFT)
    val point = POINTS[((degrees + 22.5) / 45).toInt() % POINTS.size]
    pen.text(point, left + pen.textWidth(angle, size, REGULAR) + size * 0.25f, headingY, size * 0.65f, CAPTION, REGULAR, NVG_ALIGN_LEFT)
    // Whatever the wheel shows; turning in place about its middle, set in far enough never to cross the lines' left edge.
    val arrowX = left + arrowReach() * ARROW_SIZE * face
    compassArrow(pen, arrowX, top + face * (LINE_STEP * 3 + ARROW_SIZE / 2), ARROW_SIZE * face, view.headingDegrees, aboutMiddle = true)
}

/** The cut's width and depth, in the face's radius: from its straight edges to where they meet its circle. */
internal fun italianSegmentSize(): Pair<Float, Float> {
    val radius = ITALIAN_SEGMENT_RADIUS
    return (sqrt(radius * radius - ITALIAN_SEGMENT_Y * ITALIAN_SEGMENT_Y) - ITALIAN_SEGMENT_X) to
        (sqrt(radius * radius - ITALIAN_SEGMENT_X * ITALIAN_SEGMENT_X) - ITALIAN_SEGMENT_Y)
}

/**
 * The cut's outline, in the face's radius off the dial's centre, clockwise from its inner corner: the top edge, a soft
 * corner into the circle, the circle, a soft corner out of it, and the left edge. Each soft corner is a circle touching
 * both the edge and the cut's circle, so the outline turns without a kink.
 */
internal fun italianSegmentOutline(): List<Pair<Float, Float>> {
    val x0 = ITALIAN_SEGMENT_X.toDouble()
    val y0 = ITALIAN_SEGMENT_Y.toDouble()
    val big = ITALIAN_SEGMENT_RADIUS.toDouble()
    val corner = SEGMENT_CORNER.toDouble()
    val fillet = corner
    val points = ArrayList<Pair<Float, Float>>()
    fun arc(cx: Double, cy: Double, r: Double, from: Double, to: Double, steps: Int) {
        for (i in 0..steps) {
            val a = from + (to - from) * i / steps
            points += (cx + r * cos(a)).toFloat() to (cy + r * sin(a)).toFloat()
        }
    }
    // The soft corners' centres: a fillet away from their edge, and a fillet inside the cut's circle.
    val topX = sqrt((big - fillet) * (big - fillet) - (y0 + fillet) * (y0 + fillet))
    val leftY = sqrt((big - fillet) * (big - fillet) - (x0 + fillet) * (x0 + fillet))
    val intoCircle = Math.atan2(y0 + fillet, topX)
    val outOfCircle = Math.atan2(leftY, x0 + fillet)
    arc(x0 + corner, y0 + corner, corner, Math.PI, 1.5 * Math.PI, 8)
    arc(topX, y0 + fillet, fillet, 1.5 * Math.PI, 2 * Math.PI + intoCircle, 12)
    arc(0.0, 0.0, big, intoCircle, outOfCircle, 48)
    arc(x0 + fillet, leftY, fillet, outOfCircle, Math.PI, 12)
    return points
}

private fun onArc(degrees: Double): Pair<Float, Float> {
    val at = Math.toRadians(degrees)
    return ARC_RADIUS * cos(at).toFloat() to ARC_RADIUS * sin(at).toFloat()
}

/** The Italian speedometer's pedals, standing side by side on the ray to 4:10, and its wheel on the ray to 5 o'clock. */
internal fun italianControls(pen: NanoPen, view: GaugeView, cx: Float, cy: Float, face: Float) {
    val (px, py) = onArc(PEDALS_DEGREES)
    val x = cx + px * face
    val y = cy + py * face
    standingBar(pen, x - face * PEDALS_STEP / 2, y, face * BAR_THICKNESS, face * BAR_LENGTH, view.throttle, GREEN)
    standingBar(pen, x + face * PEDALS_STEP / 2, y, face * BAR_THICKNESS, face * BAR_LENGTH, view.brake, RED)
    val (wx, wy) = onArc(WHEEL_DEGREES)
    wheel(pen, cx + wx * face, cy + wy * face, face * WHEEL_RADIUS, wheelTurn(view.wheelMode, view.headingDegrees, view.steer))
}

/** A pedal's travel standing up: a track filled from the bottom as far as the pedal is pressed, edged as the wheel. */
private fun standingBar(pen: NanoPen, cx: Float, cy: Float, width: Float, height: Float, pressed: Double, rgb: Int) {
    val vg = pen.vg
    nvgBeginPath(vg)
    nvgRoundedRect(vg, cx - width / 2, cy - height / 2, width, height, width / 2)
    pen.fill(WHEEL)
    if (pressed > 0) {
        val filled = maxOf(width, height * pressed.toFloat())
        nvgBeginPath(vg)
        nvgRoundedRect(vg, cx - width / 2, cy + height / 2 - filled, width, filled, width / 2)
        pen.fill(rgb)
    }
    nvgBeginPath(vg)
    nvgRoundedRect(vg, cx - width / 2, cy - height / 2, width, height, width / 2)
    nvgStrokeWidth(vg, 1.25f)
    pen.stroke(WHEEL_EDGE)
}
