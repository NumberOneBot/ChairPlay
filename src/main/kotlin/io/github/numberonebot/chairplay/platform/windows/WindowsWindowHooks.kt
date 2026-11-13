package io.github.numberonebot.chairplay.platform.windows

import com.sun.jna.Pointer
import com.sun.jna.platform.win32.User32
import com.sun.jna.platform.win32.WinDef.HWND
import com.sun.jna.platform.win32.WinUser
import io.github.numberonebot.chairplay.platform.CloseWatch
import io.github.numberonebot.chairplay.platform.WindowHooks
import org.lwjgl.glfw.GLFWNativeWin32.glfwGetWin32Window

/** The window hooks on Windows: each GLFW window's HWND, its DWM title bar, its window procedure and FlashWindowEx. */
internal class WindowsWindowHooks : WindowHooks {
    override fun nativeHandle(window: Long): Long = glfwGetWin32Window(window)

    override fun paintTitleBar(window: Long, dialog: Boolean) = TitleBar.paint(nativeHandle(window), dialog)

    override fun watchClose(window: Long): CloseWatch {
        val source = CloseSource().apply { watch(nativeHandle(window)) }
        return CloseWatch(source::userClosed)
    }

    override fun dragByPicture(window: Long) = ClientDrag.install(nativeHandle(window))

    override fun mediaKeys(window: Long, key: (key: Int, action: Int, mods: Int) -> Unit) =
        MediaKeyHook.install(nativeHandle(window), key)

    override fun flash(window: Long, on: Boolean) {
        val info = WinUser.FLASHWINFO().apply {
            hWnd = HWND(Pointer(nativeHandle(window)))
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
