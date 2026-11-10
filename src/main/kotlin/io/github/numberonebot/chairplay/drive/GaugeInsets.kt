package io.github.numberonebot.chairplay.drive

import io.github.numberonebot.chairplay.drive.GaugeStyle.GREEN
import io.github.numberonebot.chairplay.drive.GaugeStyle.RED
import org.lwjgl.nanovg.NanoVG.*
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.hypot
import kotlin.math.sin

// What the dials show in the gaps their scales leave: the tachometer the car's coordinates, the speedometer the wheel
// and both pedals. German, they stand under the hubs as Audi's; in the other styles each goes on its gap's middle line,
// as large as fits there ([insetPlace]), the pedals over the wheel.

/** The heading wheel and its edge. */
private const val WHEEL = 0xFF1C1C1F.toInt()
private const val WHEEL_EDGE = 0xFF55555C.toInt()

/** The pedals over the wheel, in the face's radius: as wide as the bars, from their top to the wheel's bottom. */
private const val STACK_WIDTH = 0.38f
private const val STACK_HEIGHT = 0.34f

/** The tachometer's inset, in the gap of its [sweep]: the coordinates and heading in a dark box. */
internal fun positionInset(pen: NanoPen, view: GaugeView, cx: Float, cy: Float, face: Float, sweep: Sweep) {
    val steering = view.wheelMode == WheelMode.STEERING
    val height = face * 0.3f
    if (view.design == GaugeDesign.GERMAN) {
        gaugePosition(pen, cx, cy + face * 0.625f, height, view.position, view.headingDegrees, steering)
        return
    }
    if (view.design == GaugeDesign.BRITISH) {
        britishPosition(pen, view, cx, cy, face, height, gapSide(sweep))
        return
    }
    val width = gaugePositionWidth(pen, height)
    // Italian, a little larger than fits its gap, over the hub's edge.
    val place = insetPlace(sweep, width / face, height / face).let { it.copy(scale = it.scale * ITALIAN_POSITION_GROWTH) }
    val x = cx + place.x * face
    val y = cy + place.y * face
    scaledAbout(pen.vg, x, y, place.scale) { gaugePosition(pen, x, y - height / 2, height, view.position, view.headingDegrees, steering) }
}

/** How much larger the Italian coordinates are than the most their gap holds. */
private const val ITALIAN_POSITION_GROWTH = 1.2f

/** The British screens: black, as the hub. */
private const val BRITISH_SCREEN = GaugeStyle.HUB_FILL

/** The British screens' room above and below the coordinates' lines, in the face's radius: 0.425 high in all. */
private const val BRITISH_PAD = 0.0625f

/**
 * British, as Aston Martin's displays: the coordinates at their own size on 3 or 9 o'clock, the [side] of the gap, the
 * compass beside them, in a screen set into the dial from inside the hub out to the well's edge, which cuts it.
 */
private fun britishPosition(pen: NanoPen, view: GaugeView, cx: Float, cy: Float, face: Float, height: Float, side: Float) {
    gaugePosition(
        pen, cx + side * BRITISH_SCREEN_MIDDLE * face, cy - height / 2, height, view.position, view.headingDegrees,
        view.wheelMode == WheelMode.STEERING, BRITISH_SCREEN, BRITISH_SCREEN_WIDTH * face, britishScreenX(cx, face, side),
        BRITISH_PAD * face, wellCut(pen, cx, cy, face), compassBeside = true,
    )
}

/** The British screens' width, and the middle of what shows of them, from their inner end to the well's edge. */
private const val BRITISH_SCREEN_WIDTH = BRITISH_POSITION_TO - BRITISH_POSITION_FROM
private const val BRITISH_SCREEN_MIDDLE = (BRITISH_POSITION_FROM + 1 / GaugeStyle.FACE) / 2

/** A British screen's left edge on the [side] of the dial at [cx]. */
private fun britishScreenX(cx: Float, face: Float, side: Float) =
    cx + face * if (side > 0) BRITISH_POSITION_FROM else -BRITISH_POSITION_TO

/**
 * The British speedometer's screen, the coordinates' mirror on the [side] of its gap: both pedals as bars toward the
 * hub, and the wheel toward the rim, together in the middle of what shows of it.
 */
private fun britishControls(pen: NanoPen, view: GaugeView, cx: Float, cy: Float, face: Float, side: Float) {
    val height = face * (0.3f + 2 * BRITISH_PAD)
    insetBox(pen, britishScreenX(cx, face, side), cy - height / 2, BRITISH_SCREEN_WIDTH * face, height, BRITISH_SCREEN, wellCut(pen, cx, cy, face))
    // The bars, a gap and the wheel (its radius [hub]), in the face's radius: as large as the screen comfortably holds.
    val bars = 0.34f
    val gap = 0.045f
    val hub = 0.11f
    val start = BRITISH_SCREEN_MIDDLE - (bars + gap + 2 * hub) / 2
    val barsX = cx + side * (start + bars / 2) * face
    pedalBar(pen, barsX, cy - face * 0.046f, face * bars, face * 0.033f, view.throttle, GREEN)
    pedalBar(pen, barsX, cy + face * 0.046f, face * bars, face * 0.033f, view.brake, RED)
    wheel(pen, cx + side * (start + bars + gap + hub) * face, cy, face * hub, wheelTurn(view.wheelMode, view.headingDegrees, view.steer))
}

/** A box's outline cut by the circle of the well round the dial at [cx], [cy]. */
private fun wellCut(pen: NanoPen, cx: Float, cy: Float, face: Float): (Float, Float, Float, Float) -> Unit = { x, y, width, height ->
    trace(pen.vg, roundedRectInCircle(x, y, width, height, height * 0.12f, cx, cy, face / GaugeStyle.FACE))
}

/** Adds the closed outline through [points] to the path. */
private fun trace(vg: Long, points: List<Pair<Float, Float>>) {
    nvgMoveTo(vg, points[0].first, points[0].second)
    for ((x, y) in points.drop(1)) nvgLineTo(vg, x, y)
    nvgClosePath(vg)
}

/**
 * The outline of a rounded rectangle at [x], [y], [width] x [height] with corners of [corner], cut by the circle of
 * [radius] at [circleX], [circleY]: its points outside the circle moved onto it along their rays from its centre, which
 * keeps the cut on the circle from one edge round to the other. NanoVG clips to rectangles only.
 */
internal fun roundedRectInCircle(
    x: Float, y: Float, width: Float, height: Float, corner: Float, circleX: Float, circleY: Float, radius: Float,
): List<Pair<Float, Float>> {
    // The corners clockwise from the top right, each a quarter turn, with the straight sides between them in short steps.
    val turns = listOf(
        Triple(x + width - corner, y + corner, -PI / 2), Triple(x + width - corner, y + height - corner, 0.0),
        Triple(x + corner, y + height - corner, PI / 2), Triple(x + corner, y + corner, PI),
    ).map { (cx, cy, start) ->
        (0..CORNER_STEPS).map { i ->
            val angle = start + PI / 2 * i / CORNER_STEPS
            (cx + corner * cos(angle).toFloat()) to (cy + corner * sin(angle).toFloat())
        }
    }
    val outline = turns.indices.flatMap { i ->
        val end = turns[i].last()
        val next = turns[(i + 1) % turns.size].first()
        turns[i] + (1 until SIDE_STEPS).map { step ->
            val t = step.toFloat() / SIDE_STEPS
            (end.first + (next.first - end.first) * t) to (end.second + (next.second - end.second) * t)
        }
    }
    return outline.map { (px, py) ->
        val distance = hypot(px - circleX, py - circleY)
        if (distance <= radius) px to py
        else (circleX + (px - circleX) * radius / distance) to (circleY + (py - circleY) * radius / distance)
    }
}

private const val CORNER_STEPS = 6
private const val SIDE_STEPS = 48

/** The speedometer's inset, in the gap of its [sweep]: the wheel, turned by the heading or the steering, and both pedals. */
internal fun controlsInset(pen: NanoPen, view: GaugeView, cx: Float, cy: Float, face: Float, sweep: Sweep) {
    val turn = wheelTurn(view.wheelMode, view.headingDegrees, view.steer)
    val barWidth = face * STACK_WIDTH
    val barHeight = face * 0.025f
    if (view.design == GaugeDesign.GERMAN) {
        wheel(pen, cx - face * 0.2f, cy + face * 0.76f, face * 0.112f, turn)
        pedalBar(pen, cx + face * 0.18f, cy + face * 0.72f, barWidth, barHeight, view.throttle, GREEN)
        pedalBar(pen, cx + face * 0.18f, cy + face * 0.80f, barWidth, barHeight, view.brake, RED)
        return
    }
    if (view.design == GaugeDesign.BRITISH) {
        britishControls(pen, view, cx, cy, face, gapSide(sweep))
        return
    }
    val place = insetPlace(sweep, STACK_WIDTH, STACK_HEIGHT)
    val x = cx + place.x * face
    val y = cy + place.y * face
    scaledAbout(pen.vg, x, y, place.scale) {
        pedalBar(pen, x, y - face * 0.1575f, barWidth, barHeight, view.throttle, GREEN)
        pedalBar(pen, x, y - face * 0.0875f, barWidth, barHeight, view.brake, RED)
        wheel(pen, x, y + face * 0.058f, face * 0.112f, turn)
    }
}

/** Draws [draw] scaled by [scale] about [x], [y]. */
private inline fun scaledAbout(vg: Long, x: Float, y: Float, scale: Float, draw: () -> Unit) {
    nvgSave(vg)
    nvgTranslate(vg, x, y)
    nvgScale(vg, scale, scale)
    nvgTranslate(vg, -x, -y)
    draw()
    nvgRestore(vg)
}

/** A pedal's travel: a track filled and edged as the wheel is, filled in colour as far as the pedal is pressed. */
private fun pedalBar(pen: NanoPen, cx: Float, cy: Float, width: Float, height: Float, pressed: Double, rgb: Int) {
    val vg = pen.vg
    nvgBeginPath(vg)
    nvgRoundedRect(vg, cx - width / 2, cy - height / 2, width, height, height / 2)
    pen.fill(WHEEL)
    if (pressed > 0) {
        nvgBeginPath(vg)
        nvgRoundedRect(vg, cx - width / 2, cy - height / 2, maxOf(height, width * pressed.toFloat()), height, height / 2)
        pen.fill(rgb)
    }
    nvgBeginPath(vg)
    nvgRoundedRect(vg, cx - width / 2, cy - height / 2, width, height, height / 2)
    nvgStrokeWidth(vg, 1.25f)
    pen.stroke(WHEEL_EDGE)
}

/**
 * A schematic three-spoke steering wheel, dark and thin, turned by [turn] radians with its red centre mark on it: a
 * round rim, spokes to the left, the right and down, and a broad pad, each edged a shade lighter to stand off the
 * face.
 */
private fun wheel(pen: NanoPen, cx: Float, cy: Float, radius: Float, turn: Double) {
    val vg = pen.vg
    val rim = radius * 0.86f
    val thick = radius * 0.13f
    fun shape(widen: Float, argb: Int) {
        nvgLineCap(vg, NVG_ROUND)
        nvgBeginPath(vg)
        nvgCircle(vg, cx, cy, rim)
        for (spoke in listOf(0.0, PI / 2, PI)) pen.ray(cx, cy, spoke + turn, radius * 0.3f, rim)
        nvgStrokeWidth(vg, thick + widen)
        pen.stroke(argb)
        nvgBeginPath(vg)
        nvgCircle(vg, cx, cy, radius * 0.36f + widen / 2)
        pen.fill(argb)
    }
    // The lighter shape a little wider under the dark one leaves an edge round it.
    shape(2.5f, WHEEL_EDGE)
    shape(0f, WHEEL)
    nvgLineCap(vg, NVG_BUTT)
    pen.arc(cx, cy, rim, -PI / 2 + turn - 0.085, -PI / 2 + turn + 0.085, thick + 2.5f, RED)
}
