package io.github.numberonebot.chairplay.config

import java.util.Properties

/** Independent desktop window preferences; neither changes the iPhone's session. */
class WindowOnTop(settings: Properties) {
    val main = settings.booleanSetting("window.always.on.top", false)
    val altscreen = settings.booleanSetting("altscreen.always.on.top", false)
}
