package io.github.numberonebot.chairplay.media

import io.github.numberonebot.chairplay.config.Theme

/**
 * The night mode to send the iPhone (AirPlaySession.setNightMode) for [theme], or null to leave it to the
 * iPhone. [systemDark] is asked only for [Theme.SYSTEM]; an unknown answer counts as light.
 */
fun nightMode(theme: Theme, systemDark: () -> Boolean?): Boolean? = when (theme) {
    Theme.LIGHT -> false
    Theme.DARK -> true
    Theme.SYSTEM -> systemDark() ?: false
    Theme.PHONE -> null
}
