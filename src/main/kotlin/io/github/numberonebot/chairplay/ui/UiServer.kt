package io.github.numberonebot.chairplay.ui

import com.sun.net.httpserver.HttpExchange
import com.sun.net.httpserver.HttpServer
import java.io.Closeable
import java.io.IOException
import java.net.InetAddress
import java.net.InetSocketAddress
import java.net.URLDecoder
import java.security.MessageDigest
import java.security.SecureRandom
import java.util.Base64
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors

/**
 * The UI's bridge to the app: an HTTP server on 127.0.0.1 that serves the built page from [assets] and the JSON API
 * of [api], on [port] or, with 0, a free one. Every API call carries [token], a random secret the page gets in its
 * own address ([url]), as `Authorization: Bearer` or the `token` query parameter (EventSource cannot set headers).
 * Requests naming another host are refused, so a web page cannot reach the API through a name of its own that
 * resolves to 127.0.0.1. The page itself holds no secret and is served to anyone on this PC.
 *
 * API: GET state and settings, POST settings ({"changes": {key: value}}), connect, reconnect, screen
 * ({"screen": "start" | "settings" | "carplay"}), window ({"name": "altscreen" | "controls", "visible": bool}), control
 * ({"action": "knob.select", "down": bool}, a button of the controls window), altscreen ({"content": "hud"}) and open
 * ({"place": "bluetooth"}, a Windows page by a name the app knows), and GET events, a stream of the state on every
 * change.
 */
class UiServer(
    private val api: UiApi,
    private val assets: (String) -> ByteArray?,
    private val requestedPort: Int = 0,
    private val token: String = newToken(),
    private val log: (String) -> Unit = {},
) : Closeable {
    private val executor: ExecutorService = Executors.newCachedThreadPool { Thread(it, "ui-http").apply { isDaemon = true } }
    private val server: HttpServer = HttpServer.create(InetSocketAddress(InetAddress.getLoopbackAddress(), requestedPort), 0)
    @Volatile private var closing = false

    val port: Int get() = server.address.port

    /** The page's address, with the token. */
    val url: String get() = "http://127.0.0.1:$port/?token=$token"

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
        server.start()
    }

    override fun close() {
        closing = true
        server.stop(0)
        executor.shutdownNow()
    }

    private fun handle(exchange: HttpExchange) {
        exchange.responseHeaders.apply {
            add("X-Content-Type-Options", "nosniff")
            add("Referrer-Policy", "no-referrer")
        }
        val host = exchange.requestHeaders.getFirst("Host")
        if (host != "127.0.0.1:$port" && host != "localhost:$port") {
            json(exchange, 403, mapOf("error" to "wrong host"))
            return
        }
        val path = exchange.requestURI.path ?: "/"
        if (path.startsWith("/api/")) api(exchange, path.removePrefix("/api/")) else page(exchange, path)
    }

    private fun authorized(exchange: HttpExchange): Boolean {
        val header = exchange.requestHeaders.getFirst("Authorization")?.removePrefix("Bearer ")
        val query = exchange.requestURI.rawQuery?.split('&')?.firstNotNullOfOrNull { part ->
            part.split('=', limit = 2).takeIf { it.size == 2 && it[0] == "token" }?.let { URLDecoder.decode(it[1], Charsets.UTF_8) }
        }
        val given = header ?: query ?: return false
        return MessageDigest.isEqual(given.toByteArray(), token.toByteArray())
    }

    private fun api(exchange: HttpExchange, route: String) {
        if (!authorized(exchange)) {
            json(exchange, 401, mapOf("error" to "missing or wrong token"))
            return
        }
        val method = exchange.requestMethod
        val posts = setOf("settings", "controls", "connect", "reconnect", "disconnect", "screen", "window", "control", "key", "keyboard-focus", "altscreen", "open", "window-size", "firewall-rule", "forget", "altscreen-frame", "demo", "volume", "windows-location", "gps-drive", "screenshot", "drive-mode", "check-updates")
        val gets = setOf("state", "settings", "passphrase", "controls", "folder-sizes", "events")
        when {
            method == "GET" && route in gets -> when (route) {
                "state" -> json(exchange, 200, api.state())
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
        val (status, answer) = postRoute(api, route, body)
        json(exchange, status, answer)
    }

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
                    out.write("data: ${Json.write(api.state())}\n\n".toByteArray(Charsets.UTF_8))
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

        /** 32 random bytes in URL-safe Base64. */
        fun newToken(): String {
            val bytes = ByteArray(32).also(SecureRandom()::nextBytes)
            return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes)
        }

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
