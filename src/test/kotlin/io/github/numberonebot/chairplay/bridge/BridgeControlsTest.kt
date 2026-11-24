package io.github.numberonebot.chairplay.bridge

import io.github.numberonebot.chairplay.input.Control
import io.github.numberonebot.chairplay.phone.CallKey
import io.github.numberonebot.chairplay.phone.KnobInput
import io.github.numberonebot.chairplay.phone.MediaKey
import org.junit.Assert.assertEquals
import org.junit.Test

/** A game's controls (telemetry flag bit 9) as the app's: what each record sends, against the one before it. */
class BridgeControlsTest {
    private val sent = mutableListOf<Control>()
    private val controls = BridgeControls().also { it.send = { control -> sent += control } }

    private fun update(buttons: Int = 0, knob: Int = 0, zoom: Int = 0, volume: Int = 0): List<Control> {
        sent.clear()
        controls.update(GameControls(buttons, knob, zoom, volume))
        return sent.toList()
    }

    // The first record is where the game stands, as the counter is: what it holds or counted before is no news.
    @Test
    fun `the first record sends nothing`() {
        assertEquals(emptyList<Control>(), update(buttons = SELECT or SIRI, knob = 57, zoom = 3, volume = -2))
    }

    // The knob's tilts, select, back and home hold while their bits are, so CarPlay can tell a long press.
    @Test
    fun `held buttons hold the knob until their bits clear`() {
        update()
        assertEquals(listOf(Control.Knob(KnobInput(select = true), false)), update(buttons = SELECT))
        assertEquals(emptyList<Control>(), update(buttons = SELECT))
        assertEquals(listOf(Control.Knob(KnobInput(select = true, x = -127, y = -127), false)), update(buttons = SELECT or LEFT or UP))
        assertEquals(listOf(Control.Knob(KnobInput(), false)), update())
        assertEquals(listOf(Control.Knob(KnobInput(back = true, home = true, x = 127, y = 127), false)), update(buttons = BACK or HOME or RIGHT or DOWN))
    }

    // The other buttons act once, as their bits turn on: held, they do not repeat; let go, nothing happens.
    @Test
    fun `pressed buttons act as their bits turn on`() {
        update()
        assertEquals(listOf(Control.Siri), update(buttons = SIRI))
        assertEquals(emptyList<Control>(), update(buttons = SIRI))
        assertEquals(emptyList<Control>(), update())
        assertEquals(
            setOf(Control.MuteToggle, Control.Media(MediaKey.PREVIOUS), Control.Media(MediaKey.PLAY_PAUSE), Control.Media(MediaKey.NEXT)),
            update(buttons = MUTE or PREVIOUS or PLAY_PAUSE or NEXT).toSet(),
        )
        update()
        assertEquals(setOf(Control.Telephony(CallKey.ANSWER), Control.Telephony(CallKey.HANG_UP)), update(buttons = ANSWER or END).toSet())
    }

    // The counters' steps since the last record, however many there were between two reads: as the mouse wheel's.
    @Test
    fun `counters step the knob, the zoom and the volume by what they moved`() {
        update(knob = 10, zoom = 5, volume = 5)
        assertEquals(listOf(Control.Knob(KnobInput(wheel = 3), true)), update(knob = 13, zoom = 5, volume = 5))
        assertEquals(listOf(Control.MapZoom(-1)), update(knob = 13, zoom = 4, volume = 5))
        assertEquals(listOf(Control.VolumeStep(2)), update(knob = 13, zoom = 4, volume = 7))
        assertEquals(listOf(Control.Knob(KnobInput(wheel = -2), true)), update(knob = 11, zoom = 4, volume = 7))
    }

    // A counter wraps as a 32-bit integer does: from the largest to the smallest is one step on.
    @Test
    fun `a counter wrapping round steps on`() {
        update(knob = Int.MAX_VALUE)
        assertEquals(listOf(Control.Knob(KnobInput(wheel = 1), true)), update(knob = Int.MIN_VALUE))
    }

    // A game quiet, gone or sending no controls lets go of what it held; its next record is where it stands again.
    @Test
    fun `no controls let go of the knob and start afresh`() {
        update()
        update(buttons = SELECT, knob = 4)
        sent.clear()
        controls.update(null)
        assertEquals(listOf(Control.Knob(KnobInput(), false)), sent)
        sent.clear()
        controls.update(null)
        assertEquals(emptyList<Control>(), sent)
        assertEquals(emptyList<Control>(), update(buttons = SIRI, knob = 90))
    }

    private companion object {
        const val LEFT = 1 shl 0
        const val RIGHT = 1 shl 1
        const val UP = 1 shl 2
        const val DOWN = 1 shl 3
        const val SELECT = 1 shl 4
        const val BACK = 1 shl 5
        const val HOME = 1 shl 6
        const val MUTE = 1 shl 7
        const val PREVIOUS = 1 shl 8
        const val PLAY_PAUSE = 1 shl 9
        const val NEXT = 1 shl 10
        const val ANSWER = 1 shl 11
        const val END = 1 shl 12
        const val SIRI = 1 shl 13
    }
}
