package io.github.numberonebot.chairplay.input

import io.github.numberonebot.chairplay.phone.TouchPoint

data class Size(val width: Int, val height: Int)

/** A mouse sample in pixels; [down] is whether the left button is held. */
data class PointerEvent(val x: Double, val y: Double, val down: Boolean)

/**
 * GLFW reports the cursor in window units but the picture is laid out in framebuffer pixels; the two differ
 * on a scaled Windows display. Everything downstream works in framebuffer pixels.
 */
fun PointerEvent.toFramebuffer(window: Size, framebuffer: Size): PointerEvent =
    if (window.width == 0 || window.height == 0) this
    else copy(x = x * framebuffer.width / window.width, y = y * framebuffer.height / window.height)

/**
 * Maps a pixel position in [window] to a 0..1 point of the [video] picture, which is letterboxed the way
 * VideoWindow draws it (same integer rounding). Null on the bars, or clamped to the nearest edge when [clamp].
 * The phone scales to the screen size itself, so no pixel size is applied here.
 */
fun mapTouch(event: PointerEvent, window: Size, video: Size, clamp: Boolean = false): TouchPoint? {
    if (video.width == 0 || video.height == 0 || window.width == 0 || window.height == 0) return null
    val scale = minOf(window.width.toDouble() / video.width, window.height.toDouble() / video.height)
    val width = (video.width * scale).toInt()
    val height = (video.height * scale).toInt()
    if (width == 0 || height == 0) return null
    val x = (event.x - (window.width - width) / 2) / width
    val y = (event.y - (window.height - height) / 2) / height
    if (!clamp && (x !in 0.0..1.0 || y !in 0.0..1.0)) return null
    return TouchPoint(0, x.coerceIn(0.0, 1.0), y.coerceIn(0.0, 1.0), event.down)
}

/**
 * Turns the stream of mouse samples into the single touch contact: a press starting on the picture begins a
 * gesture, moves while held follow it (clamped to the picture's edge once the cursor leaves), and the release
 * lifts the contact where it last was. Hovering and presses that start on the bars produce nothing.
 * Every sample must reach it, session or not, so its idea of the button never goes stale; [reset] drops the
 * gesture when the session changes. Synchronized because samples arrive on the GL thread and resets on the
 * session's.
 */
class TouchTracker {
    private var last: TouchPoint? = null
    private var ignoring = false
    private var buttonDown = false

    @Synchronized
    fun handle(event: PointerEvent, window: Size, video: Size): TouchPoint? {
        buttonDown = event.down
        val held = last
        if (!event.down) {
            last = null
            ignoring = false
            return held?.copy(down = false)
        }
        if (ignoring) return null
        val contact = mapTouch(event, window, video, clamp = held != null)
        if (contact == null) ignoring = true
        last = contact
        return contact
    }

    /** A button already held when the session changes has no press to start from: nothing is sent until the next one. */
    @Synchronized
    fun reset() {
        last = null
        ignoring = buttonDown
    }
}
