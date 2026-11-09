package io.github.numberonebot.chairplay.media

import io.github.numberonebot.chairplay.config.Theme
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class NightModeTest {
    @Test
    fun `the theme decides night mode, system following its dark mode`() {
        assertEquals(false, nightMode(Theme.LIGHT) { true })
        assertEquals(true, nightMode(Theme.DARK) { false })
        assertEquals(true, nightMode(Theme.SYSTEM) { true })
        assertEquals(false, nightMode(Theme.SYSTEM) { false })
        assertNull(nightMode(Theme.PHONE) { true })
    }

    // Without the registry value (older Windows, other systems) light is the safe answer.
    @Test
    fun `an unreadable system theme counts as light`() {
        assertEquals(false, nightMode(Theme.SYSTEM) { null })
    }
}
