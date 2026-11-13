package io.github.numberonebot.chairplay.platform.windows

import io.github.numberonebot.chairplay.input.MediaKeys
import com.sun.jna.Library
import com.sun.jna.Native
import com.sun.jna.Pointer
import com.sun.jna.platform.win32.User32
import com.sun.jna.platform.win32.WinUser
import com.sun.jna.ptr.IntByReference
import com.sun.jna.platform.win32.WinDef.HWND
import com.sun.jna.platform.win32.WinDef.LPARAM
import com.sun.jna.platform.win32.WinDef.RECT
import com.sun.jna.platform.win32.WinDef.WPARAM
import java.lang.ref.Reference
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.lwjgl.glfw.Callbacks.glfwFreeCallbacks
import org.lwjgl.glfw.GLFW.*

/** The Windows hooks on a real, hidden GLFW window, fed the messages Windows sends it. */
class WindowsWindowHooksTest {
    private val hooks = WindowsWindowHooks()

    // The watch is the only reference to the CloseSource that holds the window procedure's native stub. If the
    // collector took it before the window is destroyed, WM_DESTROY would go through a freed stub and end the JVM.
    private val kept = mutableListOf<Any>()

    // The close button and Alt+F4 come as WM_SYSCOMMAND SC_CLOSE, which Windows then turns into WM_CLOSE; taskkill
    // without /F posts WM_CLOSE alone. The window only hides on the first and ends the app on the second.
    @Test
    fun `the close watch tells the close button from taskkill`() = withWindow { window ->
        val watch = hooks.watchClose(window).also { kept += it }

        send(window, WM_SYSCOMMAND, CloseSource.SC_CLOSE.toLong(), 0)
        assertTrue(watch.userClosed())
        assertFalse(watch.userClosed())

        send(window, WM_CLOSE, 0, 0)
        assertFalse(watch.userClosed())
    }

    // The AltScreen takes no clicks: a press anywhere on its picture moves it, as on its title bar.
    @Test
    fun `a window dragged by its picture answers there as its title bar`() = withWindow { window ->
        val hwnd = HWND(Pointer(hooks.nativeHandle(window)))
        // The middle of the window, on screen, is on its picture.
        val frame = RECT().also { User32.INSTANCE.GetWindowRect(hwnd, it) }
        val x = (frame.left + frame.right) / 2
        val y = (frame.top + frame.bottom) / 2
        val point = (y.toLong() and 0xFFFF shl 16) or (x.toLong() and 0xFFFF)
        assertEquals(ClientDrag.HTCLIENT, send(window, WM_NCHITTEST, 0, point))

        hooks.dragByPicture(window)

        assertEquals(ClientDrag.HTCAPTION, send(window, WM_NCHITTEST, 0, point))
    }

    // The keyboard's track keys are the app's while its window has the focus: Windows gets none of them, so another
    // player does not skip too; the volume keys stay Windows' (owner, 2026-11-13).
    @Test
    fun `the track keys go to the app and not on to Windows, the volume keys do`() = withWindow { window ->
        val glfw = mutableListOf<Int>()
        glfwSetKeyCallback(window) { _, _, scancode, _, _ -> glfw += scancode }
        val got = mutableListOf<Triple<Int, Int, Int>>()
        hooks.mediaKeys(window) { key, action, mods -> got += Triple(key, action, mods) }

        assertEquals(0L, send(window, WM_KEYDOWN, VK_MEDIA_NEXT_TRACK, NEXT_SCAN))
        send(window, WM_KEYDOWN, VK_MEDIA_NEXT_TRACK, NEXT_SCAN or WAS_DOWN)
        send(window, WM_KEYUP, VK_MEDIA_NEXT_TRACK, NEXT_SCAN or WAS_DOWN or RELEASED)
        send(window, WM_KEYDOWN, VK_MEDIA_PREV_TRACK, 1)
        send(window, WM_KEYDOWN, VK_MEDIA_PLAY_PAUSE, 1)
        send(window, WM_KEYDOWN, VK_VOLUME_UP, 1)

        assertEquals(
            listOf(
                Triple(MediaKeys.NEXT, GLFW_PRESS, 0), Triple(MediaKeys.NEXT, GLFW_REPEAT, 0),
                Triple(MediaKeys.NEXT, GLFW_RELEASE, 0), Triple(MediaKeys.PREVIOUS, GLFW_PRESS, 0),
                Triple(MediaKeys.PLAY_PAUSE, GLFW_PRESS, 0),
            ),
            got,
        )
        // GLFW's procedure, which hands keys on to Windows, saw the volume key alone.
        assertEquals(1, glfw.size)
    }

    // Windows draws a dark title bar for a window in dark mode; a dialog's keeps only its close button.
    @Test
    fun `a painted title bar is dark and a dialog's has no size buttons`() = withWindow { window ->
        hooks.paintTitleBar(window, dialog = true)

        val hwnd = HWND(Pointer(hooks.nativeHandle(window)))
        val dark = IntByReference()
        assertEquals(0, Dwm.INSTANCE.DwmGetWindowAttribute(Pointer(hooks.nativeHandle(window)), DWMWA_USE_IMMERSIVE_DARK_MODE, dark, Int.SIZE_BYTES))
        assertEquals(1, dark.value)
        assertEquals(0, User32.INSTANCE.GetWindowLong(hwnd, WinUser.GWL_STYLE) and (WS_MINIMIZEBOX or WS_MAXIMIZEBOX))
    }

    private interface Dwm : Library {
        fun DwmGetWindowAttribute(hwnd: Pointer, attribute: Int, value: IntByReference, size: Int): Int

        companion object {
            val INSTANCE: Dwm = Native.load("dwmapi", Dwm::class.java)
        }
    }

    private fun send(window: Long, message: Int, wParam: Long, lParam: Long): Long =
        User32.INSTANCE.SendMessage(HWND(Pointer(hooks.nativeHandle(window))), message, WPARAM(wParam), LPARAM(lParam)).toLong()

    private fun withWindow(test: (Long) -> Unit) {
        assertTrue(glfwInit())
        glfwDefaultWindowHints()
        glfwWindowHint(GLFW_CLIENT_API, GLFW_NO_API)
        glfwWindowHint(GLFW_VISIBLE, GLFW_FALSE)
        val window = glfwCreateWindow(400, 240, "Window hooks test", 0, 0)
        try {
            assertNotEquals(0L, window)
            test(window)
        } finally {
            if (window != 0L) {
                glfwFreeCallbacks(window)
                glfwDestroyWindow(window)
            }
            Reference.reachabilityFence(kept)
            glfwTerminate()
        }
    }

    private companion object {
        const val WM_KEYDOWN = 0x0100
        const val WM_KEYUP = 0x0101
        const val VK_VOLUME_UP = 0xAFL
        const val VK_MEDIA_NEXT_TRACK = 0xB0L
        const val VK_MEDIA_PREV_TRACK = 0xB1L
        const val VK_MEDIA_PLAY_PAUSE = 0xB3L
        // A key message's lParam: one press, the extended scan code E0 19, and the bits of a repeat and a release.
        const val NEXT_SCAN = 1L or (0x19L shl 16) or (1L shl 24)
        const val WAS_DOWN = 1L shl 30
        const val RELEASED = 1L shl 31
        const val WM_CLOSE = 0x0010
        const val WM_NCHITTEST = 0x0084
        const val WM_SYSCOMMAND = 0x0112
        const val DWMWA_USE_IMMERSIVE_DARK_MODE = 20
        const val WS_MINIMIZEBOX = 0x00020000
        const val WS_MAXIMIZEBOX = 0x00010000
    }
}
