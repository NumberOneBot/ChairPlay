package io.github.numberonebot.chairplay.config

import java.util.Properties

/** Independent desktop window preferences; none changes the iPhone's session. */
class WindowOnTop(settings: Properties) {
    val main = settings.booleanSetting("window.always.on.top", false)
    val altscreen = settings.booleanSetting("altscreen.always.on.top", false)

    /** window.front.on.call: an incoming call shows the CarPlay window and brings it to the front. */
    val frontOnCall = settings.booleanSetting("window.front.on.call", false)
}
