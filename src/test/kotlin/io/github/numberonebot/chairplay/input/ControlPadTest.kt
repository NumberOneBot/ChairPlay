package io.github.numberonebot.chairplay.input

import io.github.numberonebot.chairplay.phone.KnobInput
import io.github.numberonebot.chairplay.phone.MediaKey
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class ControlPadTest {
    private val pad = ControlPad()

    // A held button holds the knob as a held key does, so CarPlay can tell a long press; the release lets go.
    @Test
    fun `knob buttons hold until released`() {
        assertEquals(Control.Knob(KnobInput(select = true), false), pad.press(ControlAction.SELECT))
        assertEquals(Control.Knob(KnobInput(), false), pad.release(ControlAction.SELECT))
        assertEquals(Control.Knob(KnobInput(x = -127), false), pad.press(ControlAction.KNOB_LEFT))
        assertEquals(Control.Knob(KnobInput(home = true), false), pad.press(ControlAction.HOME))
    }

    @Test
    fun `turns, media, calls, Siri and zoom act on the press alone`() {
        assertEquals(Control.Knob(KnobInput(wheel = 1), true), pad.press(ControlAction.KNOB_NEXT))
        assertEquals(Control.Knob(KnobInput(wheel = -1), true), pad.press(ControlAction.KNOB_PREVIOUS))
        assertEquals(Control.Media(MediaKey.PLAY_PAUSE), pad.press(ControlAction.MEDIA_PLAY_PAUSE))
        assertEquals(Control.Siri, pad.press(ControlAction.SIRI))
        assertEquals(Control.MapZoom(1), pad.press(ControlAction.ALTSCREEN_ZOOM_IN))
        assertEquals(Control.MapZoom(-1), pad.press(ControlAction.ALTSCREEN_ZOOM_OUT))
        assertNull(pad.release(ControlAction.KNOB_NEXT))
        assertNull(pad.release(ControlAction.SIRI))
    }

    @Test
    fun `the volume buttons step the volume and mute`() {
        assertEquals(Control.VolumeStep(1), pad.press(ControlAction.VOLUME_UP))
        assertEquals(Control.VolumeStep(-1), pad.press(ControlAction.VOLUME_DOWN))
        assertEquals(Control.MuteToggle, pad.press(ControlAction.VOLUME_MUTE))
        assertNull(pad.release(ControlAction.VOLUME_UP))
    }

    // As F11 and Shift+F11.
    @Test
    fun `the AltScreen buttons switch its view and its cards`() {
        assertEquals(Control.NextAltscreenContent, pad.press(ControlAction.ALTSCREEN_CONTENT))
        assertEquals(Control.NextAltscreenCards, pad.press(ControlAction.ALTSCREEN_CARDS))
        assertNull(pad.release(ControlAction.ALTSCREEN_CONTENT))
    }
}
