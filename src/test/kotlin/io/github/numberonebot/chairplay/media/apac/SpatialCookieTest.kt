package io.github.numberonebot.chairplay.media.apac

import org.junit.Assert.*
import org.junit.Test
import java.io.IOException
import java.nio.ByteBuffer

class SpatialCookieTest {
    @Test fun `AAC cookie walks nested MPEG descriptors including four-byte length fields`() {
        assertTrue(isSpatialAacCookie(aacCookie()))
        assertTrue(isSpatialAacCookie(aacCookie(longLengths = true)))
        assertFalse(isSpatialAacCookie(byteArrayOf(0, 0, 0, 33)))
    }

    @Test fun `AAC rejects malformed descriptors and unsupported geometry rather than guessing from SETUP`() {
        for (cookie in listOf(aacCookie().dropLast(1).toByteArray(),
            byteArrayOf(3, -128, -128, -128, -128, 0), aacCookie(asc = byteArrayOf(0x12, 0x10)),
            aacCookie(asc = byteArrayOf(0x11, 0x94.toByte())), aacCookie(objectType = 0x66))) {
            assertThrows(IOException::class.java) { isSpatialAacCookie(cookie) }
        }
    }

    @Test fun `APAC atom traversal respects atom boundaries sync and outer codec`() {
        val dapa = atom("dapa", ByteArray(4) + byteArrayOf(8, 0, 1, 2, 3))
        assertArrayEquals(dapa, apacCookieAtom(dapa))
        val wrapped = atom("frma", "apac".toByteArray()) + atom("apac", ByteArray(4) + dapa)
        assertArrayEquals(dapa, apacCookieAtom(wrapped))
        for (bad in listOf(dapa.dropLast(1).toByteArray(), atom("dapa", ByteArray(4) + byteArrayOf(0, 0, 1)),
            atom("frma", "mp4a".toByteArray()) + dapa, atom("apac", ByteArray(4) + dapa.dropLast(1)))) {
            assertThrows(IOException::class.java) { apacCookieAtom(bad) }
        }
    }

    private fun atom(name: String, bytes: ByteArray) = ByteBuffer.allocate(8).putInt(bytes.size + 8)
        .put(name.toByteArray(Charsets.US_ASCII)).array() + bytes
}

internal fun aacCookie(asc: ByteArray = byteArrayOf(0x11, 0x90.toByte()), longLengths: Boolean = false, objectType: Int = 0x40): ByteArray {
    fun descriptor(tag: Int, data: ByteArray): ByteArray = byteArrayOf(tag.toByte()) +
        (if (longLengths) byteArrayOf(-128, -128, -128, data.size.toByte()) else byteArrayOf(data.size.toByte())) + data
    val decoder = byteArrayOf(objectType.toByte(), 0x15) + ByteArray(11) + descriptor(5, asc)
    return descriptor(3, ByteArray(3) + descriptor(4, decoder) + descriptor(6, byteArrayOf(2)))
}
