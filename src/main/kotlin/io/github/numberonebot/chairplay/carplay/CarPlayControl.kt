package io.github.numberonebot.chairplay.carplay

import com.shilapi.xcertplay.airplay.AirPlayContact
import com.shilapi.xcertplay.airplay.AirPlayKnobState
import com.shilapi.xcertplay.airplay.AirPlaySession
import com.shilapi.xcertplay.airplay.CarPlayMediaButton
import io.github.numberonebot.chairplay.phone.CallKey
import io.github.numberonebot.chairplay.phone.KnobInput
import io.github.numberonebot.chairplay.phone.MediaKey
import io.github.numberonebot.chairplay.phone.PhoneControl
import io.github.numberonebot.chairplay.phone.TouchPoint
import kotlin.math.abs

/** The calls of AirPlaySession that [CarPlayControl] makes, so a test can record them. */
internal interface CarPlayWire {
    fun sendKnob(state: AirPlayKnobState, momentary: Boolean)
    fun sendMedia(index: Int)
    fun sendTelephony(index: Int)
    fun invokeSiri()
    fun sendTouch(contacts: List<AirPlayContact>)
    fun changeMapZoomLevel(zoomIn: Boolean): Boolean
}

private class SessionWire(private val session: AirPlaySession) : CarPlayWire {
    override fun sendKnob(state: AirPlayKnobState, momentary: Boolean) = session.sendKnob(state, momentary)
    override fun sendMedia(index: Int) = session.sendMedia(index)
    override fun sendTelephony(index: Int) = session.sendTelephony(index)
    override fun invokeSiri() = session.invokeSiri()
    override fun sendTouch(contacts: List<AirPlayContact>) { session.sendTouch(contacts) }
    override fun changeMapZoomLevel(zoomIn: Boolean) = session.changeMapZoomLevel(zoomIn)
}

/** The keys, the knob and the touches of a CarPlay [session]: AirPlaySession's HID and command calls. */
class CarPlayControl internal constructor(private val wire: CarPlayWire) : PhoneControl {
    constructor(session: AirPlaySession) : this(SessionWire(session))

    override fun knob(input: KnobInput, momentary: Boolean) = wire.sendKnob(
        AirPlayKnobState(input.select, input.home, input.back, input.x, input.y, input.wheel), momentary,
    )

    override fun media(key: MediaKey) = wire.sendMedia(
        when (key) {
            MediaKey.PREVIOUS -> CarPlayMediaButton.PREVIOUS
            MediaKey.PLAY_PAUSE -> CarPlayMediaButton.PLAY_PAUSE
            MediaKey.NEXT -> CarPlayMediaButton.NEXT
        },
    )

    /** Upstream CarPlayController's TELEPHONY_HOOK_SWITCH and TELEPHONY_DROP. */
    override fun call(key: CallKey) = wire.sendTelephony(
        when (key) {
            CallKey.ANSWER -> 1
            CallKey.HANG_UP -> 3
        },
    )

    override fun voiceAssistant() = wire.invokeSiri()

    override fun touch(points: List<TouchPoint>) = wire.sendTouch(points.map { AirPlayContact(it.id, it.x, it.y, it.down) })

    override fun mapZoom(steps: Int): Boolean {
        var sent = true
        repeat(abs(steps)) { sent = wire.changeMapZoomLevel(zoomIn = steps > 0) && sent }
        return sent
    }
}
