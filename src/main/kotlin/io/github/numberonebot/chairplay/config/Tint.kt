package io.github.numberonebot.chairplay.config

/** ui.tint's palettes, black first and the default: the greys of the settings and the controls window (tints.css). */
val TINTS = listOf("black", "graphite", "slate", "teal", "warm")

/** [value] as one of [TINTS]; black for anything else, so a value typed by hand never leaves the pages uncoloured. */
fun tintOf(value: String?): String = value?.trim()?.lowercase()?.takeIf { it in TINTS } ?: TINTS.first()
