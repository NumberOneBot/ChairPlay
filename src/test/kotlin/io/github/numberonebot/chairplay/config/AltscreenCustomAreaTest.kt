package io.github.numberonebot.chairplay.config

import com.shilapi.xcertplay.airplay.AirPlayInsets
import com.shilapi.xcertplay.airplay.AirPlayViewArea
import io.github.numberonebot.chairplay.carplay.toAirPlay
import io.github.numberonebot.chairplay.phone.AltscreenContent
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** DesktopConfig: each area group's custom part of the AltScreen's frame, declared as the iPhone connects. */
class AltscreenCustomAreaTest {
    private val presets = AreaPreset.entries.size

    // The presets, then one custom area per group: the maps', then the turn card's.
    @Test
    fun `a group set to custom shows in its saved rectangle`() {
        val config = configWith("altscreen.enabled=true", "altscreen.card.area=custom", "altscreen.card.area.custom=412, 36, 616, 468")

        val areas = config.airPlay.cluster!!.viewAreas!!
        assertEquals(presets + 2, config.altscreenAreaCount)
        assertEquals(AirPlayViewArea(616, 468, originX = 412, originY = 36), areas[config.altscreenAreaFor(AltscreenContent.CARD)])
        assertEquals(presets + 1, config.altscreenAreaFor(AltscreenContent.CARD))
        assertEquals(AreaChoice.Custom, config.altscreenChoice(AltscreenContent.CARD))
        assertEquals(AreaChoice.Preset(AreaPreset.FULL), config.altscreenChoice(AltscreenContent.MAP))
        assertEquals(0, config.altscreenAreaFor(AltscreenContent.MAP))
    }

    // Its margins count inside the rectangle, given as insets from the whole frame, as for the presets.
    @Test
    fun `a group set to custom with margins shows in its rectangle with them`() {
        val config = configWith("altscreen.enabled=true", "altscreen.card.area=custom", "altscreen.card.area.custom=412, 36, 616, 468",
            "altscreen.card.safe.top=10", "altscreen.card.safe.left=20")

        val areas = config.airPlay.cluster!!.viewAreas!!
        assertEquals(presets + 2 + presets + 1, areas.size)
        val index = config.altscreenAreaFor(AltscreenContent.CARD)
        assertEquals(presets + 2 + presets, index)
        assertEquals(AirPlayViewArea(616, 468, originX = 412, originY = 36,
            safeArea = AirPlayInsets(top = 46, bottom = 36, left = 432, right = 412)), areas[index])
    }

    // The count is fixed for a frame: every group always declares a custom area, with its blocks of margins.
    @Test
    fun `each group always declares a custom area, in the plain list and in its block`() {
        assertEquals(presets + 2, configWith("altscreen.enabled=true").altscreenAreaCount)
        assertEquals(presets + 2 + presets + 1, configWith("altscreen.enabled=true", "altscreen.map.safe.top=4").altscreenAreaCount)
        assertEquals(presets + 2 + 2 * (presets + 1),
            configWith("altscreen.enabled=true", "altscreen.map.safe.top=4", "altscreen.card.safe.top=4").altscreenAreaCount)
    }

    // A blank rectangle: the slot holds the group's preset, or its default while it is set to custom; nothing throws.
    @Test
    fun `a blank rectangle holds the group's preset in its slot`() {
        val custom = configWith("altscreen.enabled=true", "altscreen.card.area=custom", "altscreen.card.area.custom=",
            "altscreen.map.area=custom")
        val areas = custom.airPlay.cluster!!.viewAreas!!
        assertEquals(AreaPreset.CENTER3.area(1440, 540).toAirPlay(), areas[custom.altscreenAreaFor(AltscreenContent.CARD)])
        assertEquals(AreaPreset.FULL.area(1440, 540).toAirPlay(), areas[custom.altscreenAreaFor(AltscreenContent.MAP)])
        assertEquals(presets, custom.altscreenAreaFor(AltscreenContent.MAP))

        val preset = configWith("altscreen.enabled=true", "altscreen.card.area=left3")
        assertEquals(AreaPreset.LEFT3.area(1440, 540).toAirPlay(),
            preset.airPlay.cluster!!.viewAreas!![preset.altscreenAreaIndex(AltscreenContent.CARD, AreaChoice.Custom)])
    }

    // The AltScreen made smaller after a rectangle was saved: the app still starts, the rectangle clamped into the frame.
    @Test
    fun `a saved rectangle that no longer fits is clamped, not refused`() {
        val config = configWith("altscreen.enabled=true", "altscreen.width=1280", "altscreen.height=480",
            "altscreen.card.area=custom", "altscreen.card.area.custom=1200, 0, 400, 300")

        assertEquals(AirPlayViewArea(400, 300, originX = 880), config.airPlay.cluster!!.viewAreas!![config.altscreenAreaFor(AltscreenContent.CARD)])
        assertEquals(1, config.altscreenWarnings.size)
        assertTrue(config.altscreenWarnings.single(), config.altscreenWarnings.single().contains(customKey(AreaGroup.CARD)))
        assertEquals(emptyList<String>(), configWith("altscreen.enabled=true", "altscreen.card.area.custom=0, 0, 400, 300").altscreenWarnings)
        // Off the grid is snapped without a warning.
        val offGrid = configWith("altscreen.enabled=true", "altscreen.card.area=custom", "altscreen.card.area.custom=413, 37, 619, 469")
        assertEquals(AirPlayViewArea(616, 468, originX = 412, originY = 36), offGrid.airPlay.cluster!!.viewAreas!![offGrid.altscreenAreaFor(AltscreenContent.CARD)])
        assertEquals(emptyList<String>(), offGrid.altscreenWarnings)
    }

    // A connection declared before the switch to Custom already holds the group's custom slot, so it applies at once.
    @Test
    fun `switching to custom while CarPlay runs points at the slot already declared`() {
        val config = configWith("altscreen.enabled=true", "altscreen.card.safe.top=10")

        assertEquals(presets, config.altscreenAreaIndex(AltscreenContent.MAP, AreaChoice.Custom))
        assertEquals(presets, config.altscreenAreaIndex(AltscreenContent.MAP_CARD, AreaChoice.Custom))
        assertEquals(presets + 2 + presets, config.altscreenAreaIndex(AltscreenContent.CARD, AreaChoice.Custom))
        assertEquals(presets + 2 + AreaPreset.LEFT5.ordinal, config.altscreenAreaIndex(AltscreenContent.CARD, AreaChoice.Preset(AreaPreset.LEFT5)))
        assertEquals(0, config.altscreenAreaIndex(AltscreenContent.HUD, AreaChoice.Custom))
    }

    @Test
    fun `a rectangle that is not four whole numbers or is too small stops the start with its key`() {
        for (wrong in listOf("1, 2, 3", "a, b, c, d", "0, 0, 32, 540", "0, 0, 400, 10")) {
            val error = runCatching { configWith("altscreen.enabled=true", "altscreen.map.area.custom=$wrong") }.exceptionOrNull()
            assertTrue(wrong, error is IllegalArgumentException && error.message!!.contains("altscreen.map.area.custom"))
        }
        assertTrue(customKey(AreaGroup.MAP) in DesktopConfig.SETTING_KEYS)
        assertTrue(customKey(AreaGroup.CARD) in DesktopConfig.SETTING_KEYS)
    }
}
