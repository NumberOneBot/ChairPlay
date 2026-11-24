package io.github.numberonebot.chairplay.config

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * A game's car display (telemetry bit 10) over display.* for the next connections, until the app restarts, never
 * written to the file (owner, 2026-11-24).
 */
class GameDisplayTest {
    // Its millimetres give the width; the height follows the picture's aspect, whatever the file says of either.
    @Test
    fun `a game's car display is in force at the next connections, the file untouched`() {
        val config = configWith("display.width=1280", "display.height=720", "display.physical.height.mm=90", "display.ppi=150")

        assertTrue(config.takeGameDisplay(CarDisplay(800, 480, 180)))
        val main = config.airPlayNow().main
        assertEquals(800 to 480, main.widthPixels to main.heightPixels)
        assertEquals(180 to 108, main.widthPhysicalMm to main.heightPhysicalMm)
        // What the page compares its values with stays the file's: nothing there waits for a reconnect.
        assertEquals("1280", config.now().values["display.width"])
    }

    @Test
    fun `the display in force is not taken again`() {
        val config = configWith("display.width=800", "display.height=480", "display.physical.width.mm=180")

        assertFalse(config.takeGameDisplay(CarDisplay(800, 480, 180)))
        assertTrue(config.takeGameDisplay(CarDisplay(1280, 720, 300)))
        assertFalse(config.takeGameDisplay(CarDisplay(1280, 720, 300)))
        assertTrue(config.now().takeGameDisplay(CarDisplay(800, 480, 180)))
        assertEquals(800, config.now().display.width)
    }
}
