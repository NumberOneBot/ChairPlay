package io.github.numberonebot.chairplay.bridge

import io.github.numberonebot.chairplay.phone.TouchPoint
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** A game's finger on CarPlay's picture (telemetry flag bit 13), held as the buttons are (owner, 2026-11-24). */
class BridgeTouchTest {
    private val sent = mutableListOf<TouchPoint>()
    private val touch = BridgeTouch().also { it.send = { point -> sent += point } }

    private fun point(x: Double, y: Double, down: Boolean) = TouchPoint(BridgeTouch.ID, x, y, down)

    // The place goes as the fractions of the picture the game gives, as the window's clicks do; the session scales them.
    @Test
    fun `a press, a drag and a release are sent once each`() {
        touch.update(GameTouch(0.25, 0.75, true))
        touch.update(GameTouch(0.25, 0.75, true))
        touch.update(GameTouch(0.5, 0.5, true))
        touch.update(GameTouch(0.6, 0.4, false))
        touch.update(GameTouch(0.6, 0.4, false))

        assertEquals(listOf(point(0.25, 0.75, true), point(0.5, 0.5, true), point(0.5, 0.5, false)), sent)
    }

    // No touch in the record, a game quiet or gone, or the switch off: the finger is lifted where it was.
    @Test
    fun `no touch lifts a finger that is down and changes nothing else`() {
        touch.update(null)
        touch.update(GameTouch(0.1, 0.2, false))
        assertTrue(sent.isEmpty())

        touch.update(GameTouch(0.1, 0.2, true))
        touch.update(null)
        touch.update(null)
        assertEquals(listOf(point(0.1, 0.2, true), point(0.1, 0.2, false)), sent)
    }
}
