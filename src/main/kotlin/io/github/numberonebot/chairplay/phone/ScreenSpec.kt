package io.github.numberonebot.chairplay.phone

/** Pixels at each edge of a screen, or of a part of it; all zero is none. */
data class Insets(val top: Int = 0, val bottom: Int = 0, val left: Int = 0, val right: Int = 0) {
    /** Whether every edge is zero. */
    val isNone: Boolean get() = top == 0 && bottom == 0 && left == 0 && right == 0
}

/** Where the phone puts its dock on the screen: where it likes, on the driver's side, or at the bottom. */
enum class Dock { AUTO, DRIVER, BOTTOM }

/**
 * What the screen's touch can do: swipe and tap, only tap, or nothing, for a mouse, where the knob (the keys and the
 * controls window) is the primary input and clicks and drags still reach the phone as taps.
 */
enum class TouchMode { SWIPE, TAP, NONE }

/**
 * The car's main screen as the phone is told of it: [width]x[height] pixels at up to [fps] frames a second, [physicalMm]
 * wide and high, from which the phone picks its scale; the [safe] insets it keeps its controls inside, drawing its
 * picture beyond them with [drawOutsideSafe]; [roundedCorners], or a picture that fills a right-angled screen; where
 * its [dock] goes, and what its [touch] can do.
 */
data class ScreenSpec(
    val width: Int,
    val height: Int,
    val fps: Int,
    val physicalMm: Pair<Int, Int>,
    val safe: Insets,
    val roundedCorners: Boolean,
    val dock: Dock,
    val touch: TouchMode,
    val drawOutsideSafe: Boolean,
)
