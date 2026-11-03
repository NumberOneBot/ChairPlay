package io.github.numberonebot.chairplay.media.apac

import org.junit.Assert.*
import org.junit.Test
import java.io.IOException

class ApapCookiesTest {
    @Test fun `first cookie wins and stored bytes cannot be changed by the caller`() {
        val registry = ApapCookies()
        val cookie = byteArrayOf(1, 2)
        registry.remember(7, cookie)
        cookie[0] = 9
        registry.remember(7, byteArrayOf(3, 4))
        val resolved = registry.resolve(ApapMetadata(cookieId = 7))!!
        assertArrayEquals(byteArrayOf(1, 2), resolved)
        resolved[0] = 8
        assertArrayEquals(byteArrayOf(1, 2), registry.resolve(ApapMetadata(cookieId = 7)))
    }

    @Test fun `inline cookie is authoritative without replacing a stored id`() {
        val registry = ApapCookies()
        registry.remember(1, byteArrayOf(23))
        assertArrayEquals(byteArrayOf(65), registry.resolve(ApapMetadata(cookieId = 1, cookie = byteArrayOf(65))))
        assertArrayEquals(byteArrayOf(23), registry.resolve(ApapMetadata(cookieId = 1)))
        assertThrows(IOException::class.java) { registry.resolve(ApapMetadata(cookieId = 2)) }
        assertNull(registry.resolve(ApapMetadata()))
    }

    @Test fun `registry rejects excess data and clears on session teardown`() {
        val registry = ApapCookies()
        assertThrows(IOException::class.java) { registry.remember(0, byteArrayOf()) }
        assertThrows(IOException::class.java) { registry.remember(0, ByteArray(65537)) }
        repeat(16) { registry.remember(it.toLong(), ByteArray(65536) { 1 }) }
        registry.remember(0, byteArrayOf(2))
        assertThrows(IOException::class.java) { registry.remember(16, byteArrayOf(1)) }
        registry.clear()
        assertThrows(IOException::class.java) { registry.resolve(ApapMetadata(cookieId = 0)) }
        registry.remember(16, byteArrayOf(65))
        assertArrayEquals(byteArrayOf(65), registry.resolve(ApapMetadata(cookieId = 16)))
    }
}
