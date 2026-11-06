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

    // Bringing the CarPlay window forward on a call is off by default, and read again at each call.
    @Test fun `the window comes forward on a call only when switched on`() {
        val dir = Files.createTempDirectory("front-on-call")
        writeTestSettings(dir)
        val config = DesktopConfig(dir)
        assertFalse(config.windowOnTop.frontOnCall)
        SettingsFile(dir.resolve("settings.properties")).write(mapOf("window.front.on.call" to "true"))
        assertTrue(config.now().windowOnTop.frontOnCall)
    }

    @Test fun `each window accepts only a boolean and applies it immediately`() {
        for (key in listOf("window.always.on.top", "altscreen.always.on.top", "window.front.on.call")) {
            for (value in listOf("true", "false")) {
                assertTrue(EditableSettings.validate(mapOf(key to value), emptyMap()).isEmpty())
            }
            assertEquals(Applies.NOW, EditableSettings.applies(key))
            assertTrue(key in EditableSettings.validate(mapOf(key to "invalid"), emptyMap()))
            assertThrows(IllegalArgumentException::class.java) { configWith("$key=invalid") }
        }
    }
}
