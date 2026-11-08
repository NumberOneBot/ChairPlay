package io.github.numberonebot.chairplay.ui

import java.net.Socket

/**
 * One HTTP/1.1 request over a plain socket to 127.0.0.1:[port], so a test sets `Host` itself (Java's HttpClient sets
 * it from the address). Answers the status and the body; the API answers with a length, never in chunks, so the body
 * is everything after the headers.
 */
internal fun rawRequest(
    port: Int,
    method: String,
    path: String,
    host: String,
    headers: Map<String, String> = emptyMap(),
    body: String = "",
): Pair<Int, String> {
    val bytes = body.toByteArray(Charsets.UTF_8)
    val request = buildString {
        append("$method $path HTTP/1.1\r\nHost: $host\r\nConnection: close\r\n")
        for ((name, value) in headers) append("$name: $value\r\n")
        if (method != "GET") append("Content-Length: ${bytes.size}\r\n")
        append("\r\n")
    }
    Socket("127.0.0.1", port).use { socket ->
        socket.soTimeout = 10_000
        socket.getOutputStream().apply {
            write(request.toByteArray(Charsets.US_ASCII))
            write(bytes)
            flush()
        }
        val answer = socket.getInputStream().readBytes().toString(Charsets.UTF_8)
        val status = answer.substringBefore("\r\n").split(' ')[1].toInt()
        return status to answer.substringAfter("\r\n\r\n")
    }
}
