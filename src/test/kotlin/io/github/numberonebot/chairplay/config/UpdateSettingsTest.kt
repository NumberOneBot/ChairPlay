package io.github.numberonebot.chairplay.config

import org.junit.Assert.*
import org.junit.Test
import java.nio.file.Files

class UpdateSettingsTest {
    @Test fun `update checks default on for new and existing installations and remember opt out`() {
        val dir = Files.createTempDirectory("update-settings")
        val file = SettingsFile(dir.resolve("settings.properties"))
        file.write(mapOf("ui.autoconnect" to "false"))
        DesktopConfig(dir)
        assertEquals("true", file.read()["updates.check.on.start"])
        file.write(mapOf("updates.check.on.start" to "false"))
        DesktopConfig(dir)
        assertEquals("false", file.read()["updates.check.on.start"])
        val fresh = Files.createTempDirectory("update-defaults")
        DesktopConfig(fresh)
        assertEquals("true", SettingsFile(fresh.resolve("settings.properties")).read()["updates.check.on.start"])
    }

    @Test fun `the startup preference accepts only booleans without requiring a restart now`() {
        val key = "updates.check.on.start"
        for (value in listOf("true", "false")) {
            assertTrue(EditableSettings.validate(mapOf(key to value), emptyMap()).isEmpty())
        }
        assertEquals(Applies.NOW, EditableSettings.applies(key))
        assertTrue(key in EditableSettings.validate(mapOf(key to "invalid"), emptyMap()))
    }
}
