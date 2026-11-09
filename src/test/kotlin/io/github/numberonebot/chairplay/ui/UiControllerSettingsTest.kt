package io.github.numberonebot.chairplay.ui

import io.github.numberonebot.chairplay.config.SettingsFile
import io.github.numberonebot.chairplay.config.SetupStore
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.nio.file.Files

/** UiController: settings.properties as the pages read and save it, and what a save starts. */
class UiControllerSettingsTest : UiControllerFixture() {
    // The iPhone asks how its screen looks at each connection: a change waits for the next one, which the page marks.
    @Test
    fun `the CarPlay screen's corners and edges wait for the next connection`() {
        val ui = controller()
        ui.connected(SettingsFile(file).read())

        ui.saveSettings(mapOf("display.corners" to "rounded", "display.safe.draw.outside" to "false"))

        assertEquals(listOf("display.corners", "display.safe.draw.outside"), ui.state()["reconnectPending"])
        assertEquals(emptyList<String>(), ui.state()["restartPending"])
    }

    // CarPlay turns dark or light while it runs, as a car's headlights tell it to: a saved theme goes out at once.
    @Test
    fun `a saved theme reaches the app at once and waits for nothing`() {
        val saved = mutableListOf<Map<String, String>>()
        val ui = controller(onSaved = { saved += it })

        ui.saveSettings(mapOf("display.theme" to "dark"))

        assertEquals(listOf(mapOf("display.theme" to "dark")), saved)
        assertEquals(emptyList<String>(), ui.state()["restartPending"])
        assertEquals(emptyList<String>(), ui.state()["reconnectPending"])
    }

    // Get started owns the first Connect: a network saved before the password must not start a bring-up without it.
    @Test
    fun `the first Wi-Fi network waits for Connect while Get started is open`() {
        val ui = controller(settings = "wifi.ssid=\nwifi.passphrase=\nui.autoconnect=true\n", setupStore = SetupStore(dir))

        ui.saveSettings(mapOf("wifi.ssid" to "Car"))

        assertEquals(0, connects.get())
        assertEquals(Phase.IDLE, ui.model.state.phase)
    }

    // The state, the settings, and the log may be seen by others: there the stored password is only "set" or "not set".
    @Test
    fun `the password stays out of the state, the settings, and the log`() {
        val ui = controller()

        ui.saveSettings(mapOf("wifi.passphrase" to "brand new pass"))

        val texts = listOf(Json.write(ui.state()), Json.write(ui.settings())) + logged
        for (text in texts) assertFalse(text, "secret123" in text || "brand new pass" in text)
        assertEquals(true, (ui.state()["wifi"] as Map<*, *>)["passphraseSet"])
        assertEquals(true, ui.settings()["passphraseSet"])
        assertFalse((ui.settings()["values"] as Map<*, *>).containsKey("wifi.passphrase"))
    }

    // It is in settings.properties as written: the eye beside it asks for it, and gets it alone.
    @Test
    fun `the saved password is given when asked for`() {
        val ui = controller()
        assertEquals("secret123", ui.savedPassphrase())

        ui.saveSettings(mapOf("wifi.passphrase" to "brand new pass"))
        assertEquals("brand new pass", ui.savedPassphrase())
        assertNull(controller(settings = "wifi.ssid=Home\n").savedPassphrase())
        assertFalse(logged.any { "brand new pass" in it })
    }

    @Test
    fun `a valid change is written into the file`() {
        val ui = controller()

        val result = ui.saveSettings(mapOf("window.width" to "1600", "altscreen.enabled" to "true"))

        assertTrue(result.errors.isEmpty())
        val text = Files.readString(file)
        assertTrue(text, "window.width=1600\n" in text && "altscreen.enabled=true\n" in text)
        assertEquals(mapOf("window.width" to "restart", "altscreen.enabled" to "reconnect"), result.applies)
    }

    @Test
    fun `an invalid change leaves the file alone and says why`() {
        val ui = controller()
        val before = Files.readString(file)

        val result = ui.saveSettings(mapOf("window.width" to "1600", "altscreen.width" to "1441"))

        assertEquals(setOf("altscreen.width"), result.errors.keys)
        assertEquals(before, Files.readString(file))
    }

    @Test
    fun `autoshow applies at once`() {
        val ui = controller()

        ui.saveSettings(mapOf("ui.autoshow" to "false"))

        assertFalse(ui.model.state.autoshow)
    }

    @Test
    fun `restart-only changes are listed until they are changed back`() {
        val ui = controller()

        ui.saveSettings(mapOf("window.width" to "1600"))
        assertEquals(setOf("window.width"), ui.model.state.restartPending)

        ui.saveSettings(mapOf("window.width" to ""))
        assertEquals(emptySet<String>(), ui.model.state.restartPending)
    }

    @Test
    fun `a Wi-Fi change during a session waits for a reconnect`() {
        val ui = controller()
        ui.connected(SettingsFile(file).read())
        ui.model.sessionActive()

        ui.saveSettings(mapOf("wifi.ssid" to "Garage"))

        assertEquals(setOf("wifi.ssid"), ui.model.state.reconnectPending)
        ui.reconnect()
        assertEquals(1, reconnects.get())
    }

    // With ui.autoconnect the app connects as soon as it has a network to connect on.
    @Test
    fun `the first Wi-Fi network starts the connection`() {
        val ui = controller(settings = "wifi.ssid=\nwifi.passphrase=\nwifi.security=WPA_WPA2\n")
        assertEquals(Phase.SETUP, ui.model.state.phase)

        ui.saveSettings(mapOf("wifi.ssid" to "Home", "wifi.passphrase" to "secret123"))

        assertEquals(1, connects.get())
        assertEquals(Phase.STARTING, ui.model.state.phase)
    }

    @Test
    fun `without autoconnect the first Wi-Fi network waits for Connect`() {
        val ui = controller(settings = "wifi.ssid=\n", autoconnect = false)

        ui.saveSettings(mapOf("wifi.ssid" to "Cafe", "wifi.security" to "NONE"))

        assertEquals(0, connects.get())
        assertEquals(Phase.IDLE, ui.model.state.phase)
        assertNull(ui.connect())
        assertEquals(1, connects.get())
        assertEquals(Phase.STARTING, ui.model.state.phase)
    }

    @Test
    fun `Connect needs a Wi-Fi network`() {
        val ui = controller(settings = "wifi.ssid=\n", autoconnect = false)

        assertNotNull(ui.connect())
        assertEquals(0, connects.get())
    }

    // The pages show choices as words; the file keeps them in lower case, as the app reads them.
    @Test
    fun `choices are saved in lower case`() {
        val ui = controller()

        val result = ui.saveSettings(mapOf("display.theme" to " Dark ", "display.dock" to "Driver", "altscreen.cards" to "LEFT",
            "display.right.hand.drive" to "True", "device.name" to " My Car "))

        assertTrue(result.errors.toString(), result.errors.isEmpty())
        val text = Files.readString(file)
        for (line in listOf("display.theme=dark", "display.dock=driver", "altscreen.cards=left", "display.right.hand.drive=true", "device.name=My Car")) {
            assertTrue(line, "$line\n" in text)
        }
    }

    // A custom rectangle off the grid is saved on it, as the app would read it.
    @Test
    fun `a custom rectangle is saved snapped`() {
        val result = controller().saveSettings(mapOf("altscreen.card.area.custom" to "413, 37, 619, 469"))

        assertTrue(result.errors.toString(), result.errors.isEmpty())
        assertTrue("altscreen.card.area.custom=412, 36, 616, 468\n" in Files.readString(file))
    }

    // The Audio page offers the devices Windows has, by the names the settings match.
    @Test
    fun `the settings carry the sound devices`() {
        val audio = controller().settings()["audio"] as Map<*, *>

        assertEquals(listOf("Speakers (Realtek)"), audio["outputs"])
        assertEquals(listOf("Microphone Array"), audio["inputs"])
        val none = controller(audio = { throw IllegalStateException("no Java Sound") }).settings()["audio"] as Map<*, *>
        assertEquals(emptyList<String>(), none["outputs"])
    }

    // Windows default names the device it stands for, so the page can show where the sound goes.
    @Test
    fun `the settings carry the devices Windows uses by default`() {
        val names = AudioDeviceNames(listOf("Speakers (Realtek)"), listOf("Microphone Array"), "Speakers (Realtek(R) Audio)", null)
        val audio = controller(audio = { names }).settings()["audio"] as Map<*, *>

        assertEquals("Speakers (Realtek(R) Audio)", audio["defaultOutput"])
        assertEquals(null, audio["defaultInput"])
    }

    // Listing the devices asks Java Sound and Windows: once per run, not on every page that reads the settings.
    @Test
    fun `the sound devices are listed once`() {
        var lists = 0
        val ui = controller(audio = { lists++; AudioDeviceNames(listOf("Speakers (Realtek)"), emptyList()) })

        repeat(3) { ui.settings() }

        assertEquals(1, lists)
    }

    @Test
    fun `Disconnect ends the session and leaves the app idle`() {
        val ui = controller()
        ui.model.sessionActive()

        ui.disconnect()

        assertEquals(1, disconnects.get())
        assertEquals(Phase.IDLE, ui.model.state.phase)
    }

    @Test
    fun `every change moves the version on`() {
        val ui = controller()
        val before = ui.version

        ui.model.sessionActive()

        assertTrue(ui.version > before)
        assertEquals(ui.version, ui.awaitChange(before, 10))
    }

    // The state does not stream the settings a page reads; a settings version in it tells an open page to re-read them.
    // Only a settings save moves it on (so a key like Shift+F10 reaches the page), not every other change.
    @Test
    fun `a settings save moves the settings version on, a plain change does not`() {
        val ui = controller()
        val before = ui.state()["settingsVersion"] as Long

        ui.model.sessionActive()
        assertEquals(before, ui.state()["settingsVersion"])

        ui.saveSettings(mapOf("gps.gauges" to "small"))
        assertTrue((ui.state()["settingsVersion"] as Long) > before)
    }
}
