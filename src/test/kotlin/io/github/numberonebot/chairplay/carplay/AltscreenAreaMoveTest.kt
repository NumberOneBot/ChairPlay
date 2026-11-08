package io.github.numberonebot.chairplay.carplay

import com.shilapi.xcertplay.airplay.AirPlayInfoPlist
import org.junit.Assert.assertEquals
import org.junit.Test

class AltscreenAreaMoveTest {
    // Seen on an iPhone 15 Pro Max (AirPlay 870.14): after updateViewArea the AltScreen's view stays blurred, mid-way,
    // until it is asked for again with showUI; then it is drawn in the new area. So a move sends the area, then the view.
    @Test
    fun `a move sends the area, then asks for the view again`() {
        val sent = mutableListOf<String>()
        val move = AltscreenAreaMove(count = { 3 }, send = { sent += "area ${(it["params"] as Map<*, *>)["viewAreaIndex"]}"; true },
            showView = { sent += "view" })

        assertEquals(true, move(2))
        assertEquals(listOf("area 2", "view"), sent)
    }

    @Test
    fun `a move the iPhone did not get asks for no view`() {
        val sent = mutableListOf<String>()
        val move = AltscreenAreaMove(count = { 3 }, send = { false }, showView = { sent += "view" })

        assertEquals(false, move(1))
        assertEquals(emptyList<String>(), sent)
    }

    // DiPlay's updateViewArea, which it sends for the main screen alone, addressed to the AltScreen.
    @Test
    fun `the AltScreen is moved to an area as DiPlay moves the main screen`() {
        val command = altscreenViewAreaCommand(1, 6)

        assertEquals("updateViewArea", command["type"])
        assertEquals(
            mapOf("uuid" to AirPlayInfoPlist.ALT_UUID, "viewAreaIndex" to 1, "animationDurationMillis" to 300, "adjacentViewAreas" to listOf(0, 2, 3, 4, 5)),
            command["params"],
        )
    }
}
