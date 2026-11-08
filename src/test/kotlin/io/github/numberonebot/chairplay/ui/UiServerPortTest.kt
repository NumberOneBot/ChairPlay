package io.github.numberonebot.chairplay.ui

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Test
import java.net.InetAddress
import java.net.ServerSocket

/** ui.port taken at start: the app serves on a free port, and its address and the page's state say which. */
class UiServerPortTest {
    @Test fun `the address and the state follow the port in use`() {
        ServerSocket(0, 50, InetAddress.getLoopbackAddress()).use { busy ->
            val server = UiServer(FakeUiApi(), { null }, requestedPort = busy.localPort).also { it.start() }
            try {
                assertNotEquals(busy.localPort, server.port)
                assertEquals("http://127.0.0.1:${server.port}/", server.url)
                assertEquals(busy.localPort, server.configuredPort)

                val (status, body) = rawRequest(server.port, "GET", "/api/state", "127.0.0.1:${server.port}")
                assertEquals(200, status)
                val uiServer = (Json.parse(body) as Map<*, *>)["uiServer"] as Map<*, *>
                assertEquals(server.port.toLong(), uiServer["port"])
                assertEquals(busy.localPort.toLong(), uiServer["configuredPort"])
            } finally {
                server.close()
            }
        }
    }
}
