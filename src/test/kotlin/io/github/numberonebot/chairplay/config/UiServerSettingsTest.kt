package io.github.numberonebot.chairplay.config

import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.Properties

class UiServerSettingsTest {
    private fun settings(vararg entries: Pair<String, String>) = Properties().apply { entries.forEach { (key, value) -> setProperty(key, value) } }

    @Test fun `the port is 7529 when the file has none or a blank one`() {
        assertEquals(7529, Properties().uiServerSettings().port)
        assertEquals(7529, settings("ui.port" to "").uiServerSettings().port)
        assertEquals(7529, settings("ui.port" to "  ").uiServerSettings().port)
    }

    @Test fun `a port from 1 to 65535 is taken`() {
        assertEquals(8080, settings("ui.port" to "8080").uiServerSettings().port)
        assertEquals(1, settings("ui.port" to "1").uiServerSettings().port)
        assertEquals(65535, settings("ui.port" to " 65535 ").uiServerSettings().port)
    }

    @Test fun `any other port stops the start, naming the key`() {
        for (raw in listOf("0", "65536", "x", "-1", "80.5")) {
            val error = assertThrows(IllegalArgumentException::class.java) { settings("ui.port" to raw).uiServerSettings() }
            assertTrue(error.message, "ui.port" in error.message.orEmpty())
        }
    }

    @Test fun `a new settings file has the port`() {
        assertTrue("\nui.port=7529\n" in DesktopConfig.defaultSettingsText())
    }
}
