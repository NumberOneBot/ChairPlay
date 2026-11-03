package io.github.numberonebot.chairplay.config

import org.junit.Assert.*
import org.junit.Test
import java.nio.file.Files

class AlwaysOnTopSettingsTest {
    @Test fun `preferences default off and persist independently across reload and restart`() {
        val dir = Files.createTempDirectory("always-on-top")
        writeTestSettings(dir)
        val initial = DesktopConfig(dir)
        assertFalse(initial.windowOnTop.main)
        assertFalse(initial.windowOnTop.altscreen)
        val file = SettingsFile(dir.resolve("settings.properties"))
        file.write(mapOf("window.always.on.top" to "true"))
        assertTrue(initial.now().windowOnTop.main)
        assertFalse(initial.now().windowOnTop.altscreen)
        val restarted = DesktopConfig(dir)
        assertTrue(restarted.windowOnTop.main)
        file.write(mapOf("window.always.on.top" to "false", "altscreen.always.on.top" to "true"))
        assertFalse(restarted.now().windowOnTop.main)
        assertTrue(restarted.now().windowOnTop.altscreen)
    }

    @Test fun `each window accepts only a boolean and applies it immediately`() {
        for (key in listOf("window.always.on.top", "altscreen.always.on.top")) {
            for (value in listOf("true", "false")) {
                assertTrue(EditableSettings.validate(mapOf(key to value), emptyMap()).isEmpty())
            }
            assertEquals(Applies.NOW, EditableSettings.applies(key))
            assertTrue(key in EditableSettings.validate(mapOf(key to "invalid"), emptyMap()))
            assertThrows(IllegalArgumentException::class.java) { configWith("$key=invalid") }
        }
    }
}
