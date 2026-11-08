package io.github.numberonebot.chairplay.ui

import org.junit.Assert.assertEquals
import org.junit.Test
import java.net.InetAddress

/** This PC's addresses that another device on its network opens the settings at, with ui.network on. */
class LanAddressesTest {
    private fun ip(text: String): InetAddress = InetAddress.getByName(text)

    // A browser takes an IPv6 link-local address only with a zone id; the page lists IPv4 addresses, in any range.
    @Test
    fun `keeps the IPv4 addresses but loopback`() {
        val addresses = LanAddresses(read = { listOf(ip("127.0.0.1"), ip("192.168.1.20"), ip("fe80::1"), ip("100.64.0.5"), ip("fd12::5")) })

        assertEquals(listOf(ip("192.168.1.20"), ip("100.64.0.5")), addresses.current())
    }

    // The Host check asks on every request; walking the adapters is slow.
    @Test
    fun `reads again only after ten seconds`() {
        var reads = 0
        var now = 0L
        val addresses = LanAddresses(read = { reads++; listOf(ip("192.168.1.20")) }, now = { now })

        addresses.current()
        now = 9_999
        addresses.current()
        assertEquals(1, reads)
        now = 10_001
        addresses.current()
        assertEquals(2, reads)
    }
}
