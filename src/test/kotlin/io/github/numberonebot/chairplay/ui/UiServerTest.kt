package io.github.numberonebot.chairplay.ui

import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.net.URI
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse

class UiServerTest {
    private val api = FakeUiApi()
    private val assets = mapOf(
        "index.html" to "<!doctype html><title>ChairPlay</title>",
        "assets/app.js" to "console.log(1)",
        "assets/Inter-Regular-abc123.ttf" to "font",
    )
    private val server = UiServer(api, { assets[it]?.toByteArray() }).also { it.start() }
    private val client = HttpClient.newHttpClient()
    private val base = "http://127.0.0.1:${server.port}"

    @After
    fun close() = server.close()

    private fun get(path: String): HttpResponse<String> =
        client.send(HttpRequest.newBuilder(URI("$base$path")).build(), HttpResponse.BodyHandlers.ofString())

    private fun post(path: String, body: String, type: String = "application/json"): HttpResponse<String> {
        val request = HttpRequest.newBuilder(URI("$base$path"))
            .header("Content-Type", type)
            .POST(HttpRequest.BodyPublishers.ofString(body)).build()
        return client.send(request, HttpResponse.BodyHandlers.ofString())
    }

    @Test
    fun `the page's address is plain`() {
        assertEquals("http://127.0.0.1:${server.port}/", server.url)
    }

    @Test
    fun `the state comes from the app as JSON`() {
        val response = get("/api/state")

        assertEquals("application/json; charset=utf-8", response.headers().firstValue("Content-Type").get())
        assertEquals("no-store", response.headers().firstValue("Cache-Control").get())
        assertEquals(mapOf("version" to 0L, "phase" to "idle"), versionAndPhase(response.body()))
    }

    // The state carries the server's own fields too (UiServerPortTest); these tests look at the app's.
    private fun versionAndPhase(json: String): Map<*, *> = (Json.parse(json) as Map<*, *>).filterKeys { it == "version" || it == "phase" }

    // A page with the keyboard hands the app's keys on: the key by name, pressed, repeated or released, and whether
    // every key of the app works over it (the controls window) or only those the settings leave (F11, F12 and such).
    @Test
    fun `a page hands its keys on`() {
        assertEquals(200, post("/api/key", """{"name":"F11","phase":"press","everything":false}""").statusCode())
        assertEquals(404, post("/api/key", """{"name":"Up","phase":"press","everything":false}""").statusCode())
        assertEquals(200, post("/api/key", """{"name":"Up","phase":"press","everything":true}""").statusCode())
        assertEquals(400, post("/api/key", """{"name":"F11"}""").statusCode())
        assertEquals(listOf("key F11 press false", "key Up press false", "key Up press true"), api.calls.takeLast(3))
    }

    // The Audio page's slider: a whole percent.
    @Test fun `browser keyboard focus leases are validated and forwarded`() {
        assertEquals(200, post("/api/keyboard-focus", """{"client":"page","focused":true}""").statusCode())
        assertEquals(200, post("/api/keyboard-focus", """{"client":"page","focused":false}""").statusCode())
        assertEquals(400, post("/api/keyboard-focus", """{"client":"","focused":true}""").statusCode())
        assertEquals(400, post("/api/keyboard-focus", """{"client":"page"}""").statusCode())
        assertEquals(listOf("keyboard focus page true", "keyboard focus page false"), api.calls)
    }

    @Test
    fun `the volume is set from the page`() {
        assertEquals(200, post("/api/volume", """{"level":37}""").statusCode())
        assertEquals(400, post("/api/volume", """{"level":"loud"}""").statusCode())
        assertEquals(listOf("volume 37"), api.calls)
    }

    // GPS experiments: the car driven by a script (its pedals and wheel, each optional), and the CarPlay screen saved
    // to look at where the iPhone puts it.
    @Test
    fun `a script drives the GPS car and saves the CarPlay screen`() {
        assertEquals(200, post("/api/gps-drive", """{"throttle":0.5,"steer":-0.25}""").statusCode())
        assertEquals(200, post("/api/gps-drive", """{}""").statusCode())
        assertEquals(400, post("/api/gps-drive", """{"throttle":"fast"}""").statusCode())
        assertEquals(200, post("/api/screenshot", "{}").statusCode())
        assertEquals(listOf("gpsDrive 0.5 0.0 -0.25", "gpsDrive 0.0 0.0 0.0", "screenshot"), api.calls)
    }

    // The GPS page's Drive mode switch, as F10 turns it.
    @Test
    fun `drive mode is turned on and off from the page`() {
        assertEquals(200, post("/api/drive-mode", """{"on":true}""").statusCode())
        assertEquals(400, post("/api/drive-mode", """{}""").statusCode())
        assertEquals(200, post("/api/drive-mode", """{"on":false}""").statusCode())
        assertEquals(listOf("driveMode true", "driveMode false"), api.calls)
    }

    // The Experiments page's Play and Stop: a demo by name, or none to stop it.
    @Test
    fun `a HUD demo is played and stopped`() {
        assertEquals(200, post("/api/demo", """{"kind":"lanes"}""").statusCode())
        assertEquals(200, post("/api/demo", """{"kind":null}""").statusCode())
        assertEquals(409, post("/api/demo", """{"kind":"tetris"}""").statusCode())
        assertEquals(listOf("demo lanes", "demo null", "demo tetris"), api.calls)
    }

    @Test
    fun `settings are saved or refused with the reasons`() {
        val saved = post("/api/settings", """{"changes":{"window.width":"1600"}}""")
        assertEquals(200, saved.statusCode())
        assertEquals(mapOf("window.width" to "1600"), api.saved)
        assertEquals(mapOf("saved" to listOf("window.width"), "applies" to mapOf("window.width" to "restart")), Json.parse(saved.body()))

        val refused = post("/api/settings", """{"changes":{"window.width":"x"}}""")
        assertEquals(400, refused.statusCode())
        assertEquals(mapOf("errors" to mapOf("window.width" to "bad")), Json.parse(refused.body()))
    }

    @Test
    fun `the key bindings are read and saved, or refused with the reasons`() {
        assertEquals(mapOf("actions" to emptyMap<String, Any>()), Json.parse(get("/api/controls").body()))

        val saved = post("/api/controls", """{"changes":{"key.home":"F6"}}""")
        assertEquals(200, saved.statusCode())
        assertEquals(mapOf("key.home" to "F6"), api.savedControls)

        val refused = post("/api/controls", """{"changes":{"key.home":"Escape"}}""")
        assertEquals(400, refused.statusCode())
        assertEquals(mapOf("errors" to mapOf("key.home" to "Escape always goes back")), Json.parse(refused.body()))

        assertEquals(400, post("/api/controls", """{"changes":{"key.home":1}}""").statusCode())
        api.hasControls = false
        assertEquals(404, get("/api/controls").statusCode())
    }

    @Test
    fun `malformed requests are refused`() {
        assertEquals(400, post("/api/settings", "{").statusCode())
        assertEquals(400, post("/api/settings", """{"changes":{"a":1}}""").statusCode())
        assertEquals(415, post("/api/settings", """{"changes":{}}""", type = "text/plain").statusCode())
        assertEquals(405, get("/api/connect").statusCode())
        assertEquals(404, get("/api/nothing").statusCode())
    }

    @Test
    fun `actions reach the app`() {
        assertEquals(200, post("/api/connect", "{}").statusCode())
        assertEquals(200, post("/api/reconnect", "{}").statusCode())
        assertEquals(200, post("/api/screen", """{"screen":"settings"}""").statusCode())
        assertEquals(409, post("/api/screen", """{"screen":"carplay"}""").statusCode())
        assertEquals(listOf("connect", "reconnect", "show settings", "show carplay"), api.calls)
        assertEquals(200, post("/api/disconnect", "{}").statusCode())
        assertEquals("disconnect", api.calls.removeLast())

        // The settings screen's window triggers.
        assertEquals(200, post("/api/window", """{"name":"controls","visible":false}""").statusCode())
        assertEquals(404, post("/api/window", """{"name":"altscreen","visible":true}""").statusCode())
        assertEquals(400, post("/api/window", """{"name":"controls"}""").statusCode())
        assertEquals(listOf("window controls false", "window altscreen true"), api.calls.drop(4))

        // The controls window's buttons, pressed and released, and its AltScreen modes.
        assertEquals(200, post("/api/control", """{"action":"knob.select","down":true}""").statusCode())
        assertEquals(200, post("/api/control", """{"action":"knob.select","down":false}""").statusCode())
        assertEquals(404, post("/api/control", """{"action":"eject","down":true}""").statusCode())
        assertEquals(400, post("/api/control", """{"action":"knob.select"}""").statusCode())
        assertEquals(200, post("/api/altscreen", """{"content":"hud"}""").statusCode())
        assertEquals(409, post("/api/altscreen", """{"content":"radar"}""").statusCode())
        assertEquals(400, post("/api/altscreen", "{}").statusCode())
        assertEquals(
            listOf("control knob.select true", "control knob.select false", "control eject true", "altscreen hud", "altscreen radar"),
            api.calls.drop(6),
        )

        // A link to a Windows page.
        assertEquals(200, post("/api/open", """{"place":"bluetooth"}""").statusCode())
        assertEquals(404, post("/api/open", """{"place":"cmd.exe"}""").statusCode())
        assertEquals(400, post("/api/open", "{}").statusCode())
        assertEquals(listOf("open bluetooth", "open cmd.exe"), api.calls.drop(11))

        // The AltScreen page's Show frame.
        assertEquals(200, post("/api/altscreen-frame", "{}").statusCode())
        assertEquals(200, post("/api/altscreen-frame", """{"view":"card"}""").statusCode())
        assertTrue("altscreen frame card" in api.calls)
        api.calls.removeAll { it.startsWith("altscreen frame") }

        // Forget this iPhone.
        assertEquals(200, post("/api/forget", "{}").statusCode())
        api.calls.removeAll { it == "forget" }

        // Add the rule, with the consent given, then refused.
        assertEquals(200, post("/api/firewall-rule", "{}").statusCode())
        api.consent = false
        assertEquals(409, post("/api/firewall-rule", "{}").statusCode())
        api.calls.removeAll { it == "firewall rule" }

        // The CarPlay and AltScreen pages' Actual size; a request without a window names the main one.
        assertEquals(200, post("/api/window-size", """{"actual":true}""").statusCode())
        assertEquals(200, post("/api/window-size", """{"actual":true,"window":"altscreen"}""").statusCode())
        assertEquals(400, post("/api/window-size", "{}").statusCode())
        api.mainWindow = false
        assertEquals(404, post("/api/window-size", """{"actual":true}""").statusCode())
        assertEquals(listOf("actual size main", "actual size altscreen", "actual size main"), api.calls.drop(13))

        api.connectError = "Enter the Wi-Fi network first"
        val refused = post("/api/connect", "{}")
        assertEquals(409, refused.statusCode())
        assertEquals(mapOf("error" to "Enter the Wi-Fi network first"), Json.parse(refused.body()))
    }

    @Test
    fun `the built page is served`() {
        val index = get("/")
        assertEquals(200, index.statusCode())
        assertEquals("text/html; charset=utf-8", index.headers().firstValue("Content-Type").get())
        assertTrue(index.headers().firstValue("Content-Security-Policy").isPresent)

        val script = get("/assets/app.js")
        assertEquals("text/javascript; charset=utf-8", script.headers().firstValue("Content-Type").get())
        assertEquals("console.log(1)", script.body())
        assertEquals("font/ttf", get("/assets/Inter-Regular-abc123.ttf").headers().firstValue("Content-Type").get())

        // The page's own routes fall back to it; a missing file does not.
        assertEquals(index.body(), get("/settings").body())
        assertEquals(404, get("/assets/missing.js").statusCode())
        assertEquals(404, get("/%2e%2e/secret.txt").statusCode())
    }

    @Test(timeout = 20_000)
    fun `events carry the state and each change`() {
        val request = HttpRequest.newBuilder(URI("$base/api/events")).build()
        val response = client.send(request, HttpResponse.BodyHandlers.ofLines())
        assertEquals("text/event-stream; charset=utf-8", response.headers().firstValue("Content-Type").get())
        val lines = response.body().iterator()

        val first = lines.next()
        assertTrue(first, first.startsWith("data: "))
        assertEquals(mapOf("version" to 0L, "phase" to "idle"), versionAndPhase(first.removePrefix("data: ")))
        api.change("active")
        val next = generateSequence { lines.next() }.first { it.startsWith("data: ") }
        assertEquals(mapOf("version" to 1L, "phase" to "active"), versionAndPhase(next.removePrefix("data: ")))
    }
}
