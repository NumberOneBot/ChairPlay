package io.github.numberonebot.chairplay.config

import com.shilapi.xcertplay.airplay.AirPlayInfoPlist
import com.shilapi.xcertplay.airplay.AirPlayViewArea
import com.shilapi.xcertplay.airplay.AirPlayInsets
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** DesktopConfig: the CarPlay screen's size, physical size, layout and the window. */
class DisplayConfigTest {
    // A new install draws CarPlay at 1280x720, a window of a size most screens take. Upstream's default physical width
    // is 200 mm; the height follows the aspect ratio, as /info computes it.
    @Test
    fun `the display defaults to 1280x720 at 60 fps and 200 mm wide`() {
        val config = configWith()
        val main = config.airPlay.main

        assertEquals(1280 to 720, main.widthPixels to main.heightPixels)
        assertEquals(60, main.fps)
        assertEquals(200 to 113, main.widthPhysicalMm to main.heightPhysicalMm)
        assertEquals(WindowSize(1280, 720), config.window)
    }

    @Test
    fun `display share is off unless set`() {
        assertFalse(configWith().display.share)
        assertTrue(configWith("display.share=true").display.share)
    }

    @Test
    fun `safe rect is the screen less its insets`() {
        assertEquals(AreaRect(0, 0, 1280, 720), configWith("display.width=1280", "display.height=720").display.safeRect)
        val inset = configWith("display.width=1280", "display.height=720", "display.safe.top=20", "display.safe.left=100")
        assertEquals(AreaRect(100, 20, 1180, 700), inset.display.safeRect)
    }

    // iOS picks its 2x or 3x rendering from pixels against millimetres, so ppi is the convenient knob.
    @Test
    fun `a ppi sets the physical size the iPhone sees`() {
        val main = configWith("display.width=1920", "display.height=1080", "display.ppi=160").airPlay.main

        assertEquals(305 to 171, main.widthPhysicalMm to main.heightPhysicalMm)
    }

    @Test
    fun `millimetres win over ppi and a lone width keeps the aspect ratio`() {
        val both = configWith("display.physical.width.mm=250", "display.physical.height.mm=120", "display.ppi=160").airPlay.main
        assertEquals(250 to 120, both.widthPhysicalMm to both.heightPhysicalMm)

        val width = configWith("display.width=1280", "display.height=720", "display.physical.width.mm=300").airPlay.main
        assertEquals(300 to 169, width.widthPhysicalMm to width.heightPhysicalMm)
    }

    @Test
    fun `fps and the window size come from the settings`() {
        val config = configWith("display.width=1280", "display.height=720", "display.fps=30", "window.width=1600", "window.height=900")

        assertEquals(30, config.airPlay.main.fps)
        assertEquals(WindowSize(1600, 900), config.window)
        assertEquals("display 1280x720 px, 200x113 mm (163 ppi), 30 fps; window 1600x900", config.displaySummary())
    }

    @Test
    fun `rejects display values the iPhone cannot use`() {
        for (bad in listOf("display.fps=90", "display.fps=25", "display.ppi=0", "display.physical.width.mm=-5", "window.width=0")) {
            val error = runCatching { configWith(bad).airPlay }.exceptionOrNull()
            assertTrue("$bad was accepted", error is IllegalArgumentException)
            assertTrue("$bad: ${error?.message}", bad.substringBefore('=') in error!!.message!!)
        }
    }

    // The rest of the CarPlay screen as /info declares it; defaults keep what the iPhone saw before.
    @Test
    fun `screen layout defaults leave the iPhone to decide`() {
        val config = configWith()
        val main = config.airPlay.main

        assertEquals(Theme.SYSTEM, config.theme)
        assertEquals(false, config.airPlay.rightHandDrive)
        assertNull(main.viewAreas)
        assertNull(main.safeArea)
        assertEquals(true, main.safeAreaDrawOutside)
    }

    // A window is rectangular, so the picture fills it with square corners unless rounded is asked for.
    @Test
    fun `the screen has no touch by default, or one that swipes or only taps`() {
        // features: 0x02 a knob, 0x04 a touchscreen that only taps, 0x08 one that swipes; primary input 1 touch, 3 knob.
        // With a mouse the knob is the primary input: CarPlay leaves out its scroll buttons, and clicks still tap.
        val default = configWith().airPlay.main
        assertEquals(0x02 to 3, default.features to default.primaryInputDevice)
        val swipe = configWith("display.touch=swipe").airPlay.main
        assertEquals(0x0A to 1, swipe.features to swipe.primaryInputDevice)
        val tap = configWith("display.touch=tap").airPlay.main
        assertEquals(0x06 to 1, tap.features to tap.primaryInputDevice)
        val none = configWith("display.touch=none").airPlay.main
        assertEquals(0x02 to 3, none.features to none.primaryInputDevice)
    }

    @Test
    fun `corners are square by default and rounded on request`() {
        val square = configWith().airPlay.main
        assertEquals(true, square.mainDisplayCornerMasks)
        assertEquals(true, square.mainDisplayDisableSafeArea)

        val rounded = configWith("display.corners=rounded").airPlay.main
        assertNull(rounded.mainDisplayCornerMasks)
        assertEquals(false, rounded.mainDisplayDisableSafeArea)

        // Square corners leave the safe area out of /info, as upstream does, unless one is set.
        val inset = configWith("display.safe.top=20").airPlay.main
        assertEquals(true, inset.mainDisplayCornerMasks)
        assertEquals(false, inset.mainDisplayDisableSafeArea)
    }

    @Test
    fun `theme, driving side, dock, safe area and corners come from the settings`() {
        val config = configWith(
            "display.width=1280", "display.height=720",
            "display.theme=dark", "display.right.hand.drive=true", "display.dock=bottom",
            "display.safe.top=10", "display.safe.left=40", "display.safe.draw.outside=false", "display.corners=square",
        )
        val main = config.airPlay.main

        assertEquals(Theme.DARK, config.theme)
        assertEquals(true, config.airPlay.rightHandDrive)
        assertEquals(listOf(AirPlayViewArea(1280, 720, dockEdge = AirPlayInfoPlist.DOCK_EDGE_BOTTOM)), main.viewAreas)
        assertEquals(AirPlayInsets(top = 10, left = 40), main.safeArea)
        assertEquals(false, main.safeAreaDrawOutside)
        assertEquals(true, main.mainDisplayCornerMasks)
        assertEquals(AirPlayInfoPlist.DOCK_EDGE_DRIVER_SIDE, configWith("display.dock=driver").airPlay.main.viewAreas!!.single().dockEdge)
        for ((value, theme) in listOf("light" to Theme.LIGHT, "phone" to Theme.PHONE, "system" to Theme.SYSTEM)) {
            assertEquals(theme, configWith("display.theme=$value").theme)
        }
    }

    // A choice the app does not know, as windows that older files have for the system theme, reads as the default.
    @Test
    fun `an unknown theme reads as the default`() {
        assertEquals(Theme.SYSTEM, configWith("display.theme=windows").theme)
        assertEquals(Theme.SYSTEM, configWith("display.theme=sepia").theme)
    }

    @Test
    fun `rejects screen layout values the iPhone cannot use`() {
        for (bad in listOf(
            "display.right.hand.drive=maybe", "display.safe.top=-1", "display.safe.left=700", "display.safe.draw.outside=1",
        )) {
            val extra = if (bad.startsWith("display.safe.left")) arrayOf("display.safe.right=600") else emptyArray()
            val error = runCatching { configWith(bad, *extra).airPlay }.exceptionOrNull()
            assertTrue("$bad was accepted", error is IllegalArgumentException)
            assertTrue("$bad: ${error?.message}", bad.substringBefore('=') in error!!.message!!)
        }
        // A choice the app does not know reads as the default and stops nothing.
        assertEquals(
            configWith().display.screen,
            configWith("display.dock=top", "display.corners=round", "display.touch=pen").display.screen,
        )
    }

    @Test
    fun `windows keep the picture's aspect unless told not to`() {
        assertTrue(configWith().keepAspect)
        assertFalse(configWith("window.keep.aspect=false").keepAspect)
    }
}
