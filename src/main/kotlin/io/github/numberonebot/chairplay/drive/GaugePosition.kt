package io.github.numberonebot.chairplay.drive

import io.github.numberonebot.chairplay.drive.GaugeStyle.CAPTION
import io.github.numberonebot.chairplay.drive.GaugeStyle.EDGE
import io.github.numberonebot.chairplay.drive.GaugeStyle.HUB_FILL
import io.github.numberonebot.chairplay.drive.GaugeStyle.REGULAR
import org.lwjgl.nanovg.NanoVG.*
import java.util.Locale
import kotlin.math.roundToInt

internal val POINTS = listOf("N", "NE", "E", "SE", "S", "SW", "W", "NW")

/**
 * The position inset's width at [height]: the widest possible coordinate and heading, rather than the current
 * readings, so it keeps its size as they change.
 */
internal fun gaugePositionWidth(pen: NanoPen, height: Float): Float {
    val size = height * 0.24f
    val reserved = size * 0.6f + size * 0.4f
    val digit = widestDigit(pen, size)
    val coordinateWidth = coordinateWidth(pen, size)
    val degreesWidth = pen.textWidth("${digit.repeat(3)}°", size, REGULAR)
    val pointWidth = POINTS.maxOf { pen.textWidth(it, size * 0.65f, REGULAR) }
    val headingWidth = degreesWidth + size * 0.25f + pointWidth
    return maxOf(coordinateWidth, headingWidth + reserved) + height * 0.26f
}

private fun widestDigit(pen: NanoPen, size: Float) = ('0'..'9').maxBy { pen.textWidth(it.toString(), size, REGULAR) }.toString()

/** The widest coordinate at [size]: a sign, three degrees, and five decimals. */
internal fun coordinateWidth(pen: NanoPen, size: Float): Float {
    val digit = widestDigit(pen, size)
    return pen.textWidth("-${digit.repeat(3)}.${digit.repeat(5)}", size, REGULAR)
}

/**
 * A dark box set into the dial at [x], [y], [width] x [height]: filled [fill], shaded inward from its edges over
 * [feather], and edged; its [outline] (given its x, y, width and height) a rectangle rounded by [corner] unless another
 * is given.
 */
internal fun insetBox(
    pen: NanoPen, x: Float, y: Float, width: Float, height: Float, fill: Int = HUB_FILL,
    outline: ((Float, Float, Float, Float) -> Unit)? = null, corner: Float = height * 0.12f, feather: Float = height * 0.3f,
) {
    val vg = pen.vg
    nvgBeginPath(vg)
    if (outline != null) outline(x, y, width, height) else nvgRoundedRect(vg, x, y, width, height, corner)
    pen.fill(fill)
    nvgBoxGradient(vg, x, y + height * 0.1f, width, height, corner, feather, pen.rgba(0, pen.inner), pen.rgba(0xCC000000.toInt(), pen.outer), pen.paint)
    nvgFillPaint(vg, pen.paint)
    nvgFill(vg)
    nvgStrokeWidth(vg, 1.5f)
    pen.stroke(EDGE)
}

/**
 * Position and heading inset in the tachometer, with the same arrow as the GPS page's compass, set by [height] about
 * [centerX] and on an [insetBox] [boxWidth] wide from [boxX] (the text's own width about [centerX] unless given), [pad]
 * taller above and below, filled [fill], with its [outline]. The arrow, whatever the wheel shows, goes before the heading, or,
 * [compassBeside], larger beside all three lines, in the middle of their height. Without [box], on what is there.
 */
internal fun gaugePosition(
    pen: NanoPen, centerX: Float, y: Float, height: Float, position: GeoPoint, headingDegrees: Double,
    fill: Int = HUB_FILL, boxWidth: Float? = null, boxX: Float? = null, pad: Float = 0f,
    outline: ((Float, Float, Float, Float) -> Unit)? = null, compassBeside: Boolean = false, box: Boolean = true,
) {
    val size = height * 0.24f
    val degrees = (headingDegrees.roundToInt() % 360 + 360) % 360
    val point = POINTS[((degrees + 22.5) / 45).toInt() % POINTS.size]
    val lines = listOf(position.latitude, position.longitude).map { String.format(Locale.ROOT, "%.5f", it) }
    val arrowWidth = size * 0.6f
    val reserved = arrowWidth + size * 0.4f
    val pointSize = size * 0.65f
    val gap = size * 0.25f
    val width = boxWidth ?: gaugePositionWidth(pen, height)
    if (box) insetBox(pen, boxX ?: (centerX - width / 2), y - pad, width, height + 2 * pad, fill, outline)
    // Beside, the lines and the larger arrow share the middle: the lines' column to the left of the arrow.
    val beside = compassBeside
    val bigArrow = arrowWidth * BESIDE_ARROW
    val textX = if (beside) centerX - (gap * 2 + bigArrow) / 2 else centerX
    for ((i, line) in lines.withIndex()) {
        pen.text(line, textX, y + height * (0.24f + 0.29f * i), size, CAPTION, REGULAR)
    }
    val angle = "$degrees°"
    val angleWidth = pen.textWidth(angle, size, REGULAR)
    val labelWidth = pen.textWidth(point, pointSize, REGULAR)
    val arrowSpace = if (beside) 0f else reserved
    val rowX = textX - (arrowSpace + angleWidth + gap + labelWidth) / 2
    val headingX = rowX + arrowSpace
    pen.text(angle, headingX, y + height * 0.82f, size, CAPTION, REGULAR, NVG_ALIGN_LEFT)
    pen.text(point, headingX + angleWidth + gap, y + height * 0.82f, pointSize, CAPTION, REGULAR, NVG_ALIGN_LEFT)
    val arrowX = textX + coordinateWidth(pen, size) / 2 + gap * 2 + bigArrow / 2
    if (beside) compassArrow(pen, arrowX, y + height / 2, size * BESIDE_ARROW, headingDegrees, aboutMiddle = true)
    else compassArrow(pen, rowX + arrowWidth / 2, y + height * 0.79f, size, headingDegrees)
}

/** How much larger the arrow is beside the lines than before the heading. */
internal const val BESIDE_ARROW = 1.3f

/** The compass arrow's corners about its middle, in its 20 units, pointing up; and its outline's width. */
private val ARROW_POINTS = listOf(0f to -7.4f, 4.5f to 7.4f, 0f to 4.7f, -4.5f to 7.4f)
private const val ARROW_STROKE = 1.3f

/**
 * How far the compass arrow, turned about its middle, reaches from it at most, whichever way it points, in its size:
 * what to set its middle in by for it to turn in place and never cross the text's edge.
 */
internal fun arrowReach(): Float = (ARROW_POINTS.maxOf { (x, y) -> kotlin.math.hypot(x, y) } + ARROW_STROKE / 2) / 20

/**
 * The compass arrow, [size] as the text beside it, at [x], [y], pointing [headingDegrees]; [aboutMiddle], its own
 * middle there (it lies 1.1 of its 20 units above the point it otherwise turns about).
 */
internal fun compassArrow(pen: NanoPen, x: Float, y: Float, size: Float, headingDegrees: Double, aboutMiddle: Boolean = false) {
    val vg = pen.vg
    nvgSave(vg)
    nvgTranslate(vg, x, y)
    nvgRotate(vg, Math.toRadians(headingDegrees).toFloat())
    nvgScale(vg, size / 20f, size / 20f)
    if (aboutMiddle) nvgTranslate(vg, 0f, 1.1f)
    nvgBeginPath(vg)
    nvgMoveTo(vg, 0f, -8.5f)
    nvgLineTo(vg, 4.5f, 6.3f)
    nvgLineTo(vg, 0f, 3.6f)
    nvgLineTo(vg, -4.5f, 6.3f)
    nvgClosePath(vg)
    nvgLineJoin(vg, NVG_ROUND)
    nvgStrokeWidth(vg, ARROW_STROKE)
    pen.stroke(CAPTION)
    nvgRestore(vg)
}
