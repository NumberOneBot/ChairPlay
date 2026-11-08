package io.github.numberonebot.chairplay.ui

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.net.InetAddress
import java.net.ServerSocket

/** ui.port: the port asked for, or a free one for this run when another program has it. */
class UiBindingTest {
    private val logged = mutableListOf<String>()

    @Test fun `a free port asked for is taken`() {
        val free = ServerSocket(0).use { it.localPort }
        val server = openHttpServer(free, InetAddress.getLoopbackAddress()) { logged += it }
        try {
            assertEquals(free, server.address.port)
            assertTrue(logged.toString(), logged.isEmpty())
        } finally {
            server.stop(0)
        }
    }

    @Test fun `a busy port gives way to a free one, and the log says so`() {
        ServerSocket(0, 50, InetAddress.getLoopbackAddress()).use { busy ->
            val server = openHttpServer(busy.localPort, InetAddress.getLoopbackAddress()) { logged += it }
            try {
                assertNotEquals(busy.localPort, server.address.port)
                val line = logged.single()
                assertTrue(line, "${busy.localPort}" in line && "${server.address.port}" in line)
            } finally {
                server.stop(0)
            }
        }
    }
}
