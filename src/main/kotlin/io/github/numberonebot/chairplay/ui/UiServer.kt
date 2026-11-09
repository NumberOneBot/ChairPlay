package io.github.numberonebot.chairplay.ui

import com.sun.net.httpserver.HttpExchange
import com.sun.net.httpserver.HttpServer
import java.io.Closeable
import java.io.IOException
import java.net.InetAddress
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors

/**
 * The UI's bridge to the app: an HTTP server on 127.0.0.1, or with [network] (ui.network) on every address ([bind]),
 * that serves the built page from [assets] and the JSON API of [api], on the port asked for or, when it is taken or 0,
 * a free one ([port]), at a plain address ([url]) with no secret in it. [HostCheck] stands in for one: a request
 * naming another host than this PC's loopback names, or with [network] its addresses on the network ([lan]), with this
 * port is refused, so a web page cannot reach the API through a name of its own that resolves to this PC. With
 * [network], any client that reaches the port is served. A POST must be JSON, which another site's page cannot send
 * without a CORS preflight, and this server answers none.
 *
 * API: GET state and settings, POST settings ({"changes": {key: value}}), connect, reconnect, screen
 * ({"screen": "start" | "settings" | "carplay"}), window ({"name": "altscreen" | "controls", "visible": bool}), control
 * ({"action": "knob.select", "down": bool}, a button of the controls window), altscreen ({"content": "hud"}) and open
 * ({"place": "bluetooth"}, a Windows page by a name the app knows), open-in-browser (the default browser, by
 * [openBrowser], opens [url] or a listed address), and GET events, a stream of the state on every change.
 */
class UiServer(
    private val api: UiApi,
    private val assets: (String) -> ByteArray?,
    private val requestedPort: Int = 0,
    private val network: Boolean = false,
    bind: InetAddress = uiBindAddress(network),
    lan: (() -> List<InetAddress>)? = null,
    private val openBrowser: (String) -> Unit = {},
    private val log: (String) -> Unit = {},
) : Closeable {
    /** With [network] on and no [lan] given, this PC's addresses, walked on a timer of their own from [start] to [close]. */
    private val ownLan: LanAddresses? = if (network && lan == null) LanAddresses(log = log) else null
    private val addresses: () -> List<InetAddress> = lan ?: ownLan?.let { it::current } ?: { emptyList() }
    private val executor: ExecutorService = Executors.newCachedThreadPool { Thread(it, "ui-http").apply { isDaemon = true } }
    private val server: HttpServer = openHttpServer(requestedPort, bind, log)
    private val hostCheck = HostCheck(server.address.port, if (network) addresses else ({ emptyList() }))
    @Volatile private var closing = false

    /** The port in use: [configuredPort], or a free one when that was taken. */
    val port: Int get() = server.address.port

    /** The port asked for (ui.port); 0 asks for any free one. */
    val configuredPort: Int = requestedPort

    /** The page's address. */
    val url: String get() = "http://127.0.0.1:$port/"

    /** The address people see and a browser opens: localhost, as the Connection page shows it. */
    val browserUrl: String get() = "http://localhost:$port"

    fun start() {
        server.executor = executor
        server.createContext("/") { exchange ->
            try {
                handle(exchange)
            } catch (error: IOException) {
                // The page went away mid-answer.
            } catch (error: Exception) {
                log("UI request ${exchange.requestURI.path} failed: $error")
                runCatching { json(exchange, 500, mapOf("error" to "internal error")) }
            } finally {
                exchange.close()
            }
        }
        ownLan?.start()
        server.start()
    }

    override fun close() {
        closing = true
        ownLan?.close()
        server.stop(0)
        executor.shutdownNow()
    }

    private fun handle(exchange: HttpExchange) {
        exchange.responseHeaders.apply {
            add("X-Content-Type-Options", "nosniff")
            add("Referrer-Policy", "no-referrer")
        }
        if (!hostCheck.allowed(exchange.requestHeaders.getFirst("Host"))) {
            json(exchange, 403, mapOf("error" to "wrong host"))
            return
        }
        val path = exchange.requestURI.path ?: "/"
        if (path.startsWith("/api/")) api(exchange, path.removePrefix("/api/")) else page(exchange, path)
    }

    private fun api(exchange: HttpExchange, route: String) {
        val method = exchange.requestMethod
        val posts = setOf("settings", "controls", "connect", "reconnect", "disconnect", "screen", "window", "control", "key", "keyboard-focus", "altscreen", "open", "window-size", "firewall-rule", "forget", "altscreen-frame", "demo", "volume", "windows-location", "gps-drive", "screenshot", "drive-mode", "check-updates", "open-in-browser")
        val gets = setOf("state", "settings", "passphrase", "controls", "folder-sizes", "events")
        when {
            method == "GET" && route in gets -> when (route) {
                "state" -> json(exchange, 200, state())
                "settings" -> json(exchange, 200, api.settings())
                "passphrase" -> json(exchange, 200, mapOf("passphrase" to api.savedPassphrase()))
                "folder-sizes" -> json(exchange, 200, api.folderSizes())
                "controls" -> api.controls()?.let { json(exchange, 200, it) } ?: json(exchange, 404, mapOf("error" to "no key bindings"))
                else -> events(exchange)
            }
            method == "POST" && route in posts -> post(exchange, route)
            route in posts || route in gets -> json(exchange, 405, mapOf("error" to "method not allowed"))
            else -> json(exchange, 404, mapOf("error" to "no such API"))
        }
    }

    private fun post(exchange: HttpExchange, route: String) {
        val type = exchange.requestHeaders.getFirst("Content-Type").orEmpty()
        if (!type.startsWith("application/json")) {
            json(exchange, 415, mapOf("error" to "send application/json"))
            return
        }
        val bytes = exchange.requestBody.readNBytes(MAX_BODY + 1)
        if (bytes.size > MAX_BODY) {
            json(exchange, 413, mapOf("error" to "request too large"))
            return
        }
        val body = runCatching { Json.parse(bytes.toString(Charsets.UTF_8)) }.getOrNull() as? Map<*, *>
        if (body == null) {
            json(exchange, 400, mapOf("error" to "the body is not a JSON object"))
            return
        }
        val (status, answer) = if (route == "open-in-browser") openInBrowser(body) else postRoute(api, route, body)
        json(exchange, status, answer)
    }

    /**
     * Opens [browserUrl], or the address `url` names ({"url": "http://192.168.1.20:7529"}), in the default browser. The
     * browser gets only an address of these settings: [browserUrl], [url] or one [lanUrls] lists, never a string from
     * the request.
     */
    private fun openInBrowser(body: Map<*, *>): Pair<Int, Map<String, Any?>> {
        val asked = body["url"] ?: browserUrl
        val address = (listOf(browserUrl, url) + lanUrls()).firstOrNull { it == asked }
            ?: return 400 to mapOf("error" to "not an address of these settings")
        return runCatching { openBrowser(address) }.fold(
            onSuccess = { 200 to mapOf("ok" to true) },
            onFailure = { 500 to mapOf("error" to "the browser did not open: $it") },
        )
    }

    /** With ui.network on, the addresses another device opens the settings at; none otherwise. */
    private fun lanUrls(): List<String> = if (network) addresses().map { "http://${it.hostAddress}:$port" } else emptyList()

    /**
     * The app's state, which port this server took (the Connection page says when it is not ui.port's), and with
     * ui.network on, the addresses another device opens it at.
     */
    private fun state(): Map<String, Any?> =
        api.state() + ("uiServer" to mapOf("port" to port, "configuredPort" to configuredPort, "network" to network, "urls" to lanUrls()))

    /** Server-sent events: the state now, then again on every change, with a comment now and then to keep it open. */
    private fun events(exchange: HttpExchange) {
        exchange.responseHeaders.apply {
            add("Content-Type", "text/event-stream; charset=utf-8")
            add("Cache-Control", "no-store")
        }
        exchange.sendResponseHeaders(200, 0)
        val out = exchange.responseBody
        var seen = -1L
        try {
            while (!closing) {
                val version = api.awaitChange(seen, KEEP_ALIVE_MILLIS)
                if (version == seen) {
                    out.write(": keep-alive\n\n".toByteArray())
                } else {
                    seen = version
                    out.write("data: ${Json.write(state())}\n\n".toByteArray(Charsets.UTF_8))
                }
                out.flush()
            }
        } catch (_: InterruptedException) {
            // The server is closing.
        }
    }

    private fun page(exchange: HttpExchange, path: String) {
        if (exchange.requestMethod != "GET" && exchange.requestMethod != "HEAD") {
            text(exchange, 405, "method not allowed")
            return
        }
        val segments = path.split('/').filter { it.isNotEmpty() }
        if (segments.any { it == ".." || it == "." || '\\' in it || ':' in it }) {
            text(exchange, 404, "not found")
            return
        }
        val name = segments.joinToString("/").ifEmpty { INDEX }
        // The page's own routes have no file name extension; they all load the page.
        val file = if ('.' in segments.lastOrNull().orEmpty()) name else INDEX
        val bytes = assets(file)
        if (bytes == null) {
            text(exchange, 404, if (file == INDEX) "The UI was not built into this copy of ChairPlay." else "not found")
            return
        }
        exchange.responseHeaders.apply {
            add("Content-Type", contentType(file))
            // Vite names its bundles after their content, so they never change; the page itself may.
            add("Cache-Control", if (file.startsWith("assets/")) "public, max-age=31536000, immutable" else "no-cache")
            if (file == INDEX) add("Content-Security-Policy", CSP)
        }
        if (exchange.requestMethod == "HEAD") {
            exchange.sendResponseHeaders(200, -1)
            return
        }
        exchange.sendResponseHeaders(200, bytes.size.toLong())
        exchange.responseBody.write(bytes)
    }

    private fun json(exchange: HttpExchange, status: Int, body: Map<String, Any?>) {
        val bytes = Json.write(body).toByteArray(Charsets.UTF_8)
        exchange.responseHeaders.apply {
            add("Content-Type", "application/json; charset=utf-8")
            add("Cache-Control", "no-store")
        }
        exchange.sendResponseHeaders(status, bytes.size.toLong())
        exchange.responseBody.write(bytes)
    }

    private fun text(exchange: HttpExchange, status: Int, body: String) {
        val bytes = body.toByteArray(Charsets.UTF_8)
        exchange.responseHeaders.add("Content-Type", "text/plain; charset=utf-8")
        exchange.sendResponseHeaders(status, bytes.size.toLong())
        exchange.responseBody.write(bytes)
    }

    companion object {
        private const val INDEX = "index.html"
        private const val MAX_BODY = 64 * 1024
        private const val KEEP_ALIVE_MILLIS = 15_000L
        private const val CSP = "default-src 'self'; style-src 'self' 'unsafe-inline'; img-src 'self' data:; " +
            "object-src 'none'; base-uri 'none'; frame-ancestors 'none'"

        private fun contentType(file: String): String = when (file.substringAfterLast('.').lowercase()) {
            "html" -> "text/html; charset=utf-8"
            "js", "mjs" -> "text/javascript; charset=utf-8"
            "css" -> "text/css; charset=utf-8"
            "json", "map" -> "application/json; charset=utf-8"
            "svg" -> "image/svg+xml"
            "png" -> "image/png"
            "ico" -> "image/x-icon"
            "woff2" -> "font/woff2"
            "ttf" -> "font/ttf"
            "txt" -> "text/plain; charset=utf-8"
            else -> "application/octet-stream"
        }
    }
}

/** The built page from the classpath, under /ui/ (the Vite build's output in the jar). */
fun classpathAssets(root: String = "/ui/"): (String) -> ByteArray? = { name ->
    UiServer::class.java.getResourceAsStream(root + name)?.use { it.readBytes() }
}
