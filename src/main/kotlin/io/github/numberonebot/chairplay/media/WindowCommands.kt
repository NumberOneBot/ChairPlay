package io.github.numberonebot.chairplay.media

import io.github.numberonebot.chairplay.platform.WindowHooks
import org.lwjgl.glfw.GLFW.*
import java.util.concurrent.atomic.AtomicReference

/** Safe-from-any-thread window commands, run on the GL thread at the window loop's next pass. */
class WindowCommands internal constructor(private val wake: () -> Unit, private val hooks: WindowHooks) {
    private val fullscreen = WindowFullscreen()
    private val topmost = AtomicReference<Boolean?>()
    private val flashing = AtomicReference<Boolean?>()
    private val tray = AtomicReference<Boolean?>()
    private var handle = 0L

    /**
     * The window this one belongs to, opened before it (the controls window's is CarPlay's, owner 2026-11-26): it has no
     * taskbar button of its own, stays above it, and hides and comes back as that one is minimized and restored.
     */
    internal var owner: WindowCommands? = null

    /** Runs on the GL thread as the window is minimized (true) or comes back from minimized (false). */
    @Volatile
    var onMinimized: (Boolean) -> Unit = {}

    fun setAlwaysOnTop(enabled: Boolean) { topmost.set(enabled); wake() }
    internal fun toggleFullscreen() { fullscreen.toggle(); wake() }

    /**
     * Flashes the window's taskbar button until the window comes to the front ([on]), as Windows asks for attention,
     * or stops it.
     */
    fun flash(on: Boolean) { flashing.set(on); wake() }

    /** Hides the window, minimized and off the taskbar, for the notification area ([on]), or brings it back. */
    fun tray(on: Boolean) { tray.set(on); wake() }

    /** Apply a saved preference before the newly created window is shown. */
    internal fun open(window: Long) {
        handle = window
        owner?.let { hooks.own(window, it.handle) }
        glfwSetWindowIconifyCallback(window) { _, minimized -> onMinimized(minimized) }
        applyTopmost(window)
    }

    /** [uiShown]: the settings page is to cover the window, which then leaves full screen (WindowFullscreen). */
    internal fun apply(window: Long, tracker: PlacementTracker, uiShown: Boolean = false) {
        fullscreen.apply(window, tracker, uiShown)
        applyTopmost(window)
        flashing.getAndSet(null)?.let { on -> runCatching { hooks.flash(window, on) } }
        tray.getAndSet(null)?.let { on -> applyTray(window, on) }
    }

    private fun applyTopmost(window: Long) {
        topmost.getAndSet(null)?.let { enabled ->
            glfwSetWindowAttrib(window, GLFW_FLOATING, if (enabled) GLFW_TRUE else GLFW_FALSE)
        }
    }

    private fun applyTray(window: Long, on: Boolean) {
        if (on) {
            // Minimized first: Windows hides the windows it owns with it, and shows them again as it is restored.
            if (glfwGetWindowAttrib(window, GLFW_ICONIFIED) == GLFW_FALSE) glfwIconifyWindow(window)
            glfwHideWindow(window)
        } else {
            glfwShowWindow(window)
            glfwRestoreWindow(window)
            glfwFocusWindow(window)
        }
    }
}
