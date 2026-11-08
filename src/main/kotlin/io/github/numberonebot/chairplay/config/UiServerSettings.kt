package io.github.numberonebot.chairplay.config

import java.util.Properties

/**
 * ui.port: the port of the settings' address in a browser, `http://127.0.0.1:7529/`. When another program has it at
 * start, the app takes a free one for that run (ui/UiBinding.kt).
 */
data class UiServerSettings(val port: Int = DEFAULT_PORT) {
    companion object {
        /** PLAY on a phone keypad; above 1024, below Windows' dynamic ports, and outside CarPlay's TCP 7000-7010. */
        const val DEFAULT_PORT = 7529
    }
}

internal fun Properties.uiServerSettings(): UiServerSettings {
    val raw = getProperty("ui.port")?.trim()?.ifEmpty { null } ?: return UiServerSettings()
    val port = raw.toIntOrNull()
    require(port != null && port in 1..65535) { "ui.port must be a port from 1 to 65535, not $raw" }
    return UiServerSettings(port)
}
