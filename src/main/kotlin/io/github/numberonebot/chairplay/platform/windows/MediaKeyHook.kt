package io.github.numberonebot.chairplay.platform.windows

import com.sun.jna.CallbackReference
import com.sun.jna.Pointer
import com.sun.jna.platform.win32.User32
import com.sun.jna.platform.win32.WinDef.HWND
import com.sun.jna.platform.win32.WinDef.LRESULT
import com.sun.jna.platform.win32.WinUser
import io.github.numberonebot.chairplay.input.MediaKeys
import org.lwjgl.glfw.GLFW.GLFW_PRESS
import org.lwjgl.glfw.GLFW.GLFW_RELEASE
import org.lwjgl.glfw.GLFW.GLFW_REPEAT

/**
 * Takes the keyboard's track keys out of a window's messages, ahead of its own procedure (GLFW's), which has no codes
 * for them and hands them on to DefWindowProc: that turns them into WM_APPCOMMAND, which Windows passes to the player
 * it has in front, so that player would skip as well. Taken here, they go to the app alone. The volume keys go through
 * as before and stay Windows' (owner, 2026-11-13). Runs on the thread that pumps the window's messages.
 */
internal object MediaKeyHook {
    // The hooks, kept from the garbage collector.
    private val hooks = mutableListOf<WinUser.WindowProc>()

    /** The app's code and GLFW action for a key message, or null for a message that is no track key. */
    fun trackKey(message: Int, virtualKey: Int, lParam: Long): Pair<Int, Int>? {
        val code = CODES[virtualKey] ?: return null
        val action = when (message) {
            WM_KEYDOWN, WM_SYSKEYDOWN -> if (lParam and WAS_DOWN != 0L) GLFW_REPEAT else GLFW_PRESS
            WM_KEYUP, WM_SYSKEYUP -> GLFW_RELEASE
            else -> return null
        }
        return code to action
    }

    fun install(hwnd: Long, key: (key: Int, action: Int, mods: Int) -> Unit) {
        if (hwnd == 0L) return
        // The window procedure the hook hands every other message on to.
        var next: Pointer? = null
        val proc = WinUser.WindowProc { handle, message, wParam, lParam ->
            val taken = trackKey(message, wParam.toInt(), lParam.toLong())
            if (taken != null) {
                key(taken.first, taken.second, 0)
                LRESULT(0)
            } else {
                User32.INSTANCE.CallWindowProc(next, handle, message, wParam, lParam) ?: LRESULT(0)
            }
        }
        next = User32.INSTANCE.SetWindowLongPtr(HWND(Pointer(hwnd)), WinUser.GWL_WNDPROC, CallbackReference.getFunctionPointer(proc))
        hooks += proc
    }

    private const val WM_KEYDOWN = 0x0100
    private const val WM_KEYUP = 0x0101
    private const val WM_SYSKEYDOWN = 0x0104
    private const val WM_SYSKEYUP = 0x0105
    // lParam bit 30: the key was down before this message, so it is a repeat.
    private const val WAS_DOWN = 1L shl 30

    // VK_MEDIA_NEXT_TRACK, VK_MEDIA_PREV_TRACK and VK_MEDIA_PLAY_PAUSE.
    private val CODES = mapOf(0xB0 to MediaKeys.NEXT, 0xB1 to MediaKeys.PREVIOUS, 0xB3 to MediaKeys.PLAY_PAUSE)
}
