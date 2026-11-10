package io.github.numberonebot.chairplay.media

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import org.lwjgl.glfw.GLFW.GLFW_KEY_ESCAPE
import org.lwjgl.glfw.GLFW.GLFW_MOUSE_BUTTON_4
import org.lwjgl.glfw.GLFW.GLFW_MOUSE_BUTTON_5
import org.lwjgl.glfw.GLFW.GLFW_MOUSE_BUTTON_LEFT
import org.lwjgl.glfw.GLFW.GLFW_MOUSE_BUTTON_MIDDLE
import org.lwjgl.glfw.GLFW.GLFW_MOUSE_BUTTON_RIGHT

class WindowInputTest {
    // The mouse's back button (XButton1) goes back as Escape does, whatever the keys are set to; the left button touches,
    // and the others do nothing.
    @Test
    fun `the mouse's back button is Escape`() {
        assertEquals(GLFW_KEY_ESCAPE, mouseButtonKey(GLFW_MOUSE_BUTTON_4))
        for (button in listOf(GLFW_MOUSE_BUTTON_LEFT, GLFW_MOUSE_BUTTON_RIGHT, GLFW_MOUSE_BUTTON_MIDDLE, GLFW_MOUSE_BUTTON_5)) {
            assertNull("$button", mouseButtonKey(button))
        }
    }
}
