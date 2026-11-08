package io.github.numberonebot.chairplay.phone

import io.github.numberonebot.chairplay.config.AltscreenCards
import io.github.numberonebot.chairplay.config.AltscreenContent

/**
 * A part of a screen's frame a view can show in: [width]x[height] pixels at [x], [y]; with [safe], the insets from the
 * whole frame its content keeps inside, else those of the screen clipped to it.
 */
data class ScreenArea(val width: Int, val height: Int, val x: Int = 0, val y: Int = 0, val safe: Insets? = null)

/**
 * The second screen, a car's instrument cluster (the AltScreen), as the phone is told of it: [width]x[height] pixels,
 * the [safe] insets its map keeps its marker and attribution inside, the view [areas] it can move between without
 * reconnecting, the first the one it starts in, and the [content] it starts with, its [cards] placed as set.
 */
data class ClusterSpec(
    val width: Int,
    val height: Int,
    val safe: Insets,
    val areas: List<ScreenArea>,
    val content: AltscreenContent,
    val cards: AltscreenCards,
)
