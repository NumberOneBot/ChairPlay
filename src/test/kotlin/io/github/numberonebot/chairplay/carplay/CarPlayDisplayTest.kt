package io.github.numberonebot.chairplay.carplay

import com.shilapi.xcertplay.airplay.AirPlayInfoPlist
import com.shilapi.xcertplay.airplay.AirPlayInsets
import com.shilapi.xcertplay.airplay.AirPlayViewArea
import io.github.numberonebot.chairplay.config.AltscreenCards
import io.github.numberonebot.chairplay.config.AltscreenContent
import io.github.numberonebot.chairplay.phone.ClusterSpec
import io.github.numberonebot.chairplay.phone.Dock
import io.github.numberonebot.chairplay.phone.Insets
import io.github.numberonebot.chairplay.phone.ScreenArea
import io.github.numberonebot.chairplay.phone.ScreenSpec
import io.github.numberonebot.chairplay.phone.TouchMode
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/** The CarPlay screen and the AltScreen, described in ChairPlay's terms, as /info declares them to the iPhone. */
class CarPlayDisplayTest {
    private val screen = ScreenSpec(1280, 720, 60, 200 to 113, Insets(), roundedCorners = false, dock = Dock.AUTO,
        touch = TouchMode.NONE, drawOutsideSafe = true)

    @Test
    fun `the main screen keeps its size, physical size and frame rate`() {
        val main = screen.copy(width = 800, height = 480, fps = 30, physicalMm = 150 to 90).toAirPlay()

        assertEquals(800 to 480, main.widthPixels to main.heightPixels)
        assertEquals(150 to 90, main.widthPhysicalMm to main.heightPhysicalMm)
        assertEquals(30, main.fps)
    }

    // features: 0x02 a knob, 0x04 a touchscreen that only taps, 0x08 one that swipes; primary input 1 touch, 3 knob.
    @Test
    fun `touch declares the knob always, and the touch the screen can do`() {
        val declared = TouchMode.entries.associateWith { screen.copy(touch = it).toAirPlay().let { main -> main.features to main.primaryInputDevice } }

        assertEquals(mapOf(TouchMode.SWIPE to (0x0A to 1), TouchMode.TAP to (0x06 to 1), TouchMode.NONE to (0x02 to 3)), declared)
    }

    @Test
    fun `a dock edge is one view area over the whole screen, and auto none`() {
        assertNull(screen.toAirPlay().viewAreas)
        assertEquals(listOf(AirPlayViewArea(1280, 720, dockEdge = AirPlayInfoPlist.DOCK_EDGE_DRIVER_SIDE)),
            screen.copy(dock = Dock.DRIVER).toAirPlay().viewAreas)
        assertEquals(listOf(AirPlayViewArea(1280, 720, dockEdge = AirPlayInfoPlist.DOCK_EDGE_BOTTOM)),
            screen.copy(dock = Dock.BOTTOM).toAirPlay().viewAreas)
    }

    @Test
    fun `insets all zero declare no safe area`() {
        assertNull(screen.toAirPlay().safeArea)
        val inset = screen.copy(safe = Insets(top = 10, left = 40), drawOutsideSafe = false).toAirPlay()

        assertEquals(AirPlayInsets(top = 10, left = 40), inset.safeArea)
        assertEquals(false, inset.safeAreaDrawOutside)
    }

    // Square corners leave the safe area out of /info, as upstream does, unless one is set.
    @Test
    fun `square corners mask them, and leave out a safe area that is not set`() {
        val square = screen.toAirPlay()
        assertEquals(true to true, square.mainDisplayCornerMasks to square.mainDisplayDisableSafeArea)
        val inset = screen.copy(safe = Insets(top = 20)).toAirPlay()
        assertEquals(true to false, inset.mainDisplayCornerMasks to inset.mainDisplayDisableSafeArea)
        val rounded = screen.copy(roundedCorners = true).toAirPlay()
        assertEquals(null to false, rounded.mainDisplayCornerMasks to rounded.mainDisplayDisableSafeArea)
    }

    @Test
    fun `an area keeps its place and its insets from the whole frame`() {
        assertEquals(AirPlayViewArea(616, 468, originX = 412, originY = 36), ScreenArea(616, 468, 412, 36).toAirPlay())
        assertEquals(AirPlayViewArea(720, 540, originX = 360, safeArea = AirPlayInsets(top = 10, bottom = 0, left = 380, right = 360)),
            ScreenArea(720, 540, x = 360, safe = Insets(top = 10, left = 380, right = 360)).toAirPlay())
    }

    // DiPlay rounds its cluster stream to multiples of 8; the AltScreen goes out at the size set, 292 mm wide as Audi's
    // panel, at 30 fps and without input.
    @Test
    fun `the AltScreen goes out at its size, with its areas, margins and view`() {
        val areas = listOf(ScreenArea(1020, 382), ScreenArea(640, 382, x = 380))
        val cluster = ClusterSpec(1020, 382, Insets(top = 20, right = 400), areas, AltscreenContent.MAP_CARD, AltscreenCards.LEFT).toCarPlay()

        assertEquals(1020 to 382, cluster.widthPixels to cluster.heightPixels)
        assertEquals(292 to 109, cluster.widthPhysicalMm to cluster.heightPhysicalMm)
        assertEquals(30, cluster.fps)
        assertEquals(0 to 0, cluster.features to cluster.primaryInputDevice)
        assertEquals(AirPlayInsets(top = 20, right = 400), cluster.safeArea)
        assertEquals(areas.map { it.toAirPlay() }, cluster.viewAreas)
        assertEquals("maps:/car/instrumentcluster?showETA=yes&maneuverLayout=leftAligned", cluster.initialUrl)
        assertNull(ClusterSpec(1440, 540, Insets(), areas, AltscreenContent.MAP, AltscreenCards.TOP).toCarPlay().safeArea)
    }

    // The HUD is ChairPlay's: the iPhone sends the turn card under it. Cards auto leaves the layout to CarPlay.
    @Test
    fun `each view asks for its cluster URL, with the cards placed as set`() {
        val urls = AltscreenContent.entries.associateWith { altscreenUrl(it, AltscreenCards.AUTO) }

        assertEquals(mapOf(
            AltscreenContent.HUD to "maps:/car/instrumentcluster/instructioncard",
            AltscreenContent.MAP to "maps:/car/instrumentcluster/map",
            AltscreenContent.MAP_CARD to "maps:/car/instrumentcluster",
            AltscreenContent.CARD to "maps:/car/instrumentcluster/instructioncard",
        ), urls)
        assertEquals("maps:/car/instrumentcluster?showETA=yes&maneuverLayout=rightAligned", altscreenUrl(AltscreenContent.MAP_CARD, AltscreenCards.RIGHT))
        assertEquals("maps:/car/instrumentcluster/map?showETA=yes&maneuverLayout=topAligned", altscreenUrl(AltscreenContent.MAP, AltscreenCards.TOP))
    }
}
