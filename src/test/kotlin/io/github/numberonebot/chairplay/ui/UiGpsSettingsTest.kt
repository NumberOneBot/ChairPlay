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

    @Test
    fun `the coordinator's save still refuses a method the app does not know`() {
        val ui = controller()

        assertEquals(setOf("gps.method"), ui.saveGps(mapOf("gps.method" to "bluetooth")).errors.keys)
        assertEquals(setOf("gps.enabled"), ui.saveGps(mapOf("gps.enabled" to "yes")).errors.keys)
    }
}
