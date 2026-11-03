package io.github.numberonebot.chairplay.media.apac

import org.junit.Assert.*
import org.junit.Test
import java.io.IOException

class SpatialSetupTest {
    @Test fun `APAP validates both offered formats before replacing a stream`() {
        assertEquals(65, spatialSetup(setup(65)).index)
        assertEquals(23, spatialSetup(setup(23)).index)
        for (bad in listOf(setup(64), setup(65) + ("packetFormatAPAP" to false),
            setup(65) + ("shk" to ByteArray(31)), setup(65) + ("spf" to 960L),
            setup(65) + ("audioType" to "telephony"), setup(65) + ("audioFormat" to 0x800000L),
            setup(23) + ("ct" to 0L), setup(65) + ("isMedia" to false))) {
            assertThrows(IOException::class.java) { spatialSetup(bad) }
        }
    }

    @Test fun `SoundCheck follows metadata target headroom rounding and peak saturation on every channel`() {
        assertEquals(Math.pow(10.0, -9.0 / 20), spatialSoundCheck(-16f, listOf(-6.5f, -1f, 0.96875f)), 1e-6)
        assertEquals(Math.pow(10.0, 1.0 / 20), spatialSoundCheck(-16f, listOf(-18f, -1.8125f, -1.78125f)), 1e-6)
        assertEquals(1.0, spatialSoundCheck(null, listOf(-18f, -1f, -2f)), 0.0)
        assertEquals(1.0, spatialSoundCheck(-16f, listOf(-18f, -1f, 0f)), 0.0)
        val samples = shortArrayOf(30000, -30000, 10, -10, 20000, -20000)
        spatialGain(samples, 6, 2f, 2f)
        assertArrayEquals(shortArrayOf(32767, -32768, 20, -20, 32767, -32768), samples)
    }

    private fun setup(index: Int): Map<String, Any?> = mapOf("audioFormatIndex" to index.toLong(),
        "audioFormat" to if (index == 23) 0x800000L else 0L, "packetFormatAPAP" to true,
        "spf" to 1024L, "shk" to ByteArray(32), "audioType" to "media", "isMedia" to true)
}
