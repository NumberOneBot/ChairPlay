package io.github.numberonebot.chairplay.media.apac

import org.junit.Assert.*
import org.junit.Test

class SpatialPcmTest {
    @Test fun `each APAC decoder channel reaches its named Windows speaker`() {
        // Source C,L,R,Ls,Rs,LFE; WAVEFORMATEXTENSIBLE L,R,C,LFE,Ls,Rs.
        assertArrayEquals(shortArrayOf(200, 300, 100, 600, 400, 500), spatialPcm(shortArrayOf(100, 200, 300, 400, 500, 600), 6))
    }

    @Test fun `downmix keeps sides centre and surrounds with explicit LFE exclusion`() {
        val left = spatialPcm(shortArrayOf(0, 10000, 0, 0, 0, 0), 2)
        val right = spatialPcm(shortArrayOf(0, 0, 10000, 0, 0, 0), 2)
        assertTrue(left[0] > 0); assertEquals(0, left[1].toInt())
        assertTrue(right[1] > 0); assertEquals(0, right[0].toInt())
        val centre = spatialPcm(shortArrayOf(10000, 0, 0, 0, 0, 0), 2)
        assertTrue(centre[0] > 0); assertEquals(centre[0], centre[1])
        val surrounds = spatialPcm(shortArrayOf(0, 0, 0, 10000, -10000, 0), 2)
        assertTrue(surrounds[0] > 0); assertTrue(surrounds[1] < 0)
        assertArrayEquals(shortArrayOf(0, 0), spatialPcm(shortArrayOf(0, 0, 0, 0, 0, 30000), 2))
    }

    @Test fun `simultaneous full scale channels stay within the S16 range without wrapping`() {
        val positive = spatialPcm(ShortArray(6) { 32767 }, 2)
        val negative = spatialPcm(ShortArray(6) { -32768 }, 2)
        assertTrue(positive.all { it >= 32765 })
        assertTrue(negative.all { it <= -32766 })
        assertThrows(IllegalArgumentException::class.java) { spatialPcm(ShortArray(5), 2) }
        assertThrows(IllegalArgumentException::class.java) { spatialPcm(ShortArray(6), 8) }
    }
}
