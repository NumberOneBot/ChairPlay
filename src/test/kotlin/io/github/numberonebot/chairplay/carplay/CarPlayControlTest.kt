package io.github.numberonebot.chairplay.carplay

import com.shilapi.xcertplay.airplay.AirPlayContact
import com.shilapi.xcertplay.airplay.AirPlayKnobState
import com.shilapi.xcertplay.airplay.CarPlayMediaButton
import io.github.numberonebot.chairplay.phone.CallKey
import io.github.numberonebot.chairplay.phone.KnobInput
import io.github.numberonebot.chairplay.phone.MediaKey
import io.github.numberonebot.chairplay.phone.TouchPoint
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class CarPlayControlTest {
    private class Recorder(var zoomWorks: Boolean = true) : CarPlayWire {
        val sent = mutableListOf<Any>()
        override fun sendKnob(state: AirPlayKnobState, momentary: Boolean) { sent += state to momentary }
        override fun sendMedia(index: Int) { sent += "media" to index }
        override fun sendTelephony(index: Int) { sent += "telephony" to index }
        override fun invokeSiri() { sent += "siri" }
        override fun sendTouch(contacts: List<AirPlayContact>) { sent.add(contacts) }
        override fun changeMapZoomLevel(zoomIn: Boolean): Boolean { sent += "zoom" to zoomIn; return zoomWorks }
    }

    private val wire = Recorder()
    private val control = CarPlayControl(wire)

    @Test
    fun `every knob field reaches the same field of the knob state`() {
        control.knob(KnobInput(select = true, home = true, back = true, x = -127, y = 127, wheel = 3), false)
        assertEquals(listOf<Any>(AirPlayKnobState(select = true, home = true, back = true, x = -127, y = 127, wheel = 3) to false), wire.sent)
    }

    @Test
    fun `a wheel step stays momentary and a hold does not`() {
        control.knob(KnobInput(wheel = 1), true)
        control.knob(KnobInput(), false)
        assertEquals(listOf<Any>(AirPlayKnobState(wheel = 1) to true, AirPlayKnobState() to false), wire.sent)
    }

    @Test
    fun `each media key sends the button index upstream defines`() {
        MediaKey.entries.forEach(control::media)
        assertEquals(
            listOf<Any>(
                "media" to CarPlayMediaButton.PREVIOUS, "media" to CarPlayMediaButton.PLAY_PAUSE, "media" to CarPlayMediaButton.NEXT,
            ),
            wire.sent,
        )
    }

    @Test
    fun `answer is telephony 1 and hang up 3`() {
        control.call(CallKey.ANSWER)
        control.call(CallKey.HANG_UP)
        assertEquals(listOf<Any>("telephony" to 1, "telephony" to 3), wire.sent)
    }

    @Test
    fun `the voice assistant invokes Siri`() {
        control.voiceAssistant()
        assertEquals(listOf<Any>("siri"), wire.sent)
    }

    @Test
    fun `a touch point keeps its id, position and state`() {
        control.touch(listOf(TouchPoint(2, 0.25, 0.75, true), TouchPoint(0, 1.0, 0.0, false)))
        assertEquals(listOf<Any>(listOf(AirPlayContact(2, 0.25, 0.75, true), AirPlayContact(0, 1.0, 0.0, false))), wire.sent)
    }

    @Test
    fun `map zoom calls the session once per step in the direction of the sign`() {
        assertTrue(control.mapZoom(2))
        assertTrue(control.mapZoom(-1))
        assertEquals(listOf<Any>("zoom" to true, "zoom" to true, "zoom" to false), wire.sent)
    }

    @Test
    fun `map zoom reports a phone that has no map`() {
        wire.zoomWorks = false
        assertFalse(control.mapZoom(1))
    }
}
