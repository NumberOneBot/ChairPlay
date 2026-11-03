package io.github.numberonebot.chairplay.update

import com.sun.net.httpserver.HttpServer
import org.junit.Assert.*
import org.junit.Test
import java.net.InetSocketAddress
import java.net.URI
import java.time.Duration

class ReleaseClientTest {
    @Test fun `release versions compare numeric components including multi digit versions`() {
        for ((newer, older) in listOf("1.0.10" to "1.0.9", "1.10.0" to "1.9.99", "2.0.0" to "1.99.99")) {
            assertTrue(ReleaseVersion.parse(newer) > ReleaseVersion.parse(older))
        }
        assertEquals(ReleaseVersion.parse("1.0.5"), ReleaseVersion.parse("v1.0.5"))
        for (invalid in listOf("", "1.0", "1.0.6-beta", "garbage", "1.0.5.1", "-1.0.0")) {
            assertThrows(IllegalArgumentException::class.java) { ReleaseVersion.parse(invalid) }
        }
    }

    @Test fun `GitHub response is validated and HTTP failures are not up to date`() {
        val server = HttpServer.create(InetSocketAddress("127.0.0.1", 0), 0)
        var status = 200
        var body = """{"tag_name":"v1.0.6","draft":false,"prerelease":false}"""
        server.createContext("/latest") { exchange ->
            val bytes = body.toByteArray()
            exchange.sendResponseHeaders(status, bytes.size.toLong())
            exchange.responseBody.use { it.write(bytes) }
        }
        server.start()
        try {
            val client = ReleaseClient(URI("http://127.0.0.1:${server.address.port}/latest"))
            assertEquals(ReleaseVersion.parse("1.0.6"), client.latest())
            for (code in listOf(403, 429, 500)) {
                status = code
                assertThrows(Exception::class.java) { client.latest() }
            }
            status = 404
            assertNull(client.latest())
            status = 200
            for (invalid in listOf("not JSON", "{}", """{"tag_name":"v1.0.6","draft":true,"prerelease":false}""",
                """{"tag_name":"v1.0.6-beta","draft":false,"prerelease":true}""")) {
                body = invalid
                assertThrows(Exception::class.java) { client.latest() }
            }
        } finally {
            server.stop(0)
        }
    }

    @Test fun `a stalled release endpoint times out`() {
        val server = HttpServer.create(InetSocketAddress("127.0.0.1", 0), 0)
        server.createContext("/latest") { exchange -> Thread.sleep(300); exchange.close() }
        server.start()
        try {
            val client = ReleaseClient(URI("http://127.0.0.1:${server.address.port}/latest"), Duration.ofMillis(50))
            assertThrows(java.net.http.HttpTimeoutException::class.java) { client.latest() }
        } finally {
            server.stop(0)
        }
    }
}
