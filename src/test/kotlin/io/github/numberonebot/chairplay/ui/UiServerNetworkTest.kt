package io.github.numberonebot.chairplay.ui

import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Test
import java.net.InetAddress

/**
 * ui.network: a phone opens the settings at this PC's LAN address. The servers here listen on loopback only (the bind
 * address is injected), so no test opens a port to the network or brings up Windows Firewall's prompt; the request
 * names the LAN address as its Host, as a phone's browser does.
 */
class UiServerNetworkTest {
    private val loopback = InetAddress.getLoopbackAddress()
    private val lan = { listOf(InetAddress.getByName("192.168.1.20")) }
    private val servers = mutableListOf<UiServer>()

    private fun server(network: Boolean) =
        UiServer(FakeUiApi(), { null }, network = network, bind = loopback, lan = lan).also { it.start(); servers += it }

    @After
    fun close() = servers.forEach(UiServer::close)

    private fun uiServer(server: UiServer, host: String): Map<*, *> {
        val (status, body) = rawRequest(server.port, "GET", "/api/state", host)
        assertEquals(200, status)
        return (Json.parse(body) as Map<*, *>)["uiServer"] as Map<*, *>
    }

    @Test
    fun `with the network on, the PC's LAN address is a host, and the state lists it`() {
        val server = server(network = true)
        val host = "192.168.1.20:${server.port}"

        val state = uiServer(server, host)
        assertEquals(true, state["network"])
        assertEquals(listOf("http://192.168.1.20:${server.port}/"), state["urls"])
    }

    // Whoever is on the home Wi-Fi knows its password: the eye shows it on any device.
    @Test
    fun `with the network on, another device gets the saved password`() {
        val server = server(network = true)

        val (status, body) = rawRequest(server.port, "GET", "/api/passphrase", "192.168.1.20:${server.port}")
        assertEquals(200, status)
        assertEquals(mapOf("passphrase" to "secret123"), Json.parse(body))
    }

    @Test
    fun `with the network off, the LAN address is refused and nothing is listed`() {
        val server = server(network = false)

        assertEquals(403, rawRequest(server.port, "GET", "/api/state", "192.168.1.20:${server.port}").first)
        val state = uiServer(server, "127.0.0.1:${server.port}")
        assertEquals(false, state["network"])
        assertEquals(emptyList<String>(), state["urls"])
    }
}
