package io.github.numberonebot.chairplay.ui

import io.github.numberonebot.chairplay.network.physicalInterfaceAddresses
import java.net.Inet4Address
import java.net.InetAddress

/**
 * This PC's addresses that another device opens the settings at with ui.network on: its physical adapters' IPv4
 * addresses, in any range, never a virtual switch's (Hyper-V's or WSL's vEthernet), which no other device reaches (a
 * browser takes an IPv6 link-local address only with a zone id, so the page lists IPv4 ones only).
 * [read] walks the adapters, which is slow, and the Host check asks on every request: the list is read again only
 * [ttlMillis] after the last read, so an address the PC gets from a new network counts within that time.
 */
internal class LanAddresses(
    private val read: () -> List<InetAddress> = ::physicalInterfaceAddresses,
    private val now: () -> Long = { System.nanoTime() / 1_000_000 },
    private val ttlMillis: Long = 10_000,
) {
    private var cached: List<InetAddress>? = null
    private var readAt = 0L

    @Synchronized
    fun current(): List<InetAddress> {
        val time = now()
        cached?.let { if (time - readAt <= ttlMillis) return it }
        return read().filter { it is Inet4Address && !it.isLoopbackAddress }.also {
            cached = it
            readAt = time
        }
    }
}
