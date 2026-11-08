package io.github.numberonebot.chairplay.media.apac

import com.shilapi.xcertplay.airplay.AirPlayCrypto
import io.github.numberonebot.chairplay.media.AacPcmDecoder
import io.github.numberonebot.chairplay.media.encodeAac
import org.junit.Assert.*
import org.junit.Test
import java.io.ByteArrayInputStream
import java.io.DataInputStream
import java.io.IOException
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.util.concurrent.CopyOnWriteArrayList

// Seen with YouTube on iOS: a buffered AAC-LC stream whose iPhone app sends no cookie at all.
class ApapFallbackCookieTest {
    private val key = ByteArray(32) { it.toByte() }
    private val real = aacCookie(longLengths = true)

    @Test fun `an AAC-LC stream with no cookie plays its first unit through the AAC decoder`() {
        val audio = encodeAac(48000, 2, 3).first()
        val expected = AacPcmDecoder(48000, 2).use { it.decode(audio) }.let { bytes ->
            ByteBuffer.wrap(bytes).order(ByteOrder.LITTLE_ENDIAN).asShortBuffer().let { b -> ShortArray(b.remaining()).also(b::get) }
        }
        val device = TestSpatialDevice()
        val worker = SpatialWorker({ device }, { SpatialDecoder(null, it) }, { 1f }, { 1f }, {}, emptyMap(), startDelayNs = 0)
        val lines = CopyOnWriteArrayList<String>()
        ApapStreamSource(key, worker, lines::add, spatialSetup(setup(23)).audioFormat).use { source ->
            val input = input(packet(0, audio), packet(1, audio))
            val first = source.read(input).apap!!
            assertTrue(isSpatialAacCookie(first.metadata.cookie!!))
            source.read(input)
            assertEquals(1, lines.size) // Said once per stream, not per unit.
            source.begin()
            source.push(first)
            await { device.samples.isNotEmpty() }
            assertArrayEquals(expected, device.samples.first())
        }
    }

    @Test fun `a SETMAGICCOOKIE after the fallback gives later units its cookie`() {
        source(23).use { source ->
            val input = input(packet(0), packet(1), packet(2))
            source.read(input)
            source.remember(mapOf("magicCookieID" to 7L, "magicCookie" to real))
            assertArrayEquals(real, source.read(input).apap!!.metadata.cookie)
            assertArrayEquals(real, source.read(input).apap!!.metadata.cookie)
        }
    }

    @Test fun `an in-band cookie after the fallback replaces it`() {
        source(23).use { source ->
            val input = input(packet(0), packet(1, cookie = real), packet(2))
            val fallback = source.read(input).apap!!.metadata.cookie
            assertFalse(real.contentEquals(fallback))
            assertArrayEquals(real, source.read(input).apap!!.metadata.cookie)
            assertArrayEquals(real, source.read(input).apap!!.metadata.cookie)
        }
    }

    @Test fun `a stream of another format with no cookie still fails until a cookie comes`() {
        val lines = CopyOnWriteArrayList<String>()
        source(65, lines::add).use { source ->
            val input = input(packet(0), packet(1))
            assertThrows(IOException::class.java) { source.read(input) }
            source.remember(mapOf("magicCookieID" to 7L, "magicCookie" to real))
            assertArrayEquals(real, source.read(input).apap!!.metadata.cookie)
            assertTrue(lines.isEmpty())
        }
    }

    private fun source(index: Int, log: (String) -> Unit = {}) = ApapStreamSource(key,
        SpatialWorker({ TestSpatialDevice() }, { SpatialDecoder(null, it) }, { 1f }, { 1f }, {}, emptyMap()),
        log, spatialSetup(setup(index)).audioFormat)

    private fun setup(index: Int): Map<String, Any?> = mapOf("audioFormatIndex" to index.toLong(),
        "audioFormat" to if (index == 23) 0x800000L else 0L, "packetFormatAPAP" to true,
        "spf" to 1024L, "shk" to ByteArray(32), "audioType" to "media", "isMedia" to true)

    private fun input(vararg packets: ByteArray) = DataInputStream(ByteArrayInputStream(packets.reduce(ByteArray::plus)))

    private fun packet(index: Int, audio: ByteArray = byteArrayOf(1), cookie: ByteArray? = null): ByteArray {
        val header = ByteBuffer.allocate(15).putLong(index * 1024L).putInt(48000).put(0).put(0).put(index.toByte()).array()
        val extensions = cookie?.let { byteArrayOf(18, it.size.toByte()) + it } ?: byteArrayOf()
        val plain = extensions + byteArrayOf(0) + audio
        val nonce = ByteArray(12).also { it[11] = index.toByte() }
        val sealed = AirPlayCrypto.chachaSeal(key, nonce, plain, header.copyOfRange(0, 12))
        val body = header + sealed + nonce.copyOfRange(4, 12)
        return ByteBuffer.allocate(4).putInt(body.size + 4).array() + body
    }

    private fun await(condition: () -> Boolean) {
        val deadline = System.nanoTime() + 3_000_000_000
        while (!condition() && System.nanoTime() < deadline) Thread.sleep(5)
        assertTrue(condition())
    }
}
