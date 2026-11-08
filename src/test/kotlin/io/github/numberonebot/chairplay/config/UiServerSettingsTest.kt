package io.github.numberonebot.chairplay.config

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
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

    @Test fun `the network is off unless the file turns it on`() {
        assertFalse(Properties().uiServerSettings().network)
        assertTrue(settings("ui.network" to " True ").uiServerSettings().network)
        assertFalse(settings("ui.network" to "false").uiServerSettings().network)
        val error = assertThrows(IllegalArgumentException::class.java) { settings("ui.network" to "maybe").uiServerSettings() }
        assertTrue(error.message, "ui.network" in error.message.orEmpty())
    }

    // The firewall opens the UI's port only while the home network may reach it.
    @Test fun `the firewall's port is the UI's only while the network is on`() {
        assertNull(UiServerSettings(7529, network = false).firewallPort)
        assertEquals(7529, UiServerSettings(7529, network = true).firewallPort)
    }

    @Test fun `the network switch applies at the next start and takes only true or false`() {
        assertEquals(Applies.RESTART, EditableSettings.applies("ui.network"))
        assertTrue(EditableSettings.validate(mapOf("ui.network" to "maybe"), emptyMap()).containsKey("ui.network"))
        assertFalse(EditableSettings.validate(mapOf("ui.network" to "true"), emptyMap()).containsKey("ui.network"))
    }

    @Test fun `a new settings file has the network off`() {
        assertTrue("\nui.network=false\n" in DesktopConfig.defaultSettingsText())
    }
}
