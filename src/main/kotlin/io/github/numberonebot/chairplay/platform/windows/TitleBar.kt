package io.github.numberonebot.chairplay.platform.windows

import com.sun.jna.Library
import com.sun.jna.Native
import com.sun.jna.Pointer
import com.sun.jna.platform.win32.User32
import com.sun.jna.platform.win32.WinDef.HWND
import com.sun.jna.platform.win32.WinUser
import com.sun.jna.ptr.IntByReference

/**
 * Paints a window's title bar black through the Desktop Window Manager (dwmapi.h). Windows 11 takes
 * the caption and text colours; Windows 10 from 20H1 knows only dark mode, which is near black.
 * An attribute the system does not know fails on its own and leaves the default bar. A [dialog] window, one of a
 * fixed size, keeps only its close button, as a dialog's title bar does.
 */
internal object TitleBar {
    private interface Dwm : Library {
        fun DwmSetWindowAttribute(hwnd: Pointer, attribute: Int, value: IntByReference, size: Int): Int
    }

    private val dwm: Dwm? by lazy { runCatching { Native.load("dwmapi", Dwm::class.java) }.getOrNull() }

    fun paint(hwnd: Long, dialog: Boolean = false) {
        if (hwnd == 0L) return
        if (dialog) runCatching { dropSizeButtons(HWND(Pointer(hwnd))) }
        val api = dwm ?: return
        val window = Pointer(hwnd)
        api.DwmSetWindowAttribute(window, DWMWA_USE_IMMERSIVE_DARK_MODE, IntByReference(1), Int.SIZE_BYTES)
        api.DwmSetWindowAttribute(window, DWMWA_CAPTION_COLOR, IntByReference(BLACK), Int.SIZE_BYTES)
        api.DwmSetWindowAttribute(window, DWMWA_TEXT_COLOR, IntByReference(WHITE), Int.SIZE_BYTES)
    }

    // Without the minimize and maximize boxes in its style, and the frame drawn again to show it.
    private fun dropSizeButtons(window: HWND) {
        val user32 = User32.INSTANCE
        val style = user32.GetWindowLong(window, WinUser.GWL_STYLE)
        user32.SetWindowLong(window, WinUser.GWL_STYLE, style and (WS_MINIMIZEBOX or WS_MAXIMIZEBOX).inv())
        user32.SetWindowPos(window, null, 0, 0, 0, 0, SWP_NOMOVE or SWP_NOSIZE or SWP_NOZORDER or SWP_NOACTIVATE or SWP_FRAMECHANGED)
    }

    private const val DWMWA_USE_IMMERSIVE_DARK_MODE = 20
    private const val DWMWA_CAPTION_COLOR = 35
    private const val DWMWA_TEXT_COLOR = 36

    // winuser.h
    private const val WS_MINIMIZEBOX = 0x00020000
    private const val WS_MAXIMIZEBOX = 0x00010000
    private const val SWP_NOSIZE = 0x0001
    private const val SWP_NOMOVE = 0x0002
    private const val SWP_NOZORDER = 0x0004
    private const val SWP_NOACTIVATE = 0x0010
    private const val SWP_FRAMECHANGED = 0x0020

    // COLORREF is 0x00BBGGRR.
    private const val BLACK = 0x000000
    private const val WHITE = 0xFFFFFF
}
