package io.github.numberonebot.chairplay.input

import io.github.numberonebot.chairplay.phone.KnobInput

/**
 * The on-screen controls window's buttons as a car's controls: pressing and releasing a button makes the Controls a
 * key does (KeyControls). Knob tilts, select, back and home hold until the release, so CarPlay can tell a long press;
 * knob turns, media, calls, Siri, the AltScreen's zoom and the volume act on the press alone.
 */
class ControlPad {
    fun press(action: ControlAction): Control? = when (action) {
        ControlAction.KNOB_NEXT -> Control.Knob(KnobInput(wheel = 1), true)
        ControlAction.KNOB_PREVIOUS -> Control.Knob(KnobInput(wheel = -1), true)
        ControlAction.ALTSCREEN_ZOOM_IN -> Control.MapZoom(1)
        ControlAction.ALTSCREEN_ZOOM_OUT -> Control.MapZoom(-1)
        ControlAction.VOLUME_UP -> Control.VolumeStep(1)
        ControlAction.VOLUME_DOWN -> Control.VolumeStep(-1)
        else -> knobHold(action)?.let { Control.Knob(it, false) } ?: pressControl(action)
    }

    fun release(action: ControlAction): Control? =
        if (knobHold(action) != null) Control.Knob(KnobInput(), false) else null
}
