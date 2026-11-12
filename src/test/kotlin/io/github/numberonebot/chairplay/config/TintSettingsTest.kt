package io.github.numberonebot.chairplay.config

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.nio.file.Files

/** ui.tint: the greys of the settings and the controls window, slate unless chosen (owner, 2026-11-12). */
class TintSettingsTest {
    @Test
    fun `a new settings file and an older one both get slate`() {
        val fresh = Files.createTempDirectory("tint-fresh")
        DesktopConfig(fresh)
        assertEquals("slate", SettingsFile(fresh.resolve("settings.properties")).read()["ui.tint"])

        val older = Files.createTempDirectory("tint-older")
        val file = SettingsFile(older.resolve("settings.properties"))
        file.write(mapOf("ui.autoconnect" to "false"))
        DesktopConfig(older)
        assertEquals("slate", file.read()["ui.tint"])
    }

    @Test
    fun `the tint is one of the five palettes and applies at once`() {
        // white, the light theme (owner, 2026-11-12)
        for (tint in listOf("black", "graphite", "slate", "teal", "white")) {
            assertTrue(tint, EditableSettings.validate(mapOf("ui.tint" to tint), emptyMap()).isEmpty())
        }
        // warm was dropped (owner, 2026-11-12)
        for (tint in listOf("pink", "warm")) {
            assertTrue(tint, "ui.tint" in EditableSettings.validate(mapOf("ui.tint" to tint), emptyMap()))
        }
        assertEquals(Applies.NOW, EditableSettings.applies("ui.tint"))
    }
}
