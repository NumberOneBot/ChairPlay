package io.github.numberonebot.chairplay.config

/** ui.tint's palettes: the greys of the settings and the controls window (tints.css); white is the light theme. */
val TINTS = listOf("black", "graphite", "slate", "teal", "white")

/** The tint a new settings file gets, and what a value no palette has falls back to (owner, 2026-11-12). */
const val DEFAULT_TINT = "black"

/** The controls window's greys unless ui.tint.controls lets the tint reach it. */
const val CONTROLS_TINT = "black"

/** [value] as one of [TINTS]; [DEFAULT_TINT] for anything else, so a value typed by hand never leaves the pages uncoloured. */
fun tintOf(value: String?): String = value?.trim()?.lowercase()?.takeIf { it in TINTS } ?: DEFAULT_TINT
