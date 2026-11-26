package io.github.numberonebot.chairplay.config

import java.util.Properties

/** How the desktop windows behave: on top, on a call, in the system tray. None changes the iPhone's session. */
class WindowBehavior(settings: Properties) {
    val mainOnTop = settings.booleanSetting("window.always.on.top", false)
    val altscreenOnTop = settings.booleanSetting("altscreen.always.on.top", false)

    /** window.front.on.call: an incoming call shows the CarPlay window and brings it to the front. */
    val frontOnCall = settings.booleanSetting("window.front.on.call", false)

    /** window.tray: ChairPlay's icon in the system tray, where minimized windows go (AppTray). */
    val tray = settings.booleanSetting("window.tray", false)
}
