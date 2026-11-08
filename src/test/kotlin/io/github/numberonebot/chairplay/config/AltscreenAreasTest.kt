package io.github.numberonebot.chairplay.config

import io.github.numberonebot.chairplay.phone.ScreenArea
import org.junit.Assert.assertEquals
import org.junit.Test

class AltscreenAreasTest {
    // The parts a view can show in, for an 8:3 cluster: the whole frame; five eighths at the left, the middle or the
    // right; four eighths, a half, at the left, the middle or the right; three eighths at the left, the middle or the
    // right, between dials or in a side panel. All of the full height.
    @Test
    fun `the parts are the whole frame and parts of its eighths`() {
        assertEquals(
            listOf(
                ScreenArea(1920, 720),
                ScreenArea(1200, 720), ScreenArea(1200, 720, x = 360), ScreenArea(1200, 720, x = 720),
                ScreenArea(960, 720), ScreenArea(960, 720, x = 480), ScreenArea(960, 720, x = 960),
                ScreenArea(720, 720), ScreenArea(720, 720, x = 600), ScreenArea(720, 720, x = 1200),
            ),
            AreaPreset.entries.map { it.area(1920, 720) },
        )
        // A width keeps to a multiple of 8, as DiPlay's streams do, and the middle ones to an even origin.
        assertEquals(
            listOf(
                ScreenArea(1440, 540),
                ScreenArea(896, 540), ScreenArea(896, 540, x = 272), ScreenArea(896, 540, x = 544),
                ScreenArea(720, 540), ScreenArea(720, 540, x = 360), ScreenArea(720, 540, x = 720),
                ScreenArea(536, 540), ScreenArea(536, 540, x = 452), ScreenArea(536, 540, x = 904),
            ),
            AreaPreset.entries.map { it.area(1440, 540) },
        )
    }
}
