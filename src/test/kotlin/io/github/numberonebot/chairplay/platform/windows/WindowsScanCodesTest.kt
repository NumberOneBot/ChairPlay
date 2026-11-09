package io.github.numberonebot.chairplay.platform.windows

import org.junit.Assert.*
import org.junit.Test
import org.lwjgl.glfw.GLFW.*

class WindowsScanCodesTest {
    @Test fun `physical letters ignore the active keyboard layout`() {
        assertEquals(GLFW_KEY_S, WindowsScanCodes.key(0x1f, 0, 0x53))
        assertEquals(GLFW_KEY_S, WindowsScanCodes.key(0x1f, 0, 0))
    }
    @Test fun `extended flag distinguishes keypad from navigation and enter`() {
        assertEquals(GLFW_KEY_KP_8, WindowsScanCodes.key(0x48, 0, 0x26))
        assertEquals(GLFW_KEY_UP, WindowsScanCodes.key(0x48, 2, 0x26))
        assertEquals(GLFW_KEY_ENTER, WindowsScanCodes.key(0x1c, 0, 13))
        assertEquals(GLFW_KEY_KP_ENTER, WindowsScanCodes.key(0x1c, 2, 13))
    }
    @Test fun `fake prefixes are ignored and pause and print screen remain distinct`() {
        assertNull(WindowsScanCodes.key(0x2a, 2, 255))
        assertEquals(GLFW_KEY_PAUSE, WindowsScanCodes.key(0x45, 4, 0x13))
        assertEquals(GLFW_KEY_PRINT_SCREEN, WindowsScanCodes.key(0x37, 2, 0x2c))
    }
}
