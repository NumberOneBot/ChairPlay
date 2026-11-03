package io.github.numberonebot.chairplay.media.apac

import org.junit.Assert.*
import org.junit.Test
import java.math.BigInteger

class SpatialControlsTest {
    @Test fun `spatial offer retains AAC bootstrap and extended formats without shifting by 65`() {
        val info = linkedMapOf<String, Any?>("audioFormats" to listOf(mapOf("type" to 100), mapOf("type" to 103)))
        spatialOffer(info)
        val offers = info["audioFormats"] as List<*>
        assertEquals(mapOf("type" to 100), offers[0])
        val buffered = offers[1] as Map<*, *>
        assertEquals(0x400000L, buffered["audioOutputFormats"])
        assertEquals(listOf(65, 23), buffered["audioOutputFormatsExtended"])
        assertEquals("media", buffered["audioType"])
    }

    @Test fun `flush ranges cover inclusive sequence interval across 24 bit wrap and until-only prefix`() {
        val range = ApapFlushRange.parse(mapOf("flushFromSeq" to 0xfffffeL, "flushUntilSeq" to 1L))
        assertTrue(range.contains(0xffffff, 0))
        assertTrue(range.contains(0, 0))
        assertTrue(range.contains(1, 0))
        assertFalse(range.contains(2, 0))
        assertFalse(range.contains(0xfffffd, 0))
        val prefix = ApapFlushRange.parse(mapOf("flushUntilMediaTimeValue" to 2L, "flushUntilMediaTimeScale" to 1000L))
        assertTrue(prefix.contains(10, 0))
        assertFalse(prefix.contains(11, 1024))
        assertEquals(96L, prefix.untilTime)
    }

    @Test fun `anchor reply includes both transport time representations`() {
        val anchor = spatialAnchor(mapOf("rtpTime" to 123L, "rate" to 1, "networkTimeSecs" to 20L,
            "networkTimeFrac" to BigInteger.ZERO))!!
        assertEquals(123L, anchor["mediaTimeValue"])
        assertEquals(48000, anchor["mediaTimeScale"])
        assertEquals(0, anchor["status"])
        assertEquals(20L, anchor["networkTimeSecs"])
    }
}
