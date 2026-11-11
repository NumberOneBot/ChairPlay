package io.github.numberonebot.chairplay.ui

import io.github.numberonebot.chairplay.config.SettingsFile
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** The GPS output's own settings: only its coordinator writes them, never a page's settings save (A0 §3). */
class UiGpsSettingsTest : UiControllerFixture() {
    @Test
    fun `a page's save cannot change the GPS method, switch or prepared iPhone`() {
        val saved = mutableListOf<Map<String, String>>()
        val ui = controller(onSaved = { saved += it })

        val result = ui.saveSettings(mapOf("gps.method" to "usb", "gps.enabled" to "true", "gps.usb.device" to "X"))

        assertEquals(setOf("gps.method", "gps.enabled", "gps.usb.device"), result.errors.keys)
        val values = SettingsFile(file).read()
        assertTrue(values["gps.method"] == null && values["gps.enabled"] == null && values["gps.usb.device"] == null)
        assertEquals(emptyList<Map<String, String>>(), saved)
    }

    @Test
    fun `the coordinator's save writes them and tells the app`() {
        val saved = mutableListOf<Map<String, String>>()
        val ui = controller(onSaved = { saved += it })

        val result = ui.saveGps(mapOf("gps.method" to "usb", "gps.enabled" to "true", "gps.usb.device" to "00008110-000A"))

        assertEquals(emptyMap<String, String>(), result.errors)
        val values = SettingsFile(file).read()
        assertEquals("usb", values["gps.method"])
        assertEquals("true", values["gps.enabled"])
        assertEquals("00008110-000A", values["gps.usb.device"])
        assertEquals(listOf(mapOf("gps.method" to "usb", "gps.enabled" to "true", "gps.usb.device" to "00008110-000A")), saved)
    }

    // D8: a method chosen waits for the next connection, marked as any reconnect setting is.
    @Test
    fun `a method chosen waits for the next connection`() {
        val ui = controller()
        ui.saveGps(mapOf("gps.method" to "carplay", "gps.enabled" to "true"))
        ui.connected(SettingsFile(file).read())

        ui.saveGps(mapOf("gps.method" to "usb"))

        assertEquals(listOf("gps.method"), ui.state()["reconnectPending"])
        ui.saveGps(mapOf("gps.method" to "carplay"))
        assertEquals(emptyList<String>(), ui.state()["reconnectPending"])
    }

    // D4: the switch waits for a reconnect only where CarPlay identifies with the GPS; USB's acts at once.
    @Test
    fun `the switch waits for a reconnect with CarPlay's method only`() {
        val ui = controller()
        ui.saveGps(mapOf("gps.method" to "carplay", "gps.enabled" to "false"))
        ui.connected(SettingsFile(file).read())
        ui.saveGps(mapOf("gps.enabled" to "true"))
        assertEquals(listOf("gps.enabled"), ui.state()["reconnectPending"])

        ui.saveGps(mapOf("gps.method" to "usb", "gps.enabled" to "false"))
        ui.connected(SettingsFile(file).read())
        ui.saveGps(mapOf("gps.enabled" to "true"))
        assertEquals(emptyList<String>(), ui.state()["reconnectPending"])
    }

    // A method and the switch both changed since the connection: each is marked.
    @Test
    fun `the switch turned off with a new method chosen is marked too`() {
        val ui = controller()
        ui.saveGps(mapOf("gps.method" to "carplay", "gps.enabled" to "true"))
        ui.connected(SettingsFile(file).read())

        ui.saveGps(mapOf("gps.method" to "usb"))
        ui.saveGps(mapOf("gps.enabled" to "false"))

        assertEquals(listOf("gps.enabled", "gps.method"), ui.state()["reconnectPending"])
    }

    @Test
    fun `the coordinator's save still refuses a method the app does not know`() {
        val ui = controller()

        assertEquals(setOf("gps.method"), ui.saveGps(mapOf("gps.method" to "bluetooth")).errors.keys)
        assertEquals(setOf("gps.enabled"), ui.saveGps(mapOf("gps.enabled" to "yes")).errors.keys)
    }
}
