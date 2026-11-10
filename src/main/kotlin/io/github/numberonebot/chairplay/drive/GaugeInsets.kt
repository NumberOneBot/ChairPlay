package io.github.numberonebot.chairplay.drive

import io.github.numberonebot.chairplay.drive.GaugeStyle.GREEN
import io.github.numberonebot.chairplay.drive.GaugeStyle.RED
import org.lwjgl.nanovg.NanoVG.*
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.hypot
import kotlin.math.sin

// What the dials show in the gaps their scales leave: the tachometer the car's coordinates, the speedometer the wheel
// and both pedals. German, they stand under the hubs as Audi's; British, on screens set into the dials as Aston
// Martin's; Italian, as ItalianInsets lays them out.

/** Between the pedals and the wheel, in the face's radius, wherever they lie. */
internal const val CONTROLS_GAP = 0.045f

/** From the throttle's bar to the brake's, middle to middle, in the face's radius, side by side or one over the other. */
internal const val PEDALS_STEP = 0.08f

/** The pedals' bars, long and thick, and the wheel's radius, in the face's radius: the same in every style. */
internal const val BAR_LENGTH = 0.3f
internal const val BAR_THICKNESS = 0.02f
internal const val WHEEL_RADIUS = 0.112f

/** The pedals' bars, one over the other, and the wheel in a row across [middle] at height [cy]; the wheel first or last. */
private fun controlsRow(pen: NanoPen, view: GaugeView, middle: Float, cy: Float, face: Float, wheelFirst: Boolean) {
    val start = middle - face * (BAR_LENGTH + CONTROLS_GAP + 2 * WHEEL_RADIUS) / 2
    val barsX = start + face * (if (wheelFirst) 2 * WHEEL_RADIUS + CONTROLS_GAP + BAR_LENGTH / 2 else BAR_LENGTH / 2)
    val wheelX = start + face * (if (wheelFirst) WHEEL_RADIUS else BAR_LENGTH + CONTROLS_GAP + WHEEL_RADIUS)
    pedalBar(pen, barsX, cy - face * PEDALS_STEP / 2, face * BAR_LENGTH, face * BAR_THICKNESS, view.throttle, GREEN)
    pedalBar(pen, barsX, cy + face * PEDALS_STEP / 2, face * BAR_LENGTH, face * BAR_THICKNESS, view.brake, RED)
    wheel(pen, wheelX, cy, face * WHEEL_RADIUS, wheelTurn(view.wheelMode, view.headingDegrees, view.steer))
}

/** The heading wheel and its edge. */
internal const val WHEEL = 0xFF1C1C1F.toInt()
internal const val WHEEL_EDGE = 0xFF55555C.toInt()

/** The tachometer's inset, in the gap of its [sweep]: the coordinates and heading in a dark box. */
internal fun positionInset(pen: NanoPen, view: GaugeView, cx: Float, cy: Float, face: Float, sweep: Sweep) {
    val steering = view.wheelMode == WheelMode.STEERING
    val height = face * 0.3f
    when (view.design) {
        GaugeDesign.GERMAN -> gaugePosition(pen, cx, cy + face * 0.625f, height, view.position, view.headingDegrees, steering)
        GaugeDesign.BRITISH -> britishPosition(pen, view, cx, cy, face, height, gapSide(sweep))
        GaugeDesign.ITALIAN -> italianPosition(pen, view, cx, cy, face, height)
    }
}

/** The British screens: black, as the hub. */
private const val BRITISH_SCREEN = GaugeStyle.HUB_FILL

/** The British screens' room above and below the coordinates' lines, in the face's radius: 0.425 high in all. */
private const val BRITISH_PAD = 0.0625f

/**
 * British, as Aston Martin's displays: the coordinates at their own size on 3 or 9 o'clock, the [side] of the gap, the
 * compass beside them, in a screen set into the dial from inside the hub out to the well's edge, which cuts it.
 */
private fun britishPosition(pen: NanoPen, view: GaugeView, cx: Float, cy: Float, face: Float, height: Float, side: Float) {
    val pad = BRITISH_PAD * face
    britishScreen(pen, britishScreenX(cx, face, side), cy - height / 2 - pad, height + 2 * pad, cx, cy, face)
    gaugePosition(
        pen, cx + side * BRITISH_SCREEN_MIDDLE * face, cy - height / 2, height, view.position, view.headingDegrees,
        view.wheelMode == WheelMode.STEERING, boxWidth = BRITISH_SCREEN_WIDTH * face, compassBeside = true, box = false,
    )
}

/** The British screens' edge where the well cuts them: almost black, as the screen's glass meets the well's rim. */
private const val BRITISH_RIM = 0xFF0E0E10.toInt()

/**
 * A British screen from [x], [y], [height] high, set into the dial at [cx], [cy] and cut by its well's circle, the cut's
 * edge [BRITISH_RIM] over the box's own.
 */
private fun britishScreen(pen: NanoPen, x: Float, y: Float, height: Float, cx: Float, cy: Float, face: Float) {
    val vg = pen.vg
    val radius = face / GaugeStyle.FACE
    val width = BRITISH_SCREEN_WIDTH * face
    val outline = roundedRectInCircle(x, y, width, height, height * 0.12f, cx, cy, radius)
    insetBox(pen, x, y, width, height, BRITISH_SCREEN, { _, _, _, _ -> trace(vg, outline) })
    val edge = circleEdge(outline, cx, cy, radius)
    if (edge.size < 2) return
    nvgBeginPath(vg)
    nvgMoveTo(vg, edge[0].first, edge[0].second)
    for ((px, py) in edge.drop(1)) nvgLineTo(vg, px, py)
    nvgStrokeWidth(vg, 2f)
    pen.stroke(BRITISH_RIM)
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
    britishScreen(pen, britishScreenX(cx, face, side), cy - height / 2, height, cx, cy, face)
    // The bars toward the hub, the wheel toward the rim.
    controlsRow(pen, view, cx + side * BRITISH_SCREEN_MIDDLE * face, cy, face, wheelFirst = side < 0)
}

/** Adds the closed outline through [points] to the path. */
internal fun trace(vg: Long, points: List<Pair<Float, Float>>) {
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

/**
 * The part of a closed [outline] made by [roundedRectInCircle] that lies on the circle of [radius] at [circleX],
 * [circleY]: the cut's edge, in order from one end to the other; empty for a box the circle does not cut.
 */
internal fun circleEdge(outline: List<Pair<Float, Float>>, circleX: Float, circleY: Float, radius: Float): List<Pair<Float, Float>> {
    fun on(p: Pair<Float, Float>) = abs(hypot(p.first - circleX, p.second - circleY) - radius) < radius * 1e-4f
    // Started from a point off the circle, the run on it cannot wrap round the list's end.
    val off = outline.indexOfFirst { !on(it) }
    if (off < 0) return outline
    val turned = outline.drop(off) + outline.take(off)
    return turned.dropWhile { !on(it) }.takeWhile(::on)
}

private const val CORNER_STEPS = 6
private const val SIDE_STEPS = 48

/** The speedometer's inset, in the gap of its [sweep]: the wheel, turned by the heading or the steering, and both pedals. */
internal fun controlsInset(pen: NanoPen, view: GaugeView, cx: Float, cy: Float, face: Float, sweep: Sweep) {
    when (view.design) {
        // Under the hub, the wheel on the left.
        GaugeDesign.GERMAN -> controlsRow(pen, view, cx, cy + face * 0.76f, face, wheelFirst = true)
        GaugeDesign.BRITISH -> britishControls(pen, view, cx, cy, face, gapSide(sweep))
        GaugeDesign.ITALIAN -> italianControls(pen, view, cx, cy, face)
    }
}

/** Draws [draw] scaled by [scale] about [x], [y]. */
internal inline fun scaledAbout(vg: Long, x: Float, y: Float, scale: Float, draw: () -> Unit) {
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
internal fun wheel(pen: NanoPen, cx: Float, cy: Float, radius: Float, turn: Double) {
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
