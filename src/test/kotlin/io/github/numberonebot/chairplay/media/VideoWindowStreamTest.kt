package io.github.numberonebot.chairplay.media

import io.github.numberonebot.chairplay.config.WindowSize
import org.junit.Assert.assertEquals
import org.junit.Test

/** VideoWindow's stream: the picture it shows, as the page and the title compare the window's size with it. */
class VideoWindowStreamTest {
    // A connection with another display size asks for it from its own thread; until the window has taken the new
    // shape on its next pass, the old size stays the one it shows, or the page and the title would see the old window
    // beside the new picture and offer Actual size or a scale for a moment.
    @Test
    fun `a new stream waits for the window to take it`() {
        val window = VideoWindow(800, 480, stream = WindowSize(800, 480))

        window.setStream(WindowSize(1280, 720))

        assertEquals(WindowSize(800, 480), window.stream)
    }
}
