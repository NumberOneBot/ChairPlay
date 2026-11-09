package io.github.numberonebot.chairplay.ui

import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Test
import java.net.InetAddress
import java.util.concurrent.CopyOnWriteArrayList

/**
 * ui.network: a phone opens the settings at this PC's LAN address. The servers here listen on loopback only (the bind
 * address is injected), so no test opens a port to the network or brings up Windows Firewall's prompt; the request
 * names the LAN address as its Host, as a phone's browser does.
 */
class UiServerNetworkTest {
    private val loopback = InetAddress.getLoopbackAddress()
    private val lan = { listOf(InetAddress.getByName("192.168.1.20")) }
    private val servers = mutableListOf<UiServer>()
    private val opened = CopyOnWriteArrayList<String>()

    private fun server(network: Boolean) =
        UiServer(FakeUiApi(), { null }, network = network, bind = loopback, lan = lan, openBrowser = { opened += it })
            .also { it.start(); servers += it }

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
        assertEquals(listOf("http://192.168.1.20:${server.port}"), state["urls"])
    }

    // Walking the adapters is slow on a PC with many; the addresses are read once, as the app starts with the network
    // on (the switch itself applies at a restart), never per request or per state.
    @Test
    fun `the PC's addresses are read once, not per request`() {
        var reads = 0
        val server = UiServer(FakeUiApi(), { null }, network = true, bind = loopback, lan = { reads++; lan() })
            .also { it.start(); servers += it }

        repeat(3) { uiServer(server, "192.168.1.20:${server.port}") }

        assertEquals(1, reads)
    }

    // The window's page opens a listed address in the browser; the browser is never handed anything else.
    @Test
    fun `Open in browser opens a listed address, and refuses any other`() {
        val server = server(network = true)
        val open = { body: String ->
            rawRequest(server.port, "POST", "/api/open-in-browser", "127.0.0.1:${server.port}", mapOf("Content-Type" to "application/json"), body).first
        }

        assertEquals(200, open("""{"url":"http://192.168.1.20:${server.port}"}"""))
        assertEquals(200, open("""{"url":"http://localhost:${server.port}"}"""))
        assertEquals(400, open("""{"url":"http://192.168.1.21:${server.port}"}"""))
        assertEquals(400, open("""{"url":"calc.exe"}"""))
        assertEquals(400, open("""{"url":7}"""))
        assertEquals(listOf("http://192.168.1.20:${server.port}", "http://localhost:${server.port}"), opened)
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
