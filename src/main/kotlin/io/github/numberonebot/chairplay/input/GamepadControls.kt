package io.github.numberonebot.chairplay.input

import io.github.numberonebot.chairplay.phone.KnobInput
import org.lwjgl.glfw.GLFW.GLFW_GAMEPAD_BUTTON_A
import org.lwjgl.glfw.GLFW.GLFW_GAMEPAD_BUTTON_B
import org.lwjgl.glfw.GLFW.GLFW_GAMEPAD_BUTTON_DPAD_DOWN
import org.lwjgl.glfw.GLFW.GLFW_GAMEPAD_BUTTON_DPAD_LEFT
import org.lwjgl.glfw.GLFW.GLFW_GAMEPAD_BUTTON_DPAD_RIGHT
import org.lwjgl.glfw.GLFW.GLFW_GAMEPAD_BUTTON_DPAD_UP
import org.lwjgl.glfw.GLFW.GLFW_GAMEPAD_BUTTON_LEFT_BUMPER
import org.lwjgl.glfw.GLFW.GLFW_GAMEPAD_BUTTON_RIGHT_BUMPER

/**
 * A gamepad's buttons as the knob and the AltScreen's zoom, the triggers and the stick being the GPS car's: the D-pad
 * tilts the knob while held, A selects and B goes back, as their keys do; the right shoulder zooms the AltScreen's map
 * in a step a press, the left one out. Each [pass] turns what changed since the last into controls; on the main thread.
 */
class GamepadControls {
    private var down = BooleanArray(0)

    /** The controls for [buttons] as GLFW gives them (GLFW_GAMEPAD_BUTTON_*), null without a gamepad: all let go. */
    fun pass(buttons: BooleanArray?): List<Control> {
        val now = buttons ?: BooleanArray(0)
        val controls = mutableListOf<Control>()
        for (button in BUTTONS) {
            val was = down.getOrElse(button) { false }
            val pressed = now.getOrElse(button) { false }
            if (pressed && !was) press(button)?.let(controls::add)
            // One neutral report lets go of the whole knob, as a key's release does.
            if (!pressed && was && button in KNOB) controls += Control.Knob(KnobInput(), false)
        }
        down = now.copyOf()
        return controls
    }

    private fun press(button: Int): Control? = when (button) {
        GLFW_GAMEPAD_BUTTON_RIGHT_BUMPER -> Control.MapZoom(1)
        GLFW_GAMEPAD_BUTTON_LEFT_BUMPER -> Control.MapZoom(-1)
        else -> KNOB[button]?.let { knobHold(it) }?.let { Control.Knob(it, false) }
    }

    private companion object {
        val KNOB = mapOf(
            GLFW_GAMEPAD_BUTTON_DPAD_UP to ControlAction.KNOB_UP,
            GLFW_GAMEPAD_BUTTON_DPAD_DOWN to ControlAction.KNOB_DOWN,
            GLFW_GAMEPAD_BUTTON_DPAD_LEFT to ControlAction.KNOB_LEFT,
            GLFW_GAMEPAD_BUTTON_DPAD_RIGHT to ControlAction.KNOB_RIGHT,
            GLFW_GAMEPAD_BUTTON_A to ControlAction.SELECT,
            GLFW_GAMEPAD_BUTTON_B to ControlAction.BACK,
        )
        val BUTTONS = KNOB.keys + listOf(GLFW_GAMEPAD_BUTTON_LEFT_BUMPER, GLFW_GAMEPAD_BUTTON_RIGHT_BUMPER)
    }
}
