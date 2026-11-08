package io.github.numberonebot.chairplay.config

import io.github.numberonebot.chairplay.phone.ScreenArea

/** The least width and height of a custom part, in pixels. */
const val MIN_AREA = 64

/** altscreen.<group>.area.custom: the group's custom part of the frame, `x, y, width, height`; blank until set. */
fun customKey(group: AreaGroup) = "altscreen.${group.key}.area.custom"

/**
 * A custom part of the AltScreen's frame, in its pixels, as altscreen.<group>.area.custom holds it. On the presets' grid
 * ([snapped]): x, y and the height even, the width a multiple of 8, as DiPlay's streams take them.
 */
data class AreaRect(val x: Int, val y: Int, val width: Int, val height: Int) {
    /** Down to the grid, and at least [MIN_AREA] each way. */
    fun snapped(): AreaRect = AreaRect(
        Math.floorDiv(x, 2) * 2,
        Math.floorDiv(y, 2) * 2,
        maxOf(MIN_AREA, Math.floorDiv(width, 8) * 8),
        maxOf(MIN_AREA, Math.floorDiv(height, 2) * 2),
    )

    /** Inside a [frameWidth]x[frameHeight] frame: its size kept if it fits, else shrunk to the frame's; then moved in. */
    fun clampedInto(frameWidth: Int, frameHeight: Int): AreaRect {
        val w = minOf(width, frameWidth)
        val h = minOf(height, frameHeight)
        return AreaRect(x.coerceIn(0, frameWidth - w), y.coerceIn(0, frameHeight - h), w, h).snapped()
    }

    /** Whether it lies wholly inside a [frameWidth]x[frameHeight] frame. */
    fun fitsInto(frameWidth: Int, frameHeight: Int): Boolean =
        x >= 0 && y >= 0 && x + width <= frameWidth && y + height <= frameHeight

    /** Whether it is at least [MIN_AREA] each way, as a saved rectangle must be. */
    val isLargeEnough: Boolean get() = width >= MIN_AREA && height >= MIN_AREA

    /** The view area at its place. */
    fun area(): ScreenArea = ScreenArea(width, height, x, y)

    override fun toString() = "$x, $y, $width, $height"

    companion object {
        /** Four whole numbers, comma-separated, spaces allowed; null for anything else. */
        fun parse(text: String): AreaRect? {
            val numbers = text.split(',').map { it.trim().toIntOrNull() ?: return null }
            if (numbers.size != 4) return null
            val (x, y, width, height) = numbers
            return AreaRect(x, y, width, height)
        }

        /** [parse]d and [isLargeEnough], as the setting must hold it; null for anything else. */
        fun parseValid(text: String): AreaRect? = parse(text)?.takeIf { it.isLargeEnough }
    }
}

/** What altscreen.<group>.area holds: one of the presets, or the group's custom part. */
sealed interface AreaChoice {
    data class Preset(val preset: AreaPreset) : AreaChoice

    object Custom : AreaChoice {
        const val KEY = "custom"

        override fun toString() = KEY
    }

    companion object {
        /** A preset's key or "custom", in any case; null for anything else. */
        fun of(text: String): AreaChoice? =
            if (text.trim().lowercase() == Custom.KEY) Custom else AreaPreset.of(text)?.let(::Preset)
    }
}
