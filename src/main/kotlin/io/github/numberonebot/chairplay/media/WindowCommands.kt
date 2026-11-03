package io.github.numberonebot.chairplay.media

import org.lwjgl.glfw.GLFW.*
import java.util.concurrent.atomic.AtomicReference

/** Settings can request changes from any thread; GLFW applies them on the window's event thread. */
class WindowCommands internal constructor(private val wake: () -> Unit) {
    private val fullscreen = WindowFullscreen()
    private val topmost = AtomicReference<Boolean?>()

    fun setAlwaysOnTop(enabled: Boolean) { topmost.set(enabled); wake() }
    internal fun toggleFullscreen() { fullscreen.toggle(); wake() }

    /** Apply a saved preference before the newly created window is shown. */
    internal fun open(window: Long) = applyTopmost(window)

    internal fun apply(window: Long, tracker: PlacementTracker) {
        fullscreen.apply(window, tracker)
        applyTopmost(window)
    }

    private fun applyTopmost(window: Long) {
        topmost.getAndSet(null)?.let { enabled ->
            glfwSetWindowAttrib(window, GLFW_FLOATING, if (enabled) GLFW_TRUE else GLFW_FALSE)
        }
    }
}
