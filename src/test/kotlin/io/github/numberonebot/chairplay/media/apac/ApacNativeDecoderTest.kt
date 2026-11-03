package io.github.numberonebot.chairplay.media.apac

import org.junit.Assert.*
import org.junit.Assume.assumeNotNull
import org.junit.Test
import java.io.IOException
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.nio.file.Files
import java.nio.file.Path
import kotlin.math.abs
import kotlin.math.ceil
import kotlin.math.floor

class ApacNativeDecoderTest {
    @Test fun `missing library reports unavailability without requiring native setup`() {
        assertThrows(IOException::class.java) { ApacNativeLibrary.load(Path.of("missing-apac-library.dll")) }
    }

    @Test fun `Windows library decodes real APAC to six channels within one S16 sample of the independent oracle`() {
        val fixture = fixture()
        val backend = backend()
        ApacNativeDecoder(backend, fixture.cookie).use { decoder ->
            assertEquals(48000, decoder.format.sampleRate)
            assertEquals(6, decoder.format.channels)
            assertEquals(1024, decoder.format.framesPerPacket)
            assertEquals(0x007c0006, decoder.format.layoutTag)
            var at = 0
            for (packet in fixture.packets) {
                val pcm = decoder.decode(packet)
                assertEquals(6 * 1024, pcm.size)
                for (sample in pcm) {
                    val value = fixture.pcm[at++] * 32768.0
                    val expected = (if (value >= 0) floor(value + 0.5) else ceil(value - 0.5))
                        .coerceIn(-32768.0, 32767.0).toInt()
                    assertTrue("sample $at", abs(sample.toInt() - expected) <= 1)
                }
            }
            assertEquals(fixture.pcm.size, at)
        }
    }

    @Test fun `reset discards prior overlap and close is idempotent while use after close fails`() {
        val fixture = fixture()
        val decoder = ApacNativeDecoder(backend(), fixture.cookie)
        try {
            val first = decoder.decode(fixture.packets.first())
            fixture.packets.drop(1).forEach(decoder::decode)
            decoder.reset()
            assertArrayEquals(first, decoder.decode(fixture.packets.first()))
        } finally { decoder.close() }
        decoder.close()
        assertThrows(IllegalStateException::class.java) { decoder.decode(fixture.packets.first()) }
        assertThrows(IllegalStateException::class.java) { decoder.reset() }
    }

    @Test fun `bad cookie and oversized input fail before reaching unsafe native buffers`() {
        val backend = backend()
        assertThrows(IOException::class.java) { ApacNativeDecoder(backend, byteArrayOf(1, 2, 3)) }
        val fixture = fixture()
        ApacNativeDecoder(backend, fixture.cookie).use { decoder ->
            assertThrows(IOException::class.java) { decoder.decode(ByteArray(1_048_577)) }
            assertThrows(IOException::class.java) { decoder.decode(byteArrayOf()) }
            assertEquals(6144, decoder.decode(fixture.packets.first()).size)
        }
    }

    private fun backend(): ApacNativeLibrary {
        val path = System.getProperty("chairplay.apac.testLibrary")
        assumeNotNull(path)
        return ApacNativeLibrary.load(Path.of(path))
    }

    private data class Fixture(val cookie: ByteArray, val packets: List<ByteArray>, val pcm: FloatArray)
    private fun fixture(): Fixture {
        val path = System.getProperty("chairplay.apac.testFixture")
        assumeNotNull(path)
        val buffer = ByteBuffer.wrap(Files.readAllBytes(Path.of(path))).order(ByteOrder.LITTLE_ENDIAN)
        val magic = ByteArray(8).also(buffer::get)
        check(String(magic, Charsets.US_ASCII) == "APACFRM1")
        val count = buffer.int
        val channels = buffer.int
        val frames = buffer.int
        val cookie = ByteArray(buffer.int).also(buffer::get)
        val lengths = IntArray(count) { buffer.int }
        val packets = lengths.map { length -> ByteArray(length).also(buffer::get) }
        val pcm = FloatArray(count * channels * frames) { buffer.float }
        check(!buffer.hasRemaining())
        return Fixture(cookie, packets, pcm)
    }
}
