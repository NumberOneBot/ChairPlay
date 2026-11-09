package io.github.numberonebot.chairplay.input

/** Keys seen while another window has focus; [start] begins listening and [close] stops it. */
internal interface GlobalKeyboardSource : AutoCloseable { fun start() }

internal class GlobalKeyboardCallbacks(
    val key: (Int, Int, Int, Boolean) -> Unit,
    val reset: () -> Unit,
    val ready: () -> Unit,
    val failed: (String) -> Unit,
)
