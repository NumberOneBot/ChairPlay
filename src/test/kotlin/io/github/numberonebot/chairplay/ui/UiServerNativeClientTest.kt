package io.github.numberonebot.chairplay.ui

import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * A launcher such as ChairPlay Bridge: a native client, no browser, that takes the port from the bridge's status file
 * and sends plain HTTP naming 127.0.0.1 and that port, with JSON bodies.
 */
class UiServerNativeClientTest {
    private val api = FakeUiApi()
    private val server = UiServer(api, { null }).also { it.start() }
    private val host = "127.0.0.1:${server.port}"
    private val json = mapOf("Content-Type" to "application/json")

    @After
    fun close() = server.close()

    private fun post(route: String, body: String) = rawRequest(server.port, "POST", "/api/$route", host, json, body).first

    @Test
    fun `a native client reads the state`() {
        val (status, body) = rawRequest(server.port, "GET", "/api/state", host)
        assertEquals(200, status)
        assertEquals("idle", (Json.parse(body) as Map<*, *>)["phase"])
    }

    @Test
    fun `a native client turns the picture and the telemetry on`() {
        assertEquals(200, post("settings", """{"changes": {"bridge.picture": "true", "bridge.telemetry": "true"}}"""))
        assertEquals(mapOf("bridge.picture" to "true", "bridge.telemetry" to "true"), api.saved)
    }

    @Test
    fun `a native client presses a control and asks for the update check`() {
        assertEquals(200, post("control", """{"action": "knob.select", "down": true}"""))
        assertEquals(200, post("check-updates", "{}"))
        assertTrue(api.calls.toString(), api.calls.containsAll(listOf("control knob.select true", "check updates")))
    }
}
