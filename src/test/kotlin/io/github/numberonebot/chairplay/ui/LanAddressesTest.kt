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
        val read = { listOf(ip("127.0.0.1"), ip("192.168.1.20"), ip("fe80::1"), ip("100.64.0.5"), ip("fd12::5")) }

        assertEquals(listOf(ip("192.168.1.20"), ip("100.64.0.5")), lanAddresses(read))
    }
}
