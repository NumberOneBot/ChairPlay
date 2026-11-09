package io.github.numberonebot.chairplay.network

class WlanConnection(
    val interfaceDescription: String,
    val ssid: String,
    val bssid: ByteArray,
    val channel: Int,
)

/**
 * Windows hides the network of a connected Wi-Fi adapter: on Windows 11 24H2 and later, desktop apps that may not use
 * location are refused it.
 */
class WifiHiddenException : IllegalStateException("Windows does not share the PC's Wi-Fi network: location is off for desktop apps")
