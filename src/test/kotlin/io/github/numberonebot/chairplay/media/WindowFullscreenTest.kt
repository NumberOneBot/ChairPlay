package io.github.numberonebot.chairplay.media

import io.github.numberonebot.chairplay.config.ScreenArea
import org.junit.Assert.*
import org.junit.Test
import org.lwjgl.glfw.GLFW.*
import org.lwjgl.glfw.Callbacks.glfwFreeCallbacks

class WindowFullscreenTest {
    @Test fun `uses display containing most of the window`() {
        val screens = listOf(ScreenArea(-1920, 0, 1920, 1080), ScreenArea(0, 0, 2560, 1440))
        assertEquals(0, fullscreenMonitor(ScreenArea(-900, 100, 1000, 600), screens))
        assertEquals(1, fullscreenMonitor(ScreenArea(-100, 100, 1000, 600), screens))
        assertNull(fullscreenMonitor(ScreenArea(0, 0, 800, 480), emptyList()))
    }

    // The settings cannot show over full screen: CarPlay asking for them, or the start screen, takes the window back to
    // its own size, and F waits while they show (owner, 2026-11-12).
    @Test fun `the settings take the window out of full screen and keep it out`() = withWindow { window, tracker ->
        glfwPollEvents()
        val saved = tracker.placement()
        val fullscreen = WindowFullscreen()
        fullscreen.toggle()
        fullscreen.apply(window, tracker, uiShown = false)
        glfwPollEvents()
        assertNotEquals(0L, glfwGetWindowMonitor(window))

        fullscreen.apply(window, tracker, uiShown = true)
        glfwPollEvents()
        assertEquals(0L, glfwGetWindowMonitor(window))
        assertEquals(saved, tracker.placement())

        fullscreen.toggle()
        fullscreen.apply(window, tracker, uiShown = true)
        glfwPollEvents()
        assertEquals(0L, glfwGetWindowMonitor(window))

        // Back on CarPlay the window stays as it is until F
        fullscreen.apply(window, tracker, uiShown = false)
        glfwPollEvents()
        assertEquals(0L, glfwGetWindowMonitor(window))
    }

    private fun withWindow(test: (Long, PlacementTracker) -> Unit) {
        assertTrue(glfwInit())
        try {
            glfwDefaultWindowHints()
            glfwWindowHint(GLFW_CLIENT_API, GLFW_NO_API)
            glfwWindowHint(GLFW_VISIBLE, GLFW_FALSE)
            val window = glfwCreateWindow(800, 480, "Fullscreen test", 0, 0)
            assertNotEquals(0L, window)
            try {
                glfwSetWindowPos(window, 100, 100)
                test(window, PlacementTracker(window, false))
            } finally {
                glfwFreeCallbacks(window)
                glfwDestroyWindow(window)
            }
        } finally {
            glfwTerminate()
        }
    }

    @Test fun `native fullscreen preserves normal and maximized placement`() {
        assertTrue(glfwInit())
        try {
            for (maximized in listOf(false, true)) {
                glfwDefaultWindowHints()
                glfwWindowHint(GLFW_CLIENT_API, GLFW_NO_API)
                glfwWindowHint(GLFW_VISIBLE, GLFW_FALSE)
                val window = glfwCreateWindow(800, 480, "Fullscreen test", 0, 0)
                assertNotEquals(0L, window)
                try {
                    glfwSetWindowPos(window, 100, 100)
                    val tracker = PlacementTracker(window, false)
                    if (maximized) glfwMaximizeWindow(window)
                    glfwPollEvents()
                    val saved = tracker.placement()
                    val fullscreen = WindowFullscreen()
                    fullscreen.toggle()
                    fullscreen.apply(window, tracker)
                    glfwPollEvents()
                    assertNotEquals(0L, glfwGetWindowMonitor(window))
                    assertEquals(saved, tracker.placement())
                    fullscreen.toggle()
                    fullscreen.apply(window, tracker)
                    glfwPollEvents()
                    assertEquals(0L, glfwGetWindowMonitor(window))
                    assertEquals(saved, tracker.placement())
                    assertEquals(if (maximized) GLFW_TRUE else GLFW_FALSE, glfwGetWindowAttrib(window, GLFW_MAXIMIZED))
                } finally {
                    glfwFreeCallbacks(window)
                    glfwDestroyWindow(window)
                }
            }
        } finally {
            glfwTerminate()
        }
    }
}
