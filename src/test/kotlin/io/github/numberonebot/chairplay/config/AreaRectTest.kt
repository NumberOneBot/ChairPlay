package io.github.numberonebot.chairplay.config

import io.github.numberonebot.chairplay.phone.ScreenArea
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/** AreaRect: a custom part of the AltScreen's frame, as altscreen.<group>.area.custom holds it. */
class AreaRectTest {
    @Test
    fun `a rectangle is four whole numbers, spaces allowed`() {
        assertEquals(AreaRect(412, 36, 616, 468), AreaRect.parse("412, 36, 616, 468"))
        assertEquals(AreaRect(412, 36, 616, 468), AreaRect.parse("412,36,616,468"))
        assertEquals(AreaRect(412, 36, 616, 468), AreaRect.parse(AreaRect(412, 36, 616, 468).toString()))
        assertNull(AreaRect.parse("1,2,3"))
        assertNull(AreaRect.parse("a, b, c, d"))
        assertNull(AreaRect.parse("1, 2, 3, 4, 5"))
        assertNull(AreaRect.parse("1.5, 2, 3, 4"))
        assertNull(AreaRect.parse(""))
    }

    @Test
    fun `a valid rectangle is four whole numbers at least the minimum each way`() {
        assertEquals(AreaRect(413, 37, 64, 64), AreaRect.parseValid("413, 37, 64, 64"))
        assertNull(AreaRect.parseValid("0, 0, 63, 540"))
        assertNull(AreaRect.parseValid("0, 0, 1440, 63"))
        assertNull(AreaRect.parseValid("1, 2, 3"))
    }

    // As the presets' geometry: x, y and the height even, the width a multiple of 8, so a full-height 540 stays reachable.
    @Test
    fun `a rectangle snaps down to the grid and keeps the minimum size`() {
        assertEquals(AreaRect(412, 36, 616, 468), AreaRect(413, 37, 619, 469).snapped())
        assertEquals(AreaRect(0, 0, 1440, 540), AreaRect(0, 0, 1440, 540).snapped())
        assertEquals(AreaRect(0, 0, MIN_AREA, MIN_AREA), AreaRect(0, 0, 10, 3).snapped())
    }

    @Test
    fun `a rectangle clamped into a frame keeps its size if it fits, else shrinks, and moves inside`() {
        assertEquals(AreaRect(880, 0, 400, 300), AreaRect(1200, 0, 400, 300).clampedInto(1280, 480))
        assertEquals(AreaRect(0, 0, 1440, 540), AreaRect(0, 0, 2000, 900).clampedInto(1440, 540))
        assertEquals(AreaRect(0, 180, 400, 300), AreaRect(-20, 500, 400, 300).clampedInto(1280, 480))
        assertEquals(AreaRect(412, 36, 616, 468), AreaRect(413, 37, 619, 469).clampedInto(1440, 540))
    }

    @Test
    fun `a rectangle is the view area at its place`() {
        assertEquals(ScreenArea(616, 468, x = 412, y = 36), AreaRect(412, 36, 616, 468).area())
        assertEquals("altscreen.card.area.custom", customKey(AreaGroup.CARD))
        assertEquals("altscreen.map.area.custom", customKey(AreaGroup.MAP))
    }

    @Test
    fun `a group's choice is a preset or custom`() {
        assertEquals(AreaChoice.Custom, AreaChoice.of("custom"))
        assertEquals(AreaChoice.Preset(AreaPreset.CENTER5), AreaChoice.of(" Center5 "))
        assertNull(AreaChoice.of("half"))
    }
}
