package io.github.numberonebot.chairplay.input

import io.github.numberonebot.chairplay.phone.KnobInput
import org.junit.Assert.assertEquals
import org.junit.Test
import org.lwjgl.glfw.GLFW.GLFW_GAMEPAD_BUTTON_A
import org.lwjgl.glfw.GLFW.GLFW_GAMEPAD_BUTTON_B
import org.lwjgl.glfw.GLFW.GLFW_GAMEPAD_BUTTON_DPAD_LEFT
import org.lwjgl.glfw.GLFW.GLFW_GAMEPAD_BUTTON_DPAD_UP
import org.lwjgl.glfw.GLFW.GLFW_GAMEPAD_BUTTON_LEFT_BUMPER
import org.lwjgl.glfw.GLFW.GLFW_GAMEPAD_BUTTON_RIGHT_BUMPER

class GamepadControlsTest {
    private val controls = GamepadControls()
    private val neutral = Control.Knob(KnobInput(), false)

    private fun pressed(vararg buttons: Int) = BooleanArray(15) { it in buttons }

    // The D-pad tilts the knob while held, A selects and B goes back, as their keys do; letting go lets the knob go.
    @Test
    fun `the D-pad is the knob's cross, A select and B back`() {
        assertEquals(emptyList<Control>(), controls.pass(pressed()))
        assertEquals(listOf(Control.Knob(KnobInput(y = -127), false)), controls.pass(pressed(GLFW_GAMEPAD_BUTTON_DPAD_UP)))
        // Held, it sends nothing more.
        assertEquals(emptyList<Control>(), controls.pass(pressed(GLFW_GAMEPAD_BUTTON_DPAD_UP)))
        assertEquals(listOf(neutral), controls.pass(pressed()))
        assertEquals(listOf(Control.Knob(KnobInput(x = -127), false)), controls.pass(pressed(GLFW_GAMEPAD_BUTTON_DPAD_LEFT)))
        assertEquals(listOf(neutral), controls.pass(pressed()))
        assertEquals(listOf(Control.Knob(KnobInput(select = true), false)), controls.pass(pressed(GLFW_GAMEPAD_BUTTON_A)))
        assertEquals(listOf(neutral), controls.pass(pressed()))
        assertEquals(listOf(Control.Knob(KnobInput(back = true), false)), controls.pass(pressed(GLFW_GAMEPAD_BUTTON_B)))
        assertEquals(listOf(neutral), controls.pass(pressed()))
    }

    // The shoulders zoom the AltScreen's map a step a press: the right one in, the left one out.
    @Test
    fun `the shoulders zoom the AltScreen in and out a step a press`() {
        assertEquals(listOf(Control.MapZoom(1)), controls.pass(pressed(GLFW_GAMEPAD_BUTTON_RIGHT_BUMPER)))
        assertEquals(emptyList<Control>(), controls.pass(pressed(GLFW_GAMEPAD_BUTTON_RIGHT_BUMPER)))
        assertEquals(emptyList<Control>(), controls.pass(pressed()))
        assertEquals(listOf(Control.MapZoom(-1)), controls.pass(pressed(GLFW_GAMEPAD_BUTTON_LEFT_BUMPER)))
    }

    // A gamepad gone with a button down lets go of the knob, so it is not left tilted.
    @Test
    fun `a gamepad gone with a button down lets go of the knob`() {
        controls.pass(pressed(GLFW_GAMEPAD_BUTTON_A))
        assertEquals(listOf(neutral), controls.pass(null))
        assertEquals(emptyList<Control>(), controls.pass(null))
    }
}
