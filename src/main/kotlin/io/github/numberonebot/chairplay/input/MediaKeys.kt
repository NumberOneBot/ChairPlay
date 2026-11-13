package io.github.numberonebot.chairplay.input

/**
 * The keyboard's own track keys, which GLFW has no codes for: the window hooks report them under these, above every
 * GLFW key, and controls.properties names them MediaPrevious, MediaPlayPause and MediaNext. They are pinned to their
 * actions as Escape and Enter are. The volume keys are not among them: they stay Windows' (owner, 2026-11-13).
 */
object MediaKeys {
    const val PREVIOUS = 0x1000
    const val PLAY_PAUSE = 0x1001
    const val NEXT = 0x1002
}
