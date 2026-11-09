package io.github.numberonebot.chairplay.ui

import io.github.numberonebot.chairplay.network.physicalInterfaceAddresses
import java.net.Inet4Address
import java.net.InetAddress

/**
 * This PC's addresses that another device opens the settings at with ui.network on: its physical adapters' IPv4
 * addresses, in any range, never a virtual switch's (Hyper-V's or WSL's vEthernet), which no other device reaches (a
 * browser takes an IPv6 link-local address only with a zone id, so the page lists IPv4 ones only). UiServer reads them
 * once, as the app starts: an address from a network joined later counts after a restart, as the switch itself does.
 */
internal fun lanAddresses(read: () -> List<InetAddress> = ::physicalInterfaceAddresses): List<InetAddress> =
    read().filter { it is Inet4Address && !it.isLoopbackAddress }
