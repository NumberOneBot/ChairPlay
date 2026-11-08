package io.github.numberonebot.chairplay.ui

import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Test

/** What replaced the token: the API answers its own host without a key, and refuses other sites' pages. */
class UiServerChecksTest {
    private val api = FakeUiApi()
    private val server = UiServer(api, { null }).also { it.start() }
    private val host = "127.0.0.1:${server.port}"

    @After
    fun close() = server.close()

    @Test
    fun `the API answers without a token`() {
        assertEquals(200, rawRequest(server.port, "GET", "/api/state", host).first)
    }

    // A web page the user visits may POST to 127.0.0.1; the browser names that page's site in Origin.
    @Test
    fun `a POST from another site's page is refused`() {
        val json = mapOf("Content-Type" to "application/json")
        val (refused, _) = rawRequest(server.port, "POST", "/api/connect", host, json + ("Origin" to "http://evil.example"), "{}")
        assertEquals(403, refused)
        assertEquals(emptyList<String>(), api.calls)

        val (accepted, _) = rawRequest(server.port, "POST", "/api/connect", host, json + ("Origin" to "http://$host"), "{}")
        assertEquals(200, accepted)
        assertEquals(listOf("connect"), api.calls)
    }

    @Test
    fun `the saved password answers on this PC`() {
        val (status, body) = rawRequest(server.port, "GET", "/api/passphrase", host)
        assertEquals(200, status)
        assertEquals(mapOf("passphrase" to "secret123"), Json.parse(body))
    }
}
