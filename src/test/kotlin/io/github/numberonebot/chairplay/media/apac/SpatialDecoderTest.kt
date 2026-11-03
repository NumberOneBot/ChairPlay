package io.github.numberonebot.chairplay.media.apac

import io.github.numberonebot.chairplay.media.AacPcmDecoder
import io.github.numberonebot.chairplay.media.encodeAac
import org.junit.Assert.*
import org.junit.Test
import java.io.IOException
import java.nio.ByteBuffer
import java.nio.ByteOrder

class SpatialDecoderTest {
    @Test fun `discontinuity waits for a verified synchronization packet before invoking native decoder`() {
        val fixture = apacTestFixture()
        SpatialDecoder(apacTestBackend(), 2).use { decoder ->
            val sync = fixture.packets.first()
            val dependent = sync.copyOf().also { it[0] = (it[0].toInt() and 0x3f).toByte() }
            assertTrue(decoder.decode(unit(fixture.cookie, dependent, 65)).samples.isEmpty())
            val expected = decoder.decode(unit(fixture.cookie, sync, 65)).samples
            assertTrue(expected.isNotEmpty())
            decoder.discontinuity()
            assertTrue(decoder.decode(unit(fixture.cookie, dependent, 65)).samples.isEmpty())
            assertArrayEquals(expected, decoder.decode(unit(fixture.cookie, sync, 65)).samples)
        }
    }
    private fun unit(cookie: ByteArray, audio: ByteArray, hint: Int, trim: ApapMediaTime? = null) =
        ApapAccessUnit(ApapMediaTime(0, 48000), 0,
            ApapMetadata(cookie = cookie, formatIndex = hint, samplesPerFrame = 1024, trimStart = trim), audio)

    @Test fun `cookie switches AAC to APAC and back in one output format despite stale format hints`() {
        val fixture = apacTestFixture()
        val backend = apacTestBackend()
        val aacPacket = encodeAac(48000, 2, 3).first()
        val aacExpected = AacPcmDecoder(48000, 2).use { it.decode(aacPacket) }.let { bytes ->
            ByteBuffer.wrap(bytes).order(ByteOrder.LITTLE_ENDIAN).asShortBuffer().let { b -> ShortArray(b.remaining()).also(b::get) }
        }
        val apacExpected = ApacNativeDecoder(backend, fixture.cookie).use { spatialPcm(it.decode(fixture.packets.first()), 6) }
        SpatialDecoder(backend, 6).use { decoder ->
            val first = decoder.decode(unit(aacCookie(), aacPacket, 65))
            assertEquals(SpatialCodec.AAC, first.codec)
            for (frame in aacExpected.indices step 2) {
                val out = frame / 2 * 6
                assertArrayEquals(shortArrayOf(aacExpected[frame], aacExpected[frame + 1], 0, 0, 0, 0), first.samples.copyOfRange(out, out + 6))
            }
            val spatial = decoder.decode(unit(fixture.cookie, fixture.packets.first(), 23))
            assertEquals(SpatialCodec.APAC, spatial.codec)
            assertArrayEquals(apacExpected, spatial.samples)
            val last = decoder.decode(unit(aacCookie(), aacPacket, 65))
            assertEquals(SpatialCodec.AAC, last.codec)
            assertArrayEquals(first.samples, last.samples)
        }
    }

    @Test fun `whole AU trim succeeds with zero output and bad replacement leaves prior decoder intact`() {
        val packet = encodeAac(48000, 2, 3).first()
        SpatialDecoder(null, 2).use { decoder ->
            val trimmed = decoder.decode(unit(aacCookie(), packet, 23, ApapMediaTime(1, 1)))
            assertEquals(0, trimmed.samples.size)
            assertThrows(IOException::class.java) { decoder.decode(unit(byteArrayOf(0), packet, 65)) }
            assertTrue(decoder.decode(unit(aacCookie(), packet, 23)).samples.isNotEmpty())
        }
    }

    @Test fun `missing resolved cookie and unsupported packet geometry are rejected`() {
        SpatialDecoder(null, 2).use { decoder ->
            assertThrows(IOException::class.java) {
                decoder.decode(ApapAccessUnit(ApapMediaTime(0, 48000), 0, ApapMetadata(), byteArrayOf(1)))
            }
            assertThrows(IOException::class.java) {
                decoder.decode(ApapAccessUnit(ApapMediaTime(0, 48000), 0,
                    ApapMetadata(cookie = aacCookie(), samplesPerFrame = 960), byteArrayOf(1)))
            }
        }
    }
}
