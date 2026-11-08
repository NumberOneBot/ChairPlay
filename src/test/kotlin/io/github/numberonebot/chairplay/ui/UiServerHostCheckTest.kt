package io.github.numberonebot.chairplay.ui

import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Test

/** What replaced the token: the API answers a request that names this server's own host, and only such a request. */
class UiServerHostCheckTest {
    private val api = FakeUiApi()
    private val server = UiServer(api, { null }).also { it.start() }
    private val host = "127.0.0.1:${server.port}"

    @After
    fun close() = server.close()

    @Test
    fun `the API answers without a token`() {
        assertEquals(200, rawRequest(server.port, "GET", "/api/state", host).first)
    }

    // Another site's page reaching 127.0.0.1 through a name of its own (DNS rebinding) sends its own Host.
    @Test
    fun `requests for another host are refused`() {
        assertEquals(403, rawRequest(server.port, "GET", "/api/state", "evil.example:${server.port}").first)
    }

    // Whoever may open the settings may see the password: the home network knows it anyway.
    @Test
    fun `the saved password answers a request for this host`() {
        val (status, body) = rawRequest(server.port, "GET", "/api/passphrase", host)
        assertEquals(200, status)
        assertEquals(mapOf("passphrase" to "secret123"), Json.parse(body))
    }
}
