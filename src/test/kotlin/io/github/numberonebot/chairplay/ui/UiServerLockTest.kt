package io.github.numberonebot.chairplay.ui

import io.github.numberonebot.chairplay.ui.SettingsLock.Companion.LOCKED_ROUTES
import io.github.numberonebot.chairplay.ui.SettingsLock.Companion.WINDOW
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.IOException
import java.net.URI
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse
import java.util.concurrent.CopyOnWriteArrayList
import java.util.stream.Stream

/** The settings lock as the server applies it: each page's view of the state, the refused POSTs, the streams. */
class UiServerLockTest {
    private val api = FakeUiApi()
    @Volatile private var shows = true
    @Volatile private var now = 0L
    private val lock = SettingsLock({ shows }, api::bump, { now })
    private val opened = CopyOnWriteArrayList<String>()
    @Volatile private var browserFails = false
    private val server = UiServer(api, { null }, lock = lock, openBrowser = { url ->
        if (browserFails) throw IOException("no browser")
        opened += url
    }).also { it.start() }
    private val host = "127.0.0.1:${server.port}"
    private val client = HttpClient.newHttpClient()

    init {
        lock.update()
    }

    @After
    fun close() = server.close()

    private fun show(value: Boolean) {
        shows = value
        lock.update()
    }

    private fun named(page: String?) = if (page == null) emptyMap() else mapOf("X-ChairPlay-Page" to page)

    private fun post(route: String, page: String?, body: String = "{}"): Int =
        rawRequest(server.port, "POST", "/api/$route", host, named(page) + ("Content-Type" to "application/json"), body).first

    private fun state(page: String?): Map<*, *> = Json.parse(rawRequest(server.port, "GET", "/api/state", host, named(page)).second) as Map<*, *>

    private fun lockOf(page: String?) = state(page)["lock"] as Map<*, *>

    private fun stream(query: String): Stream<String> =
        client.send(HttpRequest.newBuilder(URI("http://$host/api/events?$query")).build(), HttpResponse.BodyHandlers.ofLines()).body()

    private fun firstState(lines: Stream<String>): Map<*, *> =
        Json.parse(lines.iterator().asSequence().first { it.startsWith("data: ") }.removePrefix("data: ")) as Map<*, *>

    private fun waitFor(what: String, condition: () -> Boolean) {
        val deadline = System.nanoTime() + 10_000_000_000L
        while (!condition()) {
            assertTrue(what, System.nanoTime() < deadline)
            Thread.sleep(200)
        }
    }

    private val edit = """{"changes":{"ui.autoshow":"true"}}"""

    @Test
    fun `a POST that edits settings from a page without them is refused`() {
        assertEquals(409, post("settings", "tab-1", edit))
        assertNull(api.saved)
        assertEquals(200, post("settings", WINDOW, edit))
    }

    @Test
    fun `the window saves while CarPlay covers it, but not over a tab or a hand-over`() {
        show(false)
        assertEquals(200, post("settings", WINDOW, edit))
        lock.open("tab-1", true)
        assertEquals(409, post("settings", WINDOW, edit))
        lock.leave("tab-1")
        lock.handOver()
        assertEquals(409, post("settings", WINDOW, edit))
    }

    @Test
    fun `every route that edits settings is refused to a page without them`() {
        for (route in LOCKED_ROUTES) assertEquals(route, 409, post(route, "tab-1"))
        assertEquals(emptyList<String>(), api.calls)
    }

    @Test
    fun `app control works from any page`() {
        post("connect", "tab-1")
        post("screen", "tab-1", """{"screen":"carplay"}""")
        post("key", "tab-1", """{"name":"F11","phase":"press","everything":false}""")
        post("control", "tab-1", """{"action":"knob.select","down":true}""")
        post("window", "tab-1", """{"name":"controls","visible":true}""")
        post("volume", "tab-1", """{"level":10}""")
        assertEquals(listOf("connect", "show carplay", "key F11 press false", "control knob.select true",
            "window controls true", "volume 10"), api.calls)
    }

    @Test
    fun `a shared page and a request without a page may write while the window holds them`() {
        assertEquals(200, post("settings", "shared", edit))
        assertEquals(200, post("settings", null, edit))
    }

    @Test
    fun `each page hears its own place`() {
        api.embedded = true
        api.waiting = true

        val window = state(WINDOW)
        assertEquals(true, window["embedded"])
        assertEquals(true, window["waiting"])
        assertEquals(true, (window["lock"] as Map<*, *>)["mine"])
        val tab = state("tab-1")
        assertEquals(false, tab["embedded"])
        assertEquals(false, tab["waiting"])
        assertEquals(mapOf("mine" to false, "holder" to "window", "shared" to false, "handOver" to false), tab["lock"])
    }

    @Test(timeout = 20_000)
    fun `a tab's stream makes it the holder, and its end lets go`() {
        show(false)
        val lines = stream("page=tab-1&visible=1")
        assertEquals(true, (firstState(lines)["lock"] as Map<*, *>)["mine"])

        lines.close()
        waitFor("the tab let go") { lockOf(WINDOW)["holder"] == null }
    }

    // The heartbeat runs out with the stream still open: the stream's own tick notices.
    @Test(timeout = 20_000)
    fun `a tab that stops reporting lets go at its stream's next tick`() {
        show(false)
        val lines = stream("page=tab-1&visible=1")
        firstState(lines)

        now += SettingsLock.PRESENCE_MILLIS + 1_000
        waitFor("the tab let go") { lockOf(WINDOW)["holder"] == null }
        lines.close()
    }

    // Review Focus 2: the window holds by the app's state, never by a stream of its own.
    @Test(timeout = 20_000)
    fun `the window's stream ending changes nothing`() {
        val lines = stream("page=window&visible=1")
        firstState(lines)
        lines.close()

        repeat(15) {
            assertEquals("window", lockOf(WINDOW)["holder"])
            Thread.sleep(200)
        }
    }

    @Test(timeout = 20_000)
    fun `the page route takes visibility and closing`() {
        show(false)
        val lines = stream("page=tab-1&visible=1")
        firstState(lines)

        assertEquals(200, post("page", "tab-1", """{"visible":false}"""))
        assertEquals(false, lockOf("tab-1")["mine"])
        assertEquals(200, post("page", "tab-1", """{"visible":true}"""))
        assertEquals(true, lockOf("tab-1")["mine"])
        assertEquals(200, post("page", "tab-1", """{"visible":false,"closing":true}"""))
        assertEquals(false, lockOf("tab-1")["mine"])
        assertEquals(400, post("page", null, """{"visible":true}"""))
        assertEquals(400, post("page", "tab-1", "{}"))
        lines.close()
    }

    @Test
    fun `Open in browser opens the plain address with the hand-over key`() {
        assertEquals(200, post("open-in-browser", WINDOW))
        assertTrue(opened.single(), Regex("""http://127\.0\.0\.1:${server.port}/\?handover=.{36}""").matches(opened.single()))
        assertEquals(409, post("open-in-browser", "tab-1"))

        lock.cancelHandOver()
        browserFails = true
        assertEquals(409, post("open-in-browser", WINDOW))
        assertEquals(false, lock.view(WINDOW)["handOver"])
        assertEquals(true, lock.view(WINDOW)["mine"])
    }

    @Test
    fun `Open in browser hands over only while the window holds the settings`() {
        show(false)
        assertEquals(409, post("open-in-browser", WINDOW))
        assertEquals(emptyList<String>(), opened)
        assertFalse(lock.view(WINDOW)["handOver"] as Boolean)
    }

    @Test
    fun `a malformed page id is refused`() {
        assertEquals(400, rawRequest(server.port, "GET", "/api/state", host, mapOf("X-ChairPlay-Page" to "a b")).first)
        assertEquals(400, rawRequest(server.port, "GET", "/api/events?page=%20", host).first)
    }
}
