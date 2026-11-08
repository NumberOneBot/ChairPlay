package io.github.numberonebot.chairplay.media

import io.github.numberonebot.chairplay.input.Size
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/** WindowTitle: the title a window shows, its picture's scale added when that is not one to one. */
class WindowTitleTest {
    private val picture = Size(1280, 720)

    private fun shown(framebuffer: Size, picture: Size = this.picture): String? =
        WindowTitle().apply { base("ChairPlay · 1280×720") }.next(framebuffer, picture)

    @Test
    fun `a picture shown one to one leaves the title as it is`() {
        assertEquals("ChairPlay · 1280×720", shown(Size(1280, 720)))
    }

    @Test
    fun `a scaled picture adds its scale`() {
        assertEquals("ChairPlay · 1280×720 · 1.05×", shown(Size(1344, 756)))
        assertEquals("ChairPlay · 1280×720 · 0.50×", shown(Size(640, 360)))
    }

    // The picture keeps its shape inside the window: the bars beside it do not scale it.
    @Test
    fun `the scale is the letterboxed one`() {
        assertEquals("ChairPlay · 1280×720", shown(Size(1600, 720)))
        assertEquals("ChairPlay · 1280×720 · 1.25×", shown(Size(1600, 1200)))
    }

    @Test
    fun `a scale that rounds to one is not shown`() {
        assertEquals("ChairPlay · 1280×720", shown(Size(1282, 721)))
    }

    @Test
    fun `no picture yet, or no window, leaves the title as it is`() {
        assertEquals("ChairPlay · 1280×720", shown(Size(1344, 756), Size(0, 0)))
        assertEquals("ChairPlay · 1280×720", shown(Size(0, 0)))
    }

    // The scale goes right after the resolution, before what the title says next.
    @Test
    fun `the scale follows the resolution`() {
        val title = WindowTitle()
        title.base("ChairPlay · 1280×720 · connecting…")
        assertEquals("ChairPlay · 1280×720 · 1.05× · connecting…", title.next(Size(1344, 756), picture))
        title.base("AltScreen · 1920×720 · map · cards top")
        assertEquals("AltScreen · 1920×720 · 1.05× · map · cards top", title.next(Size(1344, 756), picture))
        title.base("Controls")
        assertEquals("Controls · 1.05×", title.next(Size(1344, 756), picture))
    }

    @Test
    fun `the title is given again only when it changes`() {
        val title = WindowTitle()
        assertNull(title.next(Size(1344, 756), picture))
        title.base("AltScreen · 1920×720 · map")
        assertEquals("AltScreen · 1920×720 · 1.05× · map", title.next(Size(1344, 756), picture))
        assertNull(title.next(Size(1344, 756), picture))
        assertEquals("AltScreen · 1920×720 · map", title.next(Size(1280, 720), picture))
        title.base("AltScreen · 1920×720 · hud")
        assertEquals("AltScreen · 1920×720 · hud", title.next(Size(1280, 720), picture))
    }

    // A notice, the phone gone quiet, stands after whatever the title says until it is cleared, the title's owner
    // changing it meanwhile.
    @Test
    fun `a notice stays after the title until it is cleared`() {
        val title = WindowTitle()
        title.base("A")
        title.next(picture, picture)

        title.notice("N")
        assertEquals("A · N", title.next(picture, picture))
        title.base("B")
        assertEquals("B · N", title.next(picture, picture))
        title.notice(null)
        assertEquals("B", title.next(picture, picture))
    }
}
