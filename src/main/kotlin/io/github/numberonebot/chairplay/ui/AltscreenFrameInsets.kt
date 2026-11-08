package io.github.numberonebot.chairplay.ui

import io.github.numberonebot.chairplay.phone.AltscreenContent
import io.github.numberonebot.chairplay.config.AltscreenViewParts
import io.github.numberonebot.chairplay.config.areaGroup
import io.github.numberonebot.chairplay.config.DesktopConfig
import io.github.numberonebot.chairplay.phone.Insets

/**
 * The insets Show frame draws on the AltScreen, from the settings as saved ([values]), so the frame previews them before
 * a restart puts them in force; blank is none. Without a [view], altscreen.safe.*; with one ("map", "map-card", "card"),
 * its area as the connection declares it: its own margins inside its part, or the AltScreen's clipped to its part.
 * Null for a view that has no part of the frame, or settings that give none.
 */
internal fun altscreenFrameInsets(values: Map<String, String>, view: String?): Insets? {
    fun inset(edge: String) = values["altscreen.safe.$edge"]?.trim()?.toIntOrNull()?.coerceAtLeast(0) ?: 0
    if (view == null) return Insets(top = inset("top"), bottom = inset("bottom"), left = inset("left"), right = inset("right"))
    val content = AltscreenContent.entries.firstOrNull { it.key == view && it.areaGroup != null } ?: return null
    val (width, height) = altscreenFrameSize(values)
    val parts = runCatching { AltscreenViewParts(width, height, values::get) }.getOrNull() ?: return null
    val part = parts.areas[parts.indexOf(content)]
    return part.safe ?: Insets(
        top = maxOf(inset("top"), part.y),
        bottom = maxOf(inset("bottom"), height - part.y - part.height),
        left = maxOf(inset("left"), part.x),
        right = maxOf(inset("right"), width - part.x - part.width),
    )
}

/** The AltScreen's frame as the settings saved ([values]) give it, which the insets above are in the pixels of. */
internal fun altscreenFrameSize(values: Map<String, String>): Pair<Int, Int> {
    fun size(key: String, default: Int) = values[key]?.trim()?.toIntOrNull() ?: default
    return size("altscreen.width", DesktopConfig.DEFAULT_ALTSCREEN_WIDTH) to size("altscreen.height", DesktopConfig.DEFAULT_ALTSCREEN_HEIGHT)
}
