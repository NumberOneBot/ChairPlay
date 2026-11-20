package io.github.numberonebot.chairplay.platform.macos

import io.github.numberonebot.chairplay.platform.CloseWatch
import io.github.numberonebot.chairplay.platform.WindowHooks
import org.lwjgl.glfw.GLFWNativeCocoa

internal class MacWindows : WindowHooks {

    override fun nativeHandle(window: Long): Long =
        GLFWNativeCocoa.glfwGetCocoaWindow(window)

    override fun paintTitleBar(window: Long, dialog: Boolean) {
        val nsWindow = nativeHandle(window)
        MacNative.INSTANCE.cp_win_paint_titlebar(nsWindow, if (dialog) 1 else 0)
    }

    override fun watchClose(window: Long): CloseWatch {
        val nsWindow = nativeHandle(window)
        val watchHandle = MacNative.INSTANCE.cp_win_watch_close(nsWindow)
        return object : CloseWatch {
            override fun userClosed(): Boolean =
                MacNative.INSTANCE.cp_win_user_closed(watchHandle) != 0
        }
    }

    override fun dragByPicture(window: Long) {
        val nsWindow = nativeHandle(window)
        MacNative.INSTANCE.cp_win_drag_by_picture(nsWindow)
    }

    override fun mediaKeys(window: Long, key: (key: Int, action: Int, mods: Int) -> Unit) {
        // No-op for MVP on macOS
    }

    override fun flash(window: Long, on: Boolean) {
        val nsWindow = nativeHandle(window)
        MacNative.INSTANCE.cp_win_flash(nsWindow, if (on) 1 else 0)
    }
}
