package io.github.numberonebot.chairplay.ui

import io.github.numberonebot.chairplay.config.SettingsFile
import io.github.numberonebot.chairplay.phone.LinkStatus
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * UiController: which saved settings wait for the next connection or the next start, as the pages and the toolbar mark
 * them, and which no longer do once set back to the value in force.
 */
class UiPendingMarksTest : UiControllerFixture() {
    /** A connection made now: its bring-up reads the settings file, and its session runs with what it read. */
    private fun UiController.connectedNow() {
        model.bringUp(LinkStatus.Starting)
        connected(SettingsFile(file).read())
        model.sessionActive()
    }

    private val UiController.reconnectPending get() = model.state.reconnectPending
    private val UiController.restartPending get() = model.state.restartPending

    @Test
    fun `a change set back to the value the connection uses waits no more`() {
        val ui = controller()
        ui.connectedNow()

        ui.saveSettings(mapOf("altscreen.enabled" to "true"))
        assertEquals(setOf("altscreen.enabled"), ui.reconnectPending)

        ui.saveSettings(mapOf("altscreen.enabled" to "false"))
        assertEquals(emptySet<String>(), ui.reconnectPending)
    }

    @Test
    fun `a change set back leaves the others waiting`() {
        val ui = controller()
        ui.connectedNow()

        ui.saveSettings(mapOf("altscreen.enabled" to "true", "altscreen.width" to "1280"))
        ui.saveSettings(mapOf("altscreen.enabled" to "false"))

        assertEquals(setOf("altscreen.width"), ui.reconnectPending)
    }

    // The file may hold a value as written by hand, or none; the page saves it as the app reads it.
    @Test
    fun `a value set back counts as the same however the file wrote it`() {
        val ui = controller(settings = "wifi.ssid=Home\nwifi.passphrase=secret123\ndisplay.dock= Driver\n")
        ui.connectedNow()

        ui.saveSettings(mapOf("display.dock" to "bottom", "display.ppi" to "96"))
        assertEquals(setOf("display.dock", "display.ppi"), ui.reconnectPending)

        ui.saveSettings(mapOf("display.dock" to "driver", "display.ppi" to " "))
        assertEquals(emptySet<String>(), ui.reconnectPending)
    }

    @Test
    fun `the next connection puts the changes in force`() {
        val ui = controller()
        ui.connectedNow()
        ui.saveSettings(mapOf("display.dock" to "bottom"))

        ui.model.sessionEnded()
        ui.connectedNow()
        assertEquals(emptySet<String>(), ui.reconnectPending)

        // The old value is now the change.
        ui.saveSettings(mapOf("display.dock" to "auto"))
        assertEquals(setOf("display.dock"), ui.reconnectPending)
    }

    // A connection takes the settings as its bring-up reads them: until it has, a change is not waiting for another.
    @Test
    fun `nothing waits before a connection reads the settings`() {
        val ui = controller()
        assertEquals(Phase.STARTING, ui.model.state.phase)

        ui.saveSettings(mapOf("display.dock" to "bottom"))

        assertEquals(emptySet<String>(), ui.reconnectPending)
    }

    @Test
    fun `nothing waits for a connection once disconnected`() {
        val ui = controller()
        ui.connectedNow()
        ui.saveSettings(mapOf("display.dock" to "bottom"))

        ui.disconnect()
        assertEquals(emptySet<String>(), ui.reconnectPending)

        ui.saveSettings(mapOf("display.corners" to "rounded"))
        assertEquals(emptySet<String>(), ui.reconnectPending)
    }

    @Test
    fun `a restart change set back to the value written by hand waits no more`() {
        val ui = controller(settings = "wifi.ssid=Home\nwifi.passphrase=secret123\nui.network= True\niphone.join.seconds=30\n")

        ui.saveSettings(mapOf("ui.network" to "false", "iphone.join.seconds" to "45"))
        assertEquals(setOf("ui.network", "iphone.join.seconds"), ui.restartPending)

        ui.saveSettings(mapOf("ui.network" to "true", "iphone.join.seconds" to " 30 "))
        assertEquals(emptySet<String>(), ui.restartPending)
    }
}
