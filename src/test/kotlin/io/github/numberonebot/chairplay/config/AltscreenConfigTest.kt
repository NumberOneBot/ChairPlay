package io.github.numberonebot.chairplay.config

import com.shilapi.xcertplay.airplay.AirPlayInfoPlist
import com.shilapi.xcertplay.airplay.AirPlayViewArea
import com.shilapi.xcertplay.airplay.AirPlayInsets
import io.github.numberonebot.chairplay.carplay.toAirPlay
import io.github.numberonebot.chairplay.phone.ScreenArea
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.StringReader
import java.nio.file.Files
import java.nio.file.StandardOpenOption
import java.util.Properties

/** DesktopConfig: the AltScreen's settings, its view areas and what /info declares of it. */
class AltscreenConfigTest {
    // Without the setting the iPhone hears of no second screen, as before.
    @Test
    fun `the altscreen is off unless asked for`() {
        assertNull(configWith().airPlay.cluster)
        assertNull(configWith().altscreen)
    }

    // An enabled AltScreen declares every part of the frame as it connects, the whole frame first, so a view moves to
    // its part as soon as it is set, without reconnecting; a view on the full frame stays in the first.
    @Test
    fun `the altscreen declares every part, the views on the full frame in the first`() {
        val config = configWith("altscreen.enabled=true", "altscreen.width=1920", "altscreen.height=720", "altscreen.card.area=right3")

        // Then each group's custom area, its preset's while no rectangle is saved.
        val customs = listOf(AreaPreset.FULL.area(1920, 720), AreaPreset.RIGHT3.area(1920, 720))
        assertEquals((AreaPreset.entries.map { it.area(1920, 720) } + customs).map { it.toAirPlay() }, config.airPlay.cluster!!.viewAreas)
        assertEquals(12, config.altscreenAreaCount)
        assertEquals(listOf(0, 0, 0, 9), AltscreenContent.entries.map(config::altscreenAreaFor))
        // Another part, set while CarPlay runs, is an area the iPhone already knows.
        assertEquals(3, config.altscreenAreaIndex(AltscreenContent.MAP, AreaChoice.Preset(AreaPreset.RIGHT5)))
        assertEquals(0, config.altscreenAreaIndex(AltscreenContent.HUD, AreaChoice.Preset(AreaPreset.RIGHT5)))
        assertNull(configWith().airPlay.cluster)
    }

    // The views start as the owner set them up (2026-10-24): the turn card in the middle three eighths; both maps on the
    // whole frame, all of it safe.
    @Test
    fun `the views default to the card in the middle and the maps on the whole frame`() {
        val config = configWith("altscreen.enabled=true")
        assertEquals(AreaChoice.Preset(AreaPreset.CENTER3), config.altscreenChoice(AltscreenContent.CARD))
        assertEquals(AreaChoice.Preset(AreaPreset.FULL), config.altscreenChoice(AltscreenContent.MAP))
        assertEquals(AreaChoice.Preset(AreaPreset.FULL), config.altscreenChoice(AltscreenContent.MAP_CARD))
        assertEquals(AltscreenCards.TOP, config.altscreen!!.cards)
        val defaults = Properties().apply { load(StringReader(DesktopConfig.defaultSettingsText())) }
        assertEquals("top", defaults.getProperty("altscreen.cards"))
        assertEquals("full", defaults.getProperty("altscreen.map.area"))
        assertEquals("center3", defaults.getProperty("altscreen.card.area"))
        // The blank margins leave the maps to altscreen.safe.*, all zero; the rectangles are blank until set.
        for (group in listOf("map", "card")) {
            val blank = listOf("area.custom", "safe.top", "safe.bottom", "safe.left", "safe.right")
            for (key in blank) assertEquals("altscreen.$group.$key", "", defaults.getProperty("altscreen.$group.$key"))
        }
        assertTrue(defaults.stringPropertyNames().none { it.startsWith("altscreen.map-card.") })
    }

    // Center 5/8 of the default 1440x540 frame: 900 px as a width keeps to a multiple of 8 (896, as Right 5/8), in the
    // middle with an even origin.
    @Test
    fun `Center 5 of 8 is the middle five eighths`() {
        assertEquals(ScreenArea(896, 540, x = 272), AreaPreset.CENTER5.area(1440, 540))
        assertEquals(ScreenArea(1200, 720, x = 360), AreaPreset.CENTER5.area(1920, 720))
        assertEquals(AreaPreset.CENTER5, AreaPreset.of("center5"))
        assertEquals(10, AreaPreset.entries.size)
    }

    // Map and Map + card are one group: they share the part, the margins and the areas; altscreen.map-card.* is not read.
    @Test
    fun `both maps share one group and altscreen map-card keys are ignored`() {
        val config = configWith("altscreen.enabled=true", "altscreen.map.area=center5", "altscreen.map.safe.top=10",
            "altscreen.map-card.area=left3", "altscreen.map-card.safe.left=100")

        val map = AltscreenContent.MAP
        val mapCard = AltscreenContent.MAP_CARD
        assertEquals(AreaChoice.Preset(AreaPreset.CENTER5), config.altscreenChoice(map))
        assertEquals(AreaChoice.Preset(AreaPreset.CENTER5), config.altscreenChoice(mapCard))
        assertEquals(config.altscreenAreaFor(map), config.altscreenAreaFor(mapCard))
        val left4 = AreaChoice.Preset(AreaPreset.LEFT4)
        assertEquals(config.altscreenAreaIndex(map, left4), config.altscreenAreaIndex(mapCard, left4))
        // The plain areas with the two custom ones, then the map group's block alone: the ignored keys add none.
        assertEquals(AreaPreset.entries.size + 2 + AreaPreset.entries.size + 1, config.altscreenAreaCount)
        assertEquals(AreaPreset.entries.size + 2 + AreaPreset.CENTER5.ordinal, config.altscreenAreaFor(mapCard))
    }

    @Test
    fun `a group without a setting takes its default part`() {
        val config = configWith("altscreen.enabled=true", "altscreen.card.area=", "altscreen.map.area=")
        assertEquals(AreaChoice.Preset(AreaPreset.CENTER3), config.altscreenChoice(AltscreenContent.CARD))
        assertEquals(AreaChoice.Preset(AreaPreset.FULL), config.altscreenChoice(AltscreenContent.MAP))
        assertEquals(AreaPreset.CENTER3, defaultArea(AreaGroup.CARD))
        assertEquals(listOf(null, AreaGroup.MAP, AreaGroup.MAP, AreaGroup.CARD), AltscreenContent.entries.map { it.areaGroup })
    }

    // A view with margins of its own gets every part again with its margins, counted inside each part; the iPhone hears
    // of each area's own safe area as it connects, so the margins apply from the next connection, its part at once.
    @Test
    fun `a view with margins of its own gets every part with them`() {
        val config = configWith("altscreen.enabled=true", "altscreen.width=1920", "altscreen.height=720",
            "altscreen.card.area=center3", "altscreen.card.safe.top=10", "altscreen.card.safe.bottom=20",
            "altscreen.map.safe.left=", "altscreen.map.safe.right=300")

        val areas = config.airPlay.cluster!!.viewAreas!!
        assertEquals(34, areas.size)
        assertEquals(AirPlayViewArea(1920, 720), areas[0])
        // The maps' parts come next, then the turn card's, each block with its group's custom area last.
        assertEquals(AirPlayViewArea(1920, 720, safeArea = AirPlayInsets(right = 300)), areas[12])
        assertEquals(AirPlayViewArea(720, 720, originX = 600, safeArea = AirPlayInsets(top = 10, bottom = 20, left = 600, right = 600)), areas[31])
        assertEquals(12, config.altscreenAreaFor(AltscreenContent.MAP))
        assertEquals(12, config.altscreenAreaFor(AltscreenContent.MAP_CARD))
        assertEquals(31, config.altscreenAreaFor(AltscreenContent.CARD))
        assertEquals(0, config.altscreenAreaFor(AltscreenContent.HUD))
        assertEquals(24, config.altscreenAreaIndex(AltscreenContent.CARD, AreaChoice.Preset(AreaPreset.LEFT5)))

        // DiPlay's /info carries the area's own safe area, where it clipped the display's to every area.
        val alt = (AirPlayInfoPlist.build(config.airPlay)["displays"] as List<*>)[1] as Map<*, *>
        val card = (alt["viewAreas"] as List<*>)[31] as Map<*, *>
        val safe = card["safeArea"] as Map<*, *>
        assertEquals(listOf(600, 10, 720, 690), listOf(safe["originXPixels"], safe["originYPixels"], safe["widthPixels"], safe["heightPixels"]))
        // The same part of another frame.
        val small = configWith("altscreen.enabled=true", "altscreen.width=1440", "altscreen.height=540")
        assertEquals(AirPlayViewArea(896, 540, originX = 544), small.airPlay.cluster!!.viewAreas!![AreaPreset.RIGHT5.ordinal])
    }

    // Margins that fit the view's own part but not a narrower one shrink in that one with its width, so the view can
    // still move there: the maps kept to the middle three eighths of the whole frame, in the five eighths.
    @Test
    fun `margins too wide for a narrower part shrink with it`() {
        val config = configWith("altscreen.enabled=true", "altscreen.width=1280", "altscreen.height=480",
            "altscreen.map.area=full", "altscreen.map.safe.left=400", "altscreen.map.safe.right=400")
        val areas = config.airPlay.cluster!!.viewAreas!!
        val whole = areas[config.altscreenAreaIndex(AltscreenContent.MAP_CARD, AreaChoice.Preset(AreaPreset.FULL))]
        assertEquals(400, whole.safeArea!!.left)
        assertEquals(400, whole.safeArea!!.right)
        // The 800 px five eighths at the left: 400 * 800 / 1280 at each side.
        val left5 = areas[config.altscreenAreaIndex(AltscreenContent.MAP_CARD, AreaChoice.Preset(AreaPreset.LEFT5))]
        assertEquals(250, left5.safeArea!!.left)
        assertEquals(1280 - 800 + 250, left5.safeArea!!.right)
    }

    @Test
    fun `a view's part is one of the eighths, and leaves room inside its margins`() {
        val base = arrayOf("altscreen.enabled=true", "altscreen.width=1920", "altscreen.height=720")
        for (wrong in listOf("altscreen.card.area=0 0 480 720", "altscreen.card.area=half", "altscreen.map.safe.left=-1",
            "altscreen.map.safe.top=wide", "altscreen.card.area=left3\naltscreen.card.safe.left=360\naltscreen.card.safe.right=360")) {
            assertTrue(wrong, runCatching { configWith(*base, *wrong.split("\n").toTypedArray()) }.exceptionOrNull() is IllegalArgumentException)
        }
    }

    @Test
    fun `the altscreen defaults to Audi's cockpit panel and can start on the turn card`() {
        val config = configWith("altscreen.enabled=true", "altscreen.content=card")

        val cluster = config.airPlay.cluster!!
        assertEquals(1440, cluster.widthPixels)
        assertEquals(540, cluster.heightPixels)
        assertEquals(292, cluster.widthPhysicalMm)
        assertEquals(110, cluster.heightPhysicalMm)
        // Nothing covers a window on a PC, so the iPhone keeps its UI (attribution, car marker) to the panel's edges.
        assertNull(cluster.safeArea)
        // The turn card on top, as on Map + card by default.
        assertEquals("maps:/car/instrumentcluster/instructioncard?showETA=yes&maneuverLayout=topAligned", config.airPlay.cluster!!.initialUrl)
    }

    // DashBoard reads the card layout from the cluster URL for every navigation app (MHI2Q CarPlay project notes).
    @Test
    fun `the altscreen card layout goes into the cluster url`() {
        assertEquals("maps:/car/instrumentcluster/map", configWith("altscreen.enabled=true", "altscreen.content=map", "altscreen.cards=auto").airPlay.cluster!!.initialUrl)
        assertEquals(
            "maps:/car/instrumentcluster?showETA=yes&maneuverLayout=rightAligned",
            configWith("altscreen.enabled=true", "altscreen.content=map-card", "altscreen.cards=right").airPlay.cluster!!.initialUrl,
        )
        // On top by default.
        assertEquals(
            "maps:/car/instrumentcluster?showETA=yes&maneuverLayout=topAligned",
            configWith("altscreen.enabled=true").airPlay.cluster!!.initialUrl,
        )
        assertEquals(AltscreenCards.TOP, AltscreenCards.RIGHT.next())
    }

    // The HUD shows the turn card cut out of the iPhone's card-only stream, which costs next to no bandwidth.
    @Test
    fun `the HUD asks the iPhone for the turn card`() {
        assertEquals(
            "maps:/car/instrumentcluster/instructioncard",
            configWith("altscreen.enabled=true", "altscreen.content=hud", "altscreen.cards=auto").airPlay.cluster!!.initialUrl,
        )
    }

    // F11 switches between the HUD and the view of the iPhone's picked last, which stands as the AltScreen's other one.
    @Test
    fun `F11 switches between the HUD and the view picked last`() {
        assertEquals(AltscreenContent.MAP_CARD, AltscreenContent.HUD.toggled(AltscreenContent.MAP_CARD))
        assertEquals(AltscreenContent.HUD, AltscreenContent.MAP.toggled(AltscreenContent.MAP_CARD))
        assertEquals(AltscreenContent.HUD, AltscreenContent.CARD.toggled(AltscreenContent.MAP))
    }

    // Margins all zero are none: the view shows in the plain frame's areas, so a stream starts in its area, the first.
    @Test
    fun `a view with margins all zero shows in the plain frame's areas`() {
        val config = configWith("altscreen.enabled=true", "altscreen.map.safe.top=0", "altscreen.map.safe.left=0")

        assertEquals(0, config.altscreenAreaFor(AltscreenContent.MAP))
        assertEquals(AreaPreset.entries.size + 2, config.altscreenAreaCount)
    }

    // Under the HUD the iPhone stops drawing, unless altscreen.hud.stream keeps the map view picked last running, so F11
    // comes back to it at once. A demo's HUD over a view keeps that view.
    @Test
    fun `under the HUD the iPhone draws nothing unless the stream is kept`() {
        assertEquals(false, configWith("altscreen.enabled=true").altscreen!!.streamUnderHud)
        assertEquals(true, configWith("altscreen.enabled=true", "altscreen.hud.stream=true").altscreen!!.streamUnderHud)
        val hud = AltscreenContent.HUD
        assertNull(drawnView(hud, AltscreenContent.MAP_CARD, hud = true, underHud = false))
        assertEquals(AltscreenContent.MAP_CARD, drawnView(hud, AltscreenContent.MAP_CARD, hud = true, underHud = true))
        assertEquals(AltscreenContent.CARD, drawnView(AltscreenContent.CARD, AltscreenContent.MAP, hud = true, underHud = true))
        assertEquals(AltscreenContent.MAP, drawnView(AltscreenContent.MAP, AltscreenContent.MAP_CARD, hud = false, underHud = false))
    }

    // The HUD shows a session's route and song only when it is the view picked, or for a demo: on another view, it keeps
    // its waiting line while that view's picture is on its way, rather than flash the song first.
    @Test
    fun `the HUD shows the session only as the view picked or for a demo`() {
        assertEquals(false, hudShowsSession(AltscreenContent.HUD, session = false, demo = false))
        assertEquals(true, hudShowsSession(AltscreenContent.HUD, session = true, demo = false))
        assertEquals(false, hudShowsSession(AltscreenContent.MAP, session = true, demo = false))
        assertEquals(true, hudShowsSession(AltscreenContent.MAP, session = false, demo = true))
    }

    // The turn card's place (altscreen.cards, Shift+F11) is Map + card's alone: the map, the turn card alone, and the HUD
    // have none to place.
    @Test
    fun `only Map + card places the turn card`() {
        assertEquals(listOf(false, false, true, false), AltscreenContent.entries.map { it.placesCards })
    }

    // Shift+F11 steps through the places the navigation apps take; neither Google Maps nor Apple Maps hides the card.
    @Test
    fun `Shift+F11 goes auto, left, right, top`() {
        assertEquals(listOf("auto", "left", "right", "top"), AltscreenCards.entries.map { it.key })
        assertEquals(AltscreenCards.AUTO, AltscreenCards.TOP.next())
        assertTrue(runCatching { configWith("altscreen.enabled=true", "altscreen.cards=off") }.exceptionOrNull() is IllegalArgumentException)
    }

    // Every start is on the settings' view, whatever view the AltScreen was left on (an older version remembered it).
    @Test
    fun `the AltScreen starts on the settings' view, not the one it was left on`() {
        val dir = Files.createTempDirectory("chairplay-config")
        writeTestSettings(dir)
        Files.writeString(dir.resolve("settings.properties"), "altscreen.enabled=true\n", StandardOpenOption.APPEND)
        Files.writeString(
            dir.resolve("window.properties"),
            "altscreen.view.content=card\naltscreen.view.cards=right\naltscreen.view.settings=map,top\n",
        )

        val config = DesktopConfig(dir)

        assertEquals(AltscreenContent.MAP_CARD, config.altscreen!!.content)
        assertEquals(AltscreenCards.TOP, config.altscreen!!.cards)
    }

    // Room for an overlay of our own, such as the maneuver layer on the right.
    @Test
    fun `the altscreen safe area can leave room at its edges`() {
        val config = configWith("altscreen.enabled=true", "altscreen.safe.right=400", "altscreen.safe.top=20")

        assertEquals(AirPlayInsets(top = 20, right = 400), config.airPlay.cluster!!.safeArea)
    }

    @Test
    fun `an unknown altscreen content stops the start with the setting named`() {
        val error = assertThrows(IllegalArgumentException::class.java) {
            configWith("altscreen.enabled=true", "altscreen.content=radio")
        }
        assertTrue(error.message, error.message!!.contains("altscreen.content"))
    }
}
