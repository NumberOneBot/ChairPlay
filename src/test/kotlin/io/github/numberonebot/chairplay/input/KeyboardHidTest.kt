package io.github.numberonebot.chairplay.input

import org.junit.Assert.assertEquals
import org.junit.Test

class KeyboardHidTest {
    private fun bytes(vararg values: Int) = values.toList()

    // Announced as a keyboard, the device made iOS count a hardware keyboard and hide the iPhone's own on-screen one
    // for the whole session. Announced as a Consumer Control holding the same keys, CarPlay's text fields still take
    // them, editing and selection included, and the iPhone keeps its on-screen keyboard (seen on iOS 27.2, 2026-11-05).
    @Test
    fun `the keyboard is announced as a consumer control, not a keyboard`() {
        val head = KeyboardHid.descriptor().take(6).map { it.toInt() and 255 }

        assertEquals(bytes(0x05, 0x0c, 0x09, 0x01, 0xa1, 0x01), head)
    }
}
