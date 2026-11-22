package io.github.numberonebot.chairplay.platform.windows

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertNull
import org.junit.Test

/** Where APAC's 5.1 (L R C LFE Ls Rs) goes in an output's frame, by the output's speaker mask. */
class SurroundLayoutTest {
    @Test fun `a 5_1 output takes the six channels as they come`() {
        assertArrayEquals(intArrayOf(0, 1, 2, 3, 4, 5), surroundSlots(0x3f))
        assertArrayEquals(intArrayOf(0, 1, 2, 3, 4, 5), surroundSlots(0x60f))
    }

    @Test fun `a 7_1 output puts the surrounds on its sides, its backs silent`() {
        assertArrayEquals(intArrayOf(0, 1, 2, 3, 6, 7), surroundSlots(0x63f))
    }

    @Test fun `an output with backs and no sides puts the surrounds on its backs`() {
        assertArrayEquals(intArrayOf(0, 1, 2, 3, 4, 5), surroundSlots(0xff))
    }

    @Test fun `an output without 5_1 has no slots`() {
        assertNull(surroundSlots(0x3))
        assertNull(surroundSlots(0x607))
    }

    @Test fun `a 5_1 frame spreads to the output's channels by the slots, the rest silent`() {
        val frames = shortArrayOf(1, 2, 3, 4, 5, 6, 11, 12, 13, 14, 15, 16)
        assertArrayEquals(
            shortArrayOf(1, 2, 3, 4, 0, 0, 5, 6, 11, 12, 13, 14, 0, 0, 15, 16),
            spreadSurround(frames, intArrayOf(0, 1, 2, 3, 6, 7), 8),
        )
    }
}
