package io.github.numberonebot.chairplay.media

import io.github.numberonebot.chairplay.platform.WindowHooks
import org.lwjgl.glfw.GLFW.*
import java.util.concurrent.atomic.AtomicReference

/** Safe-from-any-thread window commands, run on the GL thread at the window loop's next pass. */
class WindowCommands internal constructor(private val wake: () -> Unit, private val hooks: WindowHooks) {
    private val fullscreen = WindowFullscreen()
    private val topmost = AtomicReference<Boolean?>()
    private val flashing = AtomicReference<Boolean?>()

    fun setAlwaysOnTop(enabled: Boolean) { topmost.set(enabled); wake() }
    internal fun toggleFullscreen() { fullscreen.toggle(); wake() }

    /**
     * Flashes the window's taskbar button until the window comes to the front ([on]), as Windows asks for attention,
     * or stops it.
     */
    fun flash(on: Boolean) { flashing.set(on); wake() }

    /** Apply a saved preference before the newly created window is shown. */
    internal fun open(window: Long) = applyTopmost(window)

    internal fun apply(window: Long, tracker: PlacementTracker) {
        fullscreen.apply(window, tracker)
        applyTopmost(window)
        flashing.getAndSet(null)?.let { on -> runCatching { hooks.flash(window, on) } }
    }

    private fun applyTopmost(window: Long) {
        topmost.getAndSet(null)?.let { enabled ->
            glfwSetWindowAttrib(window, GLFW_FLOATING, if (enabled) GLFW_TRUE else GLFW_FALSE)
        }
    }
}
