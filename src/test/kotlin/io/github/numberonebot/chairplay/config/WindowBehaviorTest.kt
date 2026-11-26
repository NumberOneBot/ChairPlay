package io.github.numberonebot.chairplay.config

import org.junit.Assert.*
import org.junit.Test
import java.nio.file.Files

class WindowBehaviorTest {
    @Test fun `preferences default off and persist independently across reload and restart`() {
        val dir = Files.createTempDirectory("always-on-top")
        writeTestSettings(dir)
        val initial = DesktopConfig(dir)
        assertFalse(initial.windowBehavior.mainOnTop)
        assertFalse(initial.windowBehavior.altscreenOnTop)
        val file = SettingsFile(dir.resolve("settings.properties"))
        file.write(mapOf("window.always.on.top" to "true"))
        assertTrue(initial.now().windowBehavior.mainOnTop)
        assertFalse(initial.now().windowBehavior.altscreenOnTop)
        val restarted = DesktopConfig(dir)
        assertTrue(restarted.windowBehavior.mainOnTop)
        file.write(mapOf("window.always.on.top" to "false", "altscreen.always.on.top" to "true"))
        assertFalse(restarted.now().windowBehavior.mainOnTop)
        assertTrue(restarted.now().windowBehavior.altscreenOnTop)
    }

    // Bringing the CarPlay window forward on a call is off by default, and read again at each call.
    @Test fun `the window comes forward on a call only when switched on`() {
        val dir = Files.createTempDirectory("front-on-call")
        writeTestSettings(dir)
        val config = DesktopConfig(dir)
        assertFalse(config.windowBehavior.frontOnCall)
        SettingsFile(dir.resolve("settings.properties")).write(mapOf("window.front.on.call" to "true"))
        assertTrue(config.now().windowBehavior.frontOnCall)
    }

    @Test fun `each window accepts only a boolean and applies it immediately`() {
        for (key in listOf("window.always.on.top", "altscreen.always.on.top", "window.front.on.call", "window.tray")) {
            for (value in listOf("true", "false")) {
                assertTrue(EditableSettings.validate(mapOf(key to value), emptyMap()).isEmpty())
            }
            assertEquals(Applies.NOW, EditableSettings.applies(key))
            assertTrue(key in EditableSettings.validate(mapOf(key to "invalid"), emptyMap()))
            assertThrows(IllegalArgumentException::class.java) { configWith("$key=invalid") }
        }
    }
}
