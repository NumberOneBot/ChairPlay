package io.github.numberonebot.chairplay.drive

import org.lwjgl.nanovg.NVGColor
import org.lwjgl.nanovg.NVGPaint
import org.lwjgl.nanovg.NanoVG.*
import kotlin.math.cos
import kotlin.math.sin

/** NanoVG drawing helpers over the context [vg], with the colour and paint buffers they fill. */
internal class NanoPen(val vg: Long) {
    /** Whether the fonts loaded: without them, [text] draws nothing. */
    var fonts = false
    private val color = NVGColor.create()
    val inner: NVGColor = NVGColor.create()
    val outer: NVGColor = NVGColor.create()
    val paint: NVGPaint = NVGPaint.create()

    fun fill(argb: Int) {
        nvgFillColor(vg, rgba(argb, color))
        nvgFill(vg)
    }

    fun stroke(argb: Int) {
        nvgStrokeColor(vg, rgba(argb, color))
        nvgStroke(vg)
    }

    /** The arc between the angles [from] and [to], whichever way a scale runs between them. */
    fun arc(cx: Float, cy: Float, radius: Float, from: Double, to: Double, width: Float, argb: Int) {
        nvgBeginPath(vg)
        nvgArc(vg, cx, cy, radius, minOf(from, to).toFloat(), maxOf(from, to).toFloat(), NVG_CW)
        nvgStrokeWidth(vg, width)
        stroke(argb)
    }

    /** A line along [angle] from [from] to [to] off the centre, added to the path. */
    fun ray(cx: Float, cy: Float, angle: Double, from: Float, to: Float) {
        val dx = cos(angle).toFloat()
        val dy = sin(angle).toFloat()
        nvgMoveTo(vg, cx + dx * from, cy + dy * from)
        nvgLineTo(vg, cx + dx * to, cy + dy * to)
    }

    /** [text] at [x], [y], centred unless another horizontal [align] is given. */
    fun text(text: String, x: Float, y: Float, size: Float, argb: Int, font: String, align: Int = NVG_ALIGN_CENTER) {
        if (!fonts) return
        nvgFontFace(vg, font)
        nvgFontSize(vg, size)
        nvgTextAlign(vg, align or NVG_ALIGN_MIDDLE)
        nvgFillColor(vg, rgba(argb, color))
        nvgText(vg, x, y, text)
    }

    fun textWidth(text: String, size: Float, font: String): Float {
        if (!fonts) return 0f
        nvgFontFace(vg, font)
        nvgFontSize(vg, size)
        return nvgTextBounds(vg, 0f, 0f, text, null as java.nio.FloatBuffer?)
    }

    fun rgba(argb: Int, into: NVGColor): NVGColor =
        nvgRGBA((argb shr 16 and 0xFF).toByte(), (argb shr 8 and 0xFF).toByte(), (argb and 0xFF).toByte(), (argb ushr 24).toByte(), into)
}
