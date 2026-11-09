package io.github.numberonebot.chairplay.media

import com.sun.jna.Pointer
import com.sun.jna.platform.win32.User32
import com.sun.jna.platform.win32.WinDef.HWND
import com.sun.jna.platform.win32.WinUser
import io.github.numberonebot.chairplay.platform.FakeWindowHooks
import org.junit.Assert.*
import org.junit.Test
import org.lwjgl.glfw.GLFW.*
import org.lwjgl.glfw.GLFWNativeWin32.glfwGetWin32Window
import org.lwjgl.glfw.Callbacks.glfwFreeCallbacks

class WindowCommandsTest {
    @Test fun `saved and live topmost requests affect only their window without changing its placement`() {
        assertTrue(glfwInit())
        val windows = mutableListOf<Long>()
        try {
            repeat(2) { windows += hiddenWindow() }
            val first = windows[0]
            val second = windows[1]
            val tracker = PlacementTracker(first, false)
            val before = tracker.placement()
            var wakes = 0
            val commands = WindowCommands({ wakes++ }, FakeWindowHooks())
            assertFalse(topmost(first))
            assertFalse(topmost(second))
            commands.setAlwaysOnTop(true)
            commands.open(first)
            assertTrue(topmost(first))
            assertFalse(topmost(second))
            assertEquals(GLFW_FALSE, glfwGetWindowAttrib(first, GLFW_VISIBLE))
            Thread { commands.setAlwaysOnTop(false) }.apply { start(); join() }
            assertTrue(topmost(first))
            commands.apply(first, tracker)
            assertFalse(topmost(first))
            assertEquals(before, tracker.placement())
            assertEquals(2, wakes)
            commands.setAlwaysOnTop(true)
            commands.setAlwaysOnTop(false)
            commands.apply(first, tracker)
            assertFalse(topmost(first))
        } finally {
            windows.forEach { glfwFreeCallbacks(it); glfwDestroyWindow(it) }
            glfwTerminate()
        }
    }

    // Any thread may ask for the flash; the window loop applies it on the GL thread at its next pass, once.
    @Test fun `a flash reaches the hooks on the next pass`() {
        assertTrue(glfwInit())
        val window = hiddenWindow()
        try {
            val hooks = FakeWindowHooks()
            val commands = WindowCommands({}, hooks)
            commands.flash(true)
            assertEquals(emptyList<Pair<Long, Boolean>>(), hooks.flashes)
            commands.apply(window, PlacementTracker(window, false))
            assertEquals(listOf(window to true), hooks.flashes)
            commands.apply(window, PlacementTracker(window, false))
            assertEquals(listOf(window to true), hooks.flashes)
        } finally {
            glfwFreeCallbacks(window)
            glfwDestroyWindow(window)
            glfwTerminate()
        }
    }

    private fun hiddenWindow(): Long {
        glfwDefaultWindowHints()
        glfwWindowHint(GLFW_CLIENT_API, GLFW_NO_API)
        glfwWindowHint(GLFW_VISIBLE, GLFW_FALSE)
        return glfwCreateWindow(800, 480, "Window state test", 0, 0).also { assertNotEquals(0L, it) }
    }

    private fun topmost(window: Long): Boolean =
        User32.INSTANCE.GetWindowLong(HWND(Pointer(glfwGetWin32Window(window))), WinUser.GWL_EXSTYLE) and 0x8 != 0
}
