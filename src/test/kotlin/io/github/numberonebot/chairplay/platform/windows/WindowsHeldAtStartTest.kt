package io.github.numberonebot.chairplay.platform.windows

import org.junit.Assert.*
import org.junit.Test
import org.lwjgl.glfw.GLFW.*

class WindowsHeldAtStartTest {
    @Test fun `held numpad Enter is not a new press when Windows initially reports ordinary Enter`() {
        val initial = WindowsHeldAtStart()
        initial.remember(GLFW_KEY_ENTER, 13)
        assertTrue(initial.contains(GLFW_KEY_KP_ENTER, 13))
        initial.release(GLFW_KEY_KP_ENTER, 13)
        assertFalse(initial.contains(GLFW_KEY_ENTER, 13))
    }

    @Test fun `held numpad navigation is suppressed with Num Lock off`() {
        val initial = WindowsHeldAtStart()
        initial.remember(GLFW_KEY_UP, 0x26)
        assertTrue(initial.contains(GLFW_KEY_KP_8, 0x26))
        initial.release(GLFW_KEY_KP_8, 0x26)
        assertFalse(initial.contains(GLFW_KEY_UP, 0x26))
    }

    @Test fun `releasing one modifier leaves the other side held`() {
        val initial = WindowsHeldAtStart()
        initial.remember(GLFW_KEY_LEFT_CONTROL, 0xa2)
        initial.remember(GLFW_KEY_RIGHT_CONTROL, 0xa3)
        initial.release(GLFW_KEY_LEFT_CONTROL, 0x11)
        assertFalse(GLFW_KEY_LEFT_CONTROL in initial)
        assertTrue(GLFW_KEY_RIGHT_CONTROL in initial)
    }

    @Test fun `missing startup releases are reconciled without leaving a phantom modifier`() {
        val initial = WindowsHeldAtStart()
        initial.remember(GLFW_KEY_LEFT_SHIFT, 0xa0)
        assertEquals(setOf(GLFW_KEY_LEFT_SHIFT), initial.released { false })
        assertFalse(GLFW_KEY_LEFT_SHIFT in initial)
    }
}
