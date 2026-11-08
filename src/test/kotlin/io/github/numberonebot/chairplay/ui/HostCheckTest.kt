package io.github.numberonebot.chairplay.ui

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

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
}
