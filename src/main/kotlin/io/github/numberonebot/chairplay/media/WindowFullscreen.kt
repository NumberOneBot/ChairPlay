package io.github.numberonebot.chairplay.media

import io.github.numberonebot.chairplay.config.ScreenArea
import io.github.numberonebot.chairplay.config.isVisibleOn
import org.lwjgl.glfw.GLFW.*
import java.util.concurrent.atomic.AtomicInteger

/**
 * Requests may come from WebView; all native changes stay on the window's event thread. The settings page cannot show
 * over full screen (its web view is a child window the full-screen picture leaves out): while it is to show
 * ([uiShown]) the window leaves full screen for its own size, and F does nothing until CarPlay shows again; back on
 * CarPlay the window stays as it is until F (owner, 2026-11-12).
 */
internal class WindowFullscreen {
    private val requests = AtomicInteger()
    fun toggle() { requests.incrementAndGet() }

    fun apply(window: Long, tracker: PlacementTracker, uiShown: Boolean = false) {
        // GLFW leaves full screen itself when its monitor is unplugged.
        if (tracker.suspended && glfwGetWindowMonitor(window) == 0L) restore(window, tracker)
        val toggled = requests.getAndSet(0) % 2 != 0
        if (glfwGetWindowMonitor(window) != 0L) {
            if (toggled || uiShown) restore(window, tracker)
            return
        }
        if (!toggled || uiShown) return
        val monitors = glfwGetMonitors() ?: return
        val handles = (0 until monitors.limit()).map { monitors[it] }
        val areas = handles.map { monitor ->
            val x = IntArray(1)
            val y = IntArray(1)
            glfwGetMonitorPos(monitor, x, y)
            val mode = glfwGetVideoMode(monitor)
            ScreenArea(x[0], y[0], mode?.width() ?: 0, mode?.height() ?: 0)
        }
        val x = IntArray(1)
        val y = IntArray(1)
        val w = IntArray(1)
        val h = IntArray(1)
        glfwGetWindowPos(window, x, y)
        glfwGetWindowSize(window, w, h)
        val index = fullscreenMonitor(ScreenArea(x[0], y[0], w[0], h[0]), areas) ?: return
        val monitor = handles[index]
        val mode = glfwGetVideoMode(monitor) ?: return
        tracker.suspended = true
        if (glfwGetWindowAttrib(window, GLFW_MAXIMIZED) == GLFW_TRUE) glfwRestoreWindow(window)
        glfwSetWindowAttrib(window, GLFW_AUTO_ICONIFY, GLFW_FALSE)
        glfwSetWindowMonitor(window, monitor, 0, 0, mode.width(), mode.height(), mode.refreshRate())
    }

    private fun restore(window: Long, tracker: PlacementTracker) {
        val saved = tracker.placement()
        val areas = workAreas()
        val fallback = areas.firstOrNull()
        val placement = if (saved.isVisibleOn(areas) || fallback == null) saved
            else saved.copy(x = fallback.x, y = fallback.y)
        glfwSetWindowMonitor(window, 0L, placement.x, placement.y, placement.width, placement.height, GLFW_DONT_CARE)
        if (placement.maximized) glfwMaximizeWindow(window)
        tracker.suspended = false
    }
}

/** Pick the display containing most of the window, including monitors left of the primary display. */
internal fun fullscreenMonitor(window: ScreenArea, monitors: List<ScreenArea>): Int? =
    monitors.indices.maxByOrNull { i ->
        val screen = monitors[i]
        val width = (minOf(window.x + window.width, screen.x + screen.width) - maxOf(window.x, screen.x)).coerceAtLeast(0)
        val height = (minOf(window.y + window.height, screen.y + screen.height) - maxOf(window.y, screen.y)).coerceAtLeast(0)
        width.toLong() * height
    }
