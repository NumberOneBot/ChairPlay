package io.github.numberonebot.chairplay.ui

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.net.InetAddress

class RequestChecksTest {
    private val checks = RequestChecks(7529)

    @Test
    fun `loopback names with the server's port pass, in any case`() {
        for (host in listOf("127.0.0.1:7529", "localhost:7529", "LocalHost:7529", "[::1]:7529")) assertTrue(host, checks.hostAllowed(host))
    }

    // A site's own name resolving to 127.0.0.1 (DNS rebinding) sends that name; a missing port is not this server's.
    @Test
    fun `other names, other ports, and no host are refused`() {
        for (host in listOf("evil.example:7529", "127.0.0.1.nip.io:7529", "127.0.0.1", "localhost:80", "127.0.0.2:7529", "[::1]", "", null))
            assertFalse("$host", checks.hostAllowed(host))
    }

    // Another site's page sends its own origin; the page itself sends the address it was opened at.
    @Test
    fun `a POST comes from no page or from the page's own address`() {
        assertTrue(checks.originAllowed(null, "127.0.0.1:7529"))
        assertTrue(checks.originAllowed("http://127.0.0.1:7529", "127.0.0.1:7529"))
        assertTrue(checks.originAllowed("HTTP://LOCALHOST:7529", "localhost:7529"))
        assertTrue(checks.originAllowed("http://[::1]:7529", "[::1]:7529"))
        for (origin in listOf("http://evil.example", "null", "http://localhost:7529", "https://127.0.0.1:7529"))
            assertFalse(origin, checks.originAllowed(origin, "127.0.0.1:7529"))
    }

    @Test
    fun `only this PC gets the saved password`() {
        assertTrue(checks.fromThisPc(InetAddress.getByName("127.0.0.1")))
        assertTrue(checks.fromThisPc(InetAddress.getByName("::1")))
        assertFalse(checks.fromThisPc(InetAddress.getByName("192.168.1.5")))
    }
}
