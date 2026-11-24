package io.github.numberonebot.chairplay.bridge

import io.github.numberonebot.chairplay.input.Control
import io.github.numberonebot.chairplay.input.ControlAction
import io.github.numberonebot.chairplay.input.knobHold
import io.github.numberonebot.chairplay.input.pressControl
import io.github.numberonebot.chairplay.phone.KnobInput

/** The controls a game sends (telemetry flag bit 9): the buttons it holds now, and three counters it adds steps to. */
data class GameControls(val buttons: Int, val knobSteps: Int, val zoomSteps: Int, val volumeSteps: Int)

/**
 * A game's controls as the app's, each record against the one before it, [send] as the keys' are: the knob's tilts,
 * select, back and home held while their bits are; the other buttons pressed as their bits turn on; and the counters'
 * steps since, as the knob's turns, the AltScreen's zoom and the app's volume. The first record is where the game
 * stands, not news; none (a game quiet, gone or sending no controls) lets go of what it held and starts afresh.
 * Called on the window loop only.
 */
class BridgeControls {
    @Volatile var send: (Control) -> Unit = {}

    private var last: GameControls? = null

    fun update(now: GameControls?) {
        val was = last
        last = now
        if (was == null) return
        if (now == null) {
            if (held(was) != KnobInput()) send(Control.Knob(KnobInput(), false))
            return
        }
        held(now).takeIf { it != held(was) }?.let { send(Control.Knob(it, false)) }
        for ((bit, action) in PRESSES) {
            if (now.buttons and bit != 0 && was.buttons and bit == 0) pressControl(action)?.let(send)
        }
        if (now.buttons and CARPLAY != 0 && was.buttons and CARPLAY == 0) send(Control.CarPlayScreen)
        // Int arithmetic wraps as the game's 32-bit counter does.
        (now.knobSteps - was.knobSteps).takeIf { it != 0 }?.let { send(Control.Knob(KnobInput(wheel = it), true)) }
        (now.zoomSteps - was.zoomSteps).takeIf { it != 0 }?.let { send(Control.MapZoom(it)) }
        (now.volumeSteps - was.volumeSteps).takeIf { it != 0 }?.let { send(Control.VolumeStep(it)) }
    }

    /** The knob as the held buttons hold it, merged as KnobOwners merges its owners'. */
    private fun held(controls: GameControls): KnobInput {
        val states = HOLDS.filter { (bit, _) -> controls.buttons and bit != 0 }.mapNotNull { (_, action) -> knobHold(action) }
        return KnobInput(
            select = states.any { it.select }, home = states.any { it.home }, back = states.any { it.back },
            x = states.sumOf { it.x }.coerceIn(-127, 127), y = states.sumOf { it.y }.coerceIn(-127, 127),
        )
    }

    private companion object {
        /** The buttons' bits in [GameControls.buttons] (docs/INTEGRATIONS.md). */
        val HOLDS = listOf(
            1 shl 0 to ControlAction.KNOB_LEFT, 1 shl 1 to ControlAction.KNOB_RIGHT, 1 shl 2 to ControlAction.KNOB_UP,
            1 shl 3 to ControlAction.KNOB_DOWN, 1 shl 4 to ControlAction.SELECT, 1 shl 5 to ControlAction.BACK,
            1 shl 6 to ControlAction.HOME,
        )
        val PRESSES = listOf(
            1 shl 7 to ControlAction.VOLUME_MUTE, 1 shl 8 to ControlAction.MEDIA_PREVIOUS,
            1 shl 9 to ControlAction.MEDIA_PLAY_PAUSE, 1 shl 10 to ControlAction.MEDIA_NEXT,
            1 shl 11 to ControlAction.CALL_ANSWER, 1 shl 12 to ControlAction.CALL_END, 1 shl 13 to ControlAction.SIRI,
        )
        /** The game's CarPlay button, no key's: its car display shows CarPlay again (BridgeScreen). */
        const val CARPLAY = 1 shl 14
    }
}
