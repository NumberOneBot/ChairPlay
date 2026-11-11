package io.github.numberonebot.chairplay.config

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.nio.file.Files

/** ui.tint: the greys of the settings and the controls window, black unless chosen. */
class TintSettingsTest {
    @Test
    fun `a new settings file and an older one both get black`() {
        val fresh = Files.createTempDirectory("tint-fresh")
        DesktopConfig(fresh)
        assertEquals("black", SettingsFile(fresh.resolve("settings.properties")).read()["ui.tint"])

        val older = Files.createTempDirectory("tint-older")
        val file = SettingsFile(older.resolve("settings.properties"))
        file.write(mapOf("ui.autoconnect" to "false"))
        DesktopConfig(older)
        assertEquals("black", file.read()["ui.tint"])
    }

    @Test
    fun `the tint is one of the five palettes and applies at once`() {
        for (tint in listOf("black", "graphite", "slate", "teal", "warm")) {
            assertTrue(tint, EditableSettings.validate(mapOf("ui.tint" to tint), emptyMap()).isEmpty())
        }
        assertTrue("ui.tint" in EditableSettings.validate(mapOf("ui.tint" to "pink"), emptyMap()))
        assertEquals(Applies.NOW, EditableSettings.applies("ui.tint"))
    }
}
