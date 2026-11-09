package io.github.numberonebot.chairplay.platform.windows

import com.sun.jna.CallbackReference
import com.sun.jna.Pointer
import com.sun.jna.platform.win32.User32
import com.sun.jna.platform.win32.WinDef.HWND
import com.sun.jna.platform.win32.WinDef.LRESULT
import com.sun.jna.platform.win32.WinUser

/**
 * Moves a window by a press anywhere on its picture, for a window that takes no clicks (the AltScreen): its client
 * area answers WM_NCHITTEST as the title bar does, so Windows drags it, snaps it and maximizes it on a double-click
 * there too. The edges, which Windows answers before the client area, keep sizing it. Runs on the thread that pumps
 * its messages.
 */
internal object ClientDrag {
    const val HTCLIENT = 1L
    const val HTCAPTION = 2L
    private const val WM_NCHITTEST = 0x0084

    // The hooks, kept from the garbage collector.
    private val hooks = mutableListOf<WinUser.WindowProc>()

    /** What the window answers where its own procedure (GLFW's) answered [result]. */
    fun hitTest(result: Long): Long = if (result == HTCLIENT) HTCAPTION else result

    fun install(hwnd: Long) {
        if (hwnd == 0L) return
        // The window procedure the hook hands every message on to.
        var next: Pointer? = null
        val proc = WinUser.WindowProc { handle, message, wParam, lParam ->
            val result = User32.INSTANCE.CallWindowProc(next, handle, message, wParam, lParam) ?: LRESULT(0)
            if (message == WM_NCHITTEST) LRESULT(hitTest(result.toLong())) else result
        }
        next = User32.INSTANCE.SetWindowLongPtr(HWND(Pointer(hwnd)), WinUser.GWL_WNDPROC, CallbackReference.getFunctionPointer(proc))
        hooks += proc
    }
}
