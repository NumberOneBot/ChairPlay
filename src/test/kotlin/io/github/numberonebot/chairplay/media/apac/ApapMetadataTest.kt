package io.github.numberonebot.chairplay.media.apac

import org.junit.Assert.*
import org.junit.Test
import java.io.IOException
import java.nio.ByteBuffer

class ApapMetadataTest {
    private fun packet(data: ByteArray, extension: Boolean = true) =
        ApapPacket(ApapMediaTime(-1024, 48000), 0xffffff, extension, data)
    private fun tlv(id: Int, data: ByteArray) = byteArrayOf(id.toByte()) + leb(data.size) + data
    private fun leb(value: Int): ByteArray {
        var v = value
        val out = ArrayList<Byte>()
        do { out.add(((v and 127) or if (v > 127) 128 else 0).toByte()); v = v ushr 7 } while (v != 0)
        return out.toByteArray()
    }
    private fun time(value: Long, scale: Int) = ByteBuffer.allocate(12).putLong(value).putInt(scale).array()

    @Test fun `extension identifies codec geometry cookies and trims without consuming access unit bytes`() {
        val cookie = byteArrayOf(1, 2, 3)
        val audio = byteArrayOf(0, 2, 8, 18, -1)
        val data = tlv(2, byteArrayOf(65)) + tlv(8, byteArrayOf(4, 0)) +
            tlv(17, ByteArray(8) { -1 }) + tlv(18, cookie) + tlv(4, time(1, 48000)) +
            tlv(5, time(1, 1000)) + tlv(20, byteArrayOf(35)) + byteArrayOf(0) + audio
        val unit = parseApap(packet(data))
        assertEquals(65, unit.metadata.formatIndex)
        assertEquals(1024, unit.metadata.samplesPerFrame)
        assertEquals(-1L, unit.metadata.cookieId)
        assertEquals(35, unit.metadata.sourceFormatIndex)
        assertEquals(ApapMediaTime(1, 48000), unit.metadata.trimStart)
        assertEquals(ApapMediaTime(1, 1000), unit.metadata.trimEnd)
        assertArrayEquals(cookie, unit.metadata.cookie)
        assertArrayEquals(audio, unit.audio)
        assertEquals(ApapMediaTime(-1024, 48000), unit.time)
        assertEquals(0xffffff, unit.sequence)
    }

    @Test fun `plain audio is never guessed to contain TLVs`() {
        val audio = tlv(2, byteArrayOf(65)) + byteArrayOf(0)
        val unit = parseApap(packet(audio, false))
        assertNull(unit.metadata.formatIndex)
        assertArrayEquals(audio, unit.audio)
    }

    @Test fun `unknown extensions are skipped including multibyte ids and lengths`() {
        val data = leb(300) + leb(300) + ByteArray(300) + tlv(8, byteArrayOf(4, 0)) + byteArrayOf(0, 9)
        val unit = parseApap(packet(data))
        assertEquals(1, unit.metadata.unknownFields)
        assertEquals(1024, unit.metadata.samplesPerFrame)
        assertArrayEquals(byteArrayOf(9), unit.audio)
    }

    @Test fun `malformed prefixes never produce an access unit`() {
        val invalid = listOf(byteArrayOf(), byteArrayOf(2), byteArrayOf(2, 5, 65, 0),
            byteArrayOf(2, -128), ByteArray(10) { -128 }, ByteArray(9) { -1 } + byteArrayOf(2, 0),
            byteArrayOf(2, 1, 65), byteArrayOf(18, 0, 0), byteArrayOf(17, 9) + ByteArray(10),
            byteArrayOf(2, 0, 0), byteArrayOf(8, 9) + ByteArray(10), tlv(4, ByteArray(11)) + byteArrayOf(0),
            tlv(11, ByteArray(11)) + byteArrayOf(0), tlv(7, ByteArray(21)) + byteArrayOf(0),
            tlv(12, ByteArray(2)) + byteArrayOf(0), tlv(18, ByteArray(65537)) + byteArrayOf(0))
        for (data in invalid) assertThrows(IOException::class.java) { parseApap(packet(data)) }
    }

    @Test fun `nonfinite loudness never enters the gain pipeline`() {
        for (value in listOf(Float.NaN, Float.POSITIVE_INFINITY, Float.NEGATIVE_INFINITY)) {
            val loudness = ByteBuffer.allocate(12).putFloat(-18f).putFloat(-1f).putFloat(value).array()
            assertThrows(IOException::class.java) { parseApap(packet(tlv(11, loudness) + byteArrayOf(0))) }
        }
        val loudness = ByteBuffer.allocate(12).putFloat(-18f).putFloat(-1.8125f).putFloat(-1.78125f).array()
        val metadata = parseApap(packet(tlv(11, loudness) + tlv(12, byteArrayOf(1)) + byteArrayOf(0))).metadata
        assertEquals(listOf(-18f, -1.8125f, -1.78125f), metadata.loudness)
        assertEquals(1, metadata.mediaKind)
    }

    @Test fun `trim clamps to one access unit without overflowing and invalid time trims nothing`() {
        assertEquals(48, ApapMediaTime(1, 1000).trimFrames(48000, 1024))
        assertEquals(0, ApapMediaTime(1, 96000).trimFrames(48000, 1024))
        assertEquals(1024, ApapMediaTime(Long.MAX_VALUE, 1).trimFrames(48000, 1024))
        assertEquals(1024, ApapMediaTime(Long.MAX_VALUE, Int.MAX_VALUE).trimFrames(48000, 1024))
        assertEquals(0, ApapMediaTime(-1, 48000).trimFrames(48000, 1024))
        assertEquals(0, ApapMediaTime(1, 0).trimFrames(48000, 1024))
        assertEquals(0, ApapMediaTime(1, -1).trimFrames(48000, 1024))
    }
}
