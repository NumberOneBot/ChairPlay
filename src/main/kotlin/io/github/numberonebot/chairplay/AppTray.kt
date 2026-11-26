package io.github.numberonebot.chairplay

import io.github.numberonebot.chairplay.media.TrayWindows
import io.github.numberonebot.chairplay.media.VideoWindow
import java.awt.EventQueue
import java.awt.SystemTray
import java.awt.TrayIcon
import java.awt.event.MouseAdapter
import java.awt.event.MouseEvent
import java.awt.image.BaseMultiResolutionImage
import javax.imageio.ImageIO

/**
 * ChairPlay's icon in the notification area while window.tray is on (owner, 2026-11-26), with no menu: a click on it
 * shows the windows waiting there or hides them ([TrayWindows]). The icon is new to the app: the toast (toast.cc) is a
 * WinRT notification that needs none, and AWT's SystemTray, in the image already (java.desktop), saves native code.
 * AWT starts only once the setting is on.
 */
internal class AppTray(main: VideoWindow, altscreen: VideoWindow) {
    private val windows = TrayWindows(main, listOf(altscreen), { it.visible }) { window, on -> window.commands.tray(on) }

    @Volatile
    private var icon: TrayIcon? = null

    init {
        for (window in listOf(main, altscreen)) {
            window.commands.onMinimized = { minimized -> if (minimized) windows.minimized(window) else windows.restored(window) }
        }
        // No shutdown hook of its own: SystemTray.remove there waits for AWT's event thread, which no longer answers,
        // and the process never ends. AWT's own shutdown (WToolkit) deletes the icon.
    }

    /** Adds or removes the icon, on AWT's thread; waits for it, so a window minimized after this goes to the area. */
    fun enabled(on: Boolean) {
        if (!on && icon == null) return
        EventQueue.invokeAndWait {
            runCatching { if (on) add() else remove() }.onFailure { log("notification area icon not ${if (on) "added" else "removed"}: $it") }
        }
    }

    private fun add() {
        if (icon != null) return
        if (!SystemTray.isSupported()) return log("no notification area: window.tray does nothing")
        val made = TrayIcon(image(), "ChairPlay").apply {
            isImageAutoSize = true
            addMouseListener(object : MouseAdapter() {
                // A double click's second click does nothing more.
                override fun mouseClicked(event: MouseEvent) {
                    if (event.button == MouseEvent.BUTTON1 && event.clickCount == 1) windows.clicked()
                }
            })
        }
        SystemTray.getSystemTray().add(made)
        icon = made
        windows.enabled = true
    }

    private fun remove() {
        windows.enabled = false
        icon?.let { SystemTray.getSystemTray().remove(it) }
        icon = null
    }

    // The window's icons (packaging/icon/make_icon.py): AWT picks the size the display scale needs.
    private fun image() = BaseMultiResolutionImage(*listOf(16, 32, 48).map { size ->
        checkNotNull(AppTray::class.java.getResourceAsStream("/icons/chairplay-$size.png")).use(ImageIO::read)
    }.toTypedArray())
}
