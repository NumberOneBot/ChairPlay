package io.github.numberonebot.chairplay.media.apac

import org.junit.Assert.*
import org.junit.Test
import java.io.IOException

class ApapSourceTest {
    private val aac = byteArrayOf(3, 22, 0, 1, 0, 4, 17, 0x40, 0x15, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 5, 2, 0x11, 0x90.toByte())

    @Test fun `resolved cookies are fixed before queuing and invalid replacements cannot alter state`() {
        val source = ApapSource()
        source.remember(7, aac)
        val first = source.resolve(unit(ApapMetadata(cookieId = 7)))
        source.remember(7, byteArrayOf(0)) // First registration wins, including duplicate invalid payloads.
        assertArrayEquals(aac, first.metadata.cookie)
        assertArrayEquals(aac, source.resolve(unit()).metadata.cookie)
        assertThrows(IOException::class.java) { source.resolve(unit(ApapMetadata(cookie = byteArrayOf(0)))) }
        assertArrayEquals(aac, source.resolve(unit()).metadata.cookie)
        assertThrows(IOException::class.java) { source.resolve(unit(ApapMetadata(cookieId = 8))) }
        assertArrayEquals(aac, first.metadata.cookie)
        source.close()
        assertThrows(IOException::class.java) { source.resolve(unit()) }
    }

    @Test fun `state only packets retain cookie and loudness without manufacturing audio`() {
        val source = ApapSource()
        val metadata = ApapMetadata(cookie = aac, loudness = listOf(-18f, -1f, -2f), mediaKind = 1)
        assertTrue(source.resolve(unit(metadata).copy(audio = byteArrayOf())).audio.isEmpty())
        val audio = source.resolve(unit())
        assertEquals(metadata.loudness, audio.metadata.loudness)
        assertEquals(1, audio.metadata.mediaKind)
        assertNull(audio.metadata.trimStart)
    }

    @Test fun `media time conversion floors negative values and survives multiplication overflow and RTP wrap`() {
        assertEquals(0xffffffffL, ApapMediaTime(-1, 96000).rtpTime())
        assertEquals(48000L, ApapMediaTime(1000, 1000).rtpTime())
        assertEquals(0xffff4480L, ApapMediaTime(Long.MAX_VALUE, 1).rtpTime())
        assertEquals(4L, ApapMediaTime(0x100000004, 48000).rtpTime())
        assertThrows(IOException::class.java) { ApapMediaTime(1, 0).rtpTime() }
        assertEquals(48000L, apapControlTime(mapOf("mediaTimeValue" to 1000L, "mediaTimeScale" to 1000L)))
        assertEquals(123L, apapControlTime(mapOf("rtpTime" to 123L, "mediaTimeValue" to 1000L)))
        assertNull(apapControlTime(emptyMap()))
        assertThrows(IOException::class.java) { apapControlTime(mapOf("mediaTimeValue" to 12L)) }
    }

    private fun unit(metadata: ApapMetadata = ApapMetadata()) =
        ApapAccessUnit(ApapMediaTime(0, 48000), 1, metadata, byteArrayOf(1))
}
