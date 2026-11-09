package io.github.numberonebot.chairplay.platform

import io.github.numberonebot.chairplay.network.EthernetAdapter
import io.github.numberonebot.chairplay.network.WlanConnection
import java.net.InetAddress

/** What the system says of this PC's networks: its Wi-Fi, its wired adapters, and the addresses of its physical adapters. */
internal interface PcNetwork {
    /** The Wi-Fi networks the PC is connected to; throws `WifiHiddenException` when the system hides the one it is on. */
    fun wifiConnections(): List<WlanConnection>

    /** The wired adapters that are up and have an IPv4 address, by id. */
    fun ethernetAdapters(): List<EthernetAdapter>

    /** The addresses of the physical adapters that are up, never a virtual switch's; none when the system does not answer. */
    fun physicalAddresses(): List<InetAddress>
}
