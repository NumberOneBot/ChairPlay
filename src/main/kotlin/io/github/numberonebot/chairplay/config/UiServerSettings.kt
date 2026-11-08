package io.github.numberonebot.chairplay.config

import java.util.Properties

/**
 * ui.port: the port of the settings' address in a browser, `http://127.0.0.1:7529/`. When another program has it at
 * start, the app takes a free one for that run (ui/UiBinding.kt). ui.network: the home network may open them too, at
 * this PC's address; read at the start only.
 */
data class UiServerSettings(val port: Int = DEFAULT_PORT, val network: Boolean = false) {
    /** The port the firewall rule opens to the network: ui.port while ui.network is on, none while it is off. */
    val firewallPort: Int? get() = port.takeIf { network }

    companion object {
        /** PLAY on a phone keypad; above 1024, below Windows' dynamic ports, and outside CarPlay's TCP 7000-7010. */
        const val DEFAULT_PORT = 7529
    }
}

internal fun Properties.uiServerSettings(): UiServerSettings {
    val network = booleanSetting("ui.network", false)
    val raw = getProperty("ui.port")?.trim()?.ifEmpty { null } ?: return UiServerSettings(network = network)
    val port = raw.toIntOrNull()
    require(port != null && port in 1..65535) { "ui.port must be a port from 1 to 65535, not $raw" }
    return UiServerSettings(port, network)
}
