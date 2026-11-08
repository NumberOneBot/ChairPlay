package io.github.numberonebot.chairplay.ui

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.net.InetAddress

class HostCheckTest {
    private val check = HostCheck(7529)

    @Test
    fun `loopback names with the server's port pass, in any case`() {
        for (host in listOf("127.0.0.1:7529", "localhost:7529", "LocalHost:7529", "[::1]:7529")) assertTrue(host, check.allowed(host))
    }

    // A site's own name resolving to 127.0.0.1 (DNS rebinding) sends that name; a missing port is not this server's.
    @Test
    fun `other names, other ports, and no host are refused`() {
        for (host in listOf("evil.example:7529", "127.0.0.1.nip.io:7529", "127.0.0.1", "localhost:80", "127.0.0.2:7529", "[::1]", "", null))
            assertFalse("$host", check.allowed(host))
    }

    private val lan = HostCheck(7529) { listOf(InetAddress.getByName("192.168.1.20"), InetAddress.getByName("10.0.0.7")) }

    // With ui.network on, a phone opens http://<this PC's address>:7529/; a name is never looked up.
    @Test
    fun `this PC's own addresses with the port pass while the network is on`() {
        for (host in listOf("192.168.1.20:7529", "10.0.0.7:7529", "localhost:7529")) assertTrue(host, lan.allowed(host))
        for (host in listOf("192.168.1.21:7529", "192.168.1.20:80", "192.168.1.20", "printer.local:7529", "192.168.1.20.nip.io:7529"))
            assertFalse(host, lan.allowed(host))
    }

    @Test
    fun `with the network off they do not`() {
        assertFalse(check.allowed("192.168.1.20:7529"))
    }
}
