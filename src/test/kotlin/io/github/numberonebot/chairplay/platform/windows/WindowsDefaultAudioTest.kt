package io.github.numberonebot.chairplay.platform.windows

import org.junit.Assert.assertTrue
import org.junit.Test

class WindowsDefaultAudioTest {
    /** Reads this PC's default devices (never changes them): a name, when there is one, is a whole one. */
    @Test
    fun `the defaults are read from Windows`() {
        val defaults = WindowsDefaultAudio.read()

        listOfNotNull(defaults.output, defaults.input).forEach { assertTrue(it, it.isNotBlank() && '\u0000' !in it) }
    }
}
