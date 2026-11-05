package io.github.numberonebot.chairplay.input

import org.junit.Assert.assertEquals
import org.junit.Test
import org.lwjgl.glfw.GLFW.GLFW_KEY_A
import org.lwjgl.glfw.GLFW.GLFW_KEY_LEFT_ALT
import org.lwjgl.glfw.GLFW.GLFW_KEY_LEFT_SHIFT
import org.lwjgl.glfw.GLFW.GLFW_MOD_ALT
import org.lwjgl.glfw.GLFW.GLFW_MOD_SHIFT
import org.lwjgl.glfw.GLFW.GLFW_PRESS

class KeyboardStateTest {
    private val usageA = 4

    // Windows may keep a modifier's release from the window, as switching the layout with Alt+Shift does. The next
    // key's own modifier state, which GLFW reads from Windows, says the modifier is up: it must not reach the iPhone as
    // held, where Alt is Option and every letter came out as a symbol (÷, ©, ≠).
    @Test
    fun `a modifier whose release never came is let go at the next key`() {
        val state = KeyboardState()
        state.event(GLFW_KEY_LEFT_ALT, GLFW_PRESS, GLFW_MOD_ALT)

        val typed = state.event(GLFW_KEY_A, GLFW_PRESS, 0).last()

        assertEquals(0, typed[0].toInt())
        assertEquals(usageA, typed[2].toInt())
    }

    @Test
    fun `a modifier still down stays with the keys pressed under it`() {
        val state = KeyboardState()
        state.event(GLFW_KEY_LEFT_SHIFT, GLFW_PRESS, GLFW_MOD_SHIFT)

        val typed = state.event(GLFW_KEY_A, GLFW_PRESS, GLFW_MOD_SHIFT).last()

        assertEquals(2, typed[0].toInt())
        assertEquals(usageA, typed[2].toInt())
    }
}
