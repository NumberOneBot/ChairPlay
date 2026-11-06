package io.github.numberonebot.chairplay.media

import com.sun.jna.platform.win32.User32
import com.sun.jna.platform.win32.WinDef.HWND
import com.sun.jna.platform.win32.WinUser
import org.lwjgl.glfw.GLFW.*
import org.lwjgl.glfw.GLFWNativeWin32.glfwGetWin32Window
import com.sun.jna.Pointer
import java.util.concurrent.atomic.AtomicReference

/** Safe-from-any-thread window commands, run on the GL thread at the window loop's next pass. */
class WindowCommands internal constructor(private val wake: () -> Unit) {
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
        flashing.getAndSet(null)?.let { on -> runCatching { applyFlash(window, on) } }
    }

    private fun applyTopmost(window: Long) {
        topmost.getAndSet(null)?.let { enabled ->
            glfwSetWindowAttrib(window, GLFW_FLOATING, if (enabled) GLFW_TRUE else GLFW_FALSE)
        }
    }

    private fun applyFlash(window: Long, on: Boolean) {
        val info = WinUser.FLASHWINFO().apply {
            hWnd = HWND(Pointer(glfwGetWin32Window(window)))
            dwFlags = if (on) FLASHW_TRAY or FLASHW_TIMERNOFG else FLASHW_STOP
            uCount = 0
            dwTimeout = 0
        }
        User32.INSTANCE.FlashWindowEx(info)
    }

    private companion object {
        const val FLASHW_STOP = 0
        const val FLASHW_TRAY = 2
        const val FLASHW_TIMERNOFG = 12
    }
}
