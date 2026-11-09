package io.github.numberonebot.chairplay.platform.windows

import org.lwjgl.glfw.GLFW.*

/** Set-1 physical positions, matching GLFW on Windows; never translate through the active text layout. */
internal object WindowsScanCodes {
    fun key(scan: Int, flags: Int, virtual: Int): Int? {
        if (virtual == 255 || scan == 255) return null
        if (virtual == 0x13) return GLFW_KEY_PAUSE
        if (virtual == 0x2c) return GLFW_KEY_PRINT_SCREEN
        if (flags and 4 != 0) return null
        return if (flags and 2 != 0) extended[scan] else plain[scan]
    }

    private val plain = buildMap {
        fun row(start: Int, letters: String) { letters.forEachIndexed { n, letter -> put(start + n, letter.code) } }
        row(0x02, "1234567890")
        row(0x10, "QWERTYUIOP")
        row(0x1e, "ASDFGHJKL")
        row(0x2c, "ZXCVBNM")
        for (i in 0..9) put(0x3b + i, GLFW_KEY_F1 + i)
        for (i in 0..10) put(0x64 + i, GLFW_KEY_F13 + i)
        putAll(mapOf(
            0x01 to GLFW_KEY_ESCAPE, 0x0c to GLFW_KEY_MINUS, 0x0d to GLFW_KEY_EQUAL,
            0x0e to GLFW_KEY_BACKSPACE, 0x0f to GLFW_KEY_TAB,
            0x1a to GLFW_KEY_LEFT_BRACKET, 0x1b to GLFW_KEY_RIGHT_BRACKET, 0x1c to GLFW_KEY_ENTER,
            0x1d to GLFW_KEY_LEFT_CONTROL, 0x27 to GLFW_KEY_SEMICOLON, 0x28 to GLFW_KEY_APOSTROPHE,
            0x29 to GLFW_KEY_GRAVE_ACCENT, 0x2a to GLFW_KEY_LEFT_SHIFT, 0x2b to GLFW_KEY_BACKSLASH,
            0x33 to GLFW_KEY_COMMA, 0x34 to GLFW_KEY_PERIOD, 0x35 to GLFW_KEY_SLASH,
            0x36 to GLFW_KEY_RIGHT_SHIFT, 0x37 to GLFW_KEY_KP_MULTIPLY, 0x38 to GLFW_KEY_LEFT_ALT,
            0x39 to GLFW_KEY_SPACE, 0x3a to GLFW_KEY_CAPS_LOCK, 0x45 to GLFW_KEY_NUM_LOCK,
            0x46 to GLFW_KEY_SCROLL_LOCK, 0x47 to GLFW_KEY_KP_7, 0x48 to GLFW_KEY_KP_8,
            0x49 to GLFW_KEY_KP_9, 0x4a to GLFW_KEY_KP_SUBTRACT, 0x4b to GLFW_KEY_KP_4,
            0x4c to GLFW_KEY_KP_5, 0x4d to GLFW_KEY_KP_6, 0x4e to GLFW_KEY_KP_ADD,
            0x4f to GLFW_KEY_KP_1, 0x50 to GLFW_KEY_KP_2, 0x51 to GLFW_KEY_KP_3,
            0x52 to GLFW_KEY_KP_0, 0x53 to GLFW_KEY_KP_DECIMAL, 0x56 to GLFW_KEY_WORLD_2,
            0x57 to GLFW_KEY_F11, 0x58 to GLFW_KEY_F12, 0x59 to GLFW_KEY_KP_EQUAL, 0x76 to GLFW_KEY_F24,
        ))
    }
    private val extended = mapOf(
        0x1c to GLFW_KEY_KP_ENTER, 0x1d to GLFW_KEY_RIGHT_CONTROL, 0x35 to GLFW_KEY_KP_DIVIDE,
        0x37 to GLFW_KEY_PRINT_SCREEN, 0x38 to GLFW_KEY_RIGHT_ALT,
        0x45 to GLFW_KEY_NUM_LOCK,
        0x47 to GLFW_KEY_HOME, 0x48 to GLFW_KEY_UP, 0x49 to GLFW_KEY_PAGE_UP,
        0x4b to GLFW_KEY_LEFT, 0x4d to GLFW_KEY_RIGHT, 0x4f to GLFW_KEY_END,
        0x50 to GLFW_KEY_DOWN, 0x51 to GLFW_KEY_PAGE_DOWN, 0x52 to GLFW_KEY_INSERT, 0x53 to GLFW_KEY_DELETE,
        0x5b to GLFW_KEY_LEFT_SUPER, 0x5c to GLFW_KEY_RIGHT_SUPER, 0x5d to GLFW_KEY_MENU,
    )
}
