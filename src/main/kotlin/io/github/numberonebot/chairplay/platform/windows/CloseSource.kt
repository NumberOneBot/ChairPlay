package io.github.numberonebot.chairplay.platform.windows

import com.sun.jna.CallbackReference
import com.sun.jna.Pointer
import com.sun.jna.platform.win32.User32
import com.sun.jna.platform.win32.WinDef.HWND
import com.sun.jna.platform.win32.WinDef.LRESULT
import com.sun.jna.platform.win32.WinDef.WPARAM
import com.sun.jna.platform.win32.WinUser

/**
 * Tells the close button and Alt+F4 from a close asked from outside the app: Windows turns the first into
 * WM_SYSCOMMAND SC_CLOSE before WM_CLOSE, while taskkill without /F posts WM_CLOSE alone, to one of the app's windows.
 * A window that only hides on its close button ends the app on the second. [watch] follows a window's messages;
 * [userClosed], in the WM_CLOSE that follows, says which it was. Both run on the thread that pumps its messages.
 */
internal class CloseSource {
    private var asked = false
    // The window procedure the hook hands every message on to, and the hook, kept from the garbage collector.
    private var next: Pointer? = null
    private var hook: WinUser.WindowProc? = null

    /** A WM_SYSCOMMAND came with [command]; its low four bits are Windows' own. */
    fun systemCommand(command: Int) {
        if (command and 0xFFF0 == SC_CLOSE) asked = true
    }

    /** Whether the close being handled followed the close button or Alt+F4; asked once per close. */
    fun userClosed(): Boolean = asked.also { asked = false }

    /** Sees the window's WM_SYSCOMMANDs ahead of its own procedure (GLFW's), which still gets every message. */
    fun watch(hwnd: Long) {
        if (hwnd == 0L || hook != null) return
        val window = HWND(Pointer(hwnd))
        val proc = WinUser.WindowProc { handle, message, wParam, lParam ->
            if (message == WM_SYSCOMMAND) systemCommand(wParam.toInt())
            User32.INSTANCE.CallWindowProc(next, handle, message, wParam, lParam) ?: LRESULT(0)
        }
        hook = proc
        next = User32.INSTANCE.SetWindowLongPtr(window, WinUser.GWL_WNDPROC, CallbackReference.getFunctionPointer(proc))
    }

    companion object {
        const val SC_CLOSE = 0xF060
        private const val WM_SYSCOMMAND = 0x0112
    }
}

private fun WPARAM.toInt(): Int = toLong().toInt()
