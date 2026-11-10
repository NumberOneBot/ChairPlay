package io.github.numberonebot.chairplay.config

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class EditableSettingsTest {
    private val current = mapOf(
        "wifi.ssid" to "Home", "wifi.passphrase" to "secret123", "wifi.security" to "WPA_WPA2",
        "ui.autoconnect" to "true", "ui.autoshow" to "true", "altscreen.enabled" to "false",
        "window.width" to "", "window.height" to "", "altscreen.width" to "1440", "altscreen.height" to "542",
    )

    private fun errors(vararg changes: Pair<String, String>) = EditableSettings.validate(changes.toMap(), current)

    @Test
    fun `valid changes pass`() {
        val result = errors(
            "wifi.ssid" to "Гараж", "wifi.passphrase" to "correct horse", "wifi.security" to "WPA3_TRANSITION",
            "ui.autoconnect" to "false", "ui.autoshow" to "false", "altscreen.enabled" to "true",
            "window.width" to "1600", "window.height" to "", "altscreen.width" to "1920", "altscreen.height" to "720",
        )

        assertEquals(emptyMap<String, String>(), result)
    }

    @Test
    fun `only the settings the UI offers can change`() {
        val result = errors("car.label" to "Mine")

        assertEquals(setOf("car.label"), result.keys)
    }

    // Every setting the pages show: the iPhone, the CarPlay screen, the AltScreen, and the sound.
    @Test
    fun `the pages' settings can change`() {
        val result = errors(
            "device.name" to "Гараж", "iphone.address" to "AA:bb:CC:dd:EE:ff", "iphone.join.seconds" to "45",
            "display.width" to "1920", "display.height" to "720", "display.fps" to "30",
            "display.physical.width.mm" to "292", "display.physical.height.mm" to "", "display.ppi" to "",
            "display.theme" to "dark", "display.dock" to "driver", "display.right.hand.drive" to "true",
            "display.safe.draw.outside" to "false", "display.corners" to "rounded",
            "display.safe.top" to "10", "display.safe.bottom" to "0", "display.safe.left" to "40", "display.safe.right" to "0",
            "altscreen.content" to "map-card", "altscreen.cards" to "left",
            "altscreen.safe.top" to "0", "altscreen.safe.bottom" to "0", "altscreen.safe.left" to "12", "altscreen.safe.right" to "0",
            "audio.output" to "Speakers (Realtek(R) Audio)", "audio.input" to "default", "audio.echo.cancellation" to "true",
        )

        assertEquals(emptyMap<String, String>(), result)
    }

    // The GPS page: the identification's switches wait for the next connection; the car changes at once.
    @Test
    fun `the GPS settings can change, the car's at once`() {
        val result = errors(
            "gps.enabled" to "true", "gps.wheel.speed" to "false",
            "gps.start" to "https://www.google.com/maps/@50.4501,30.5234,15z", "gps.heading" to "90.5",
            "gps.car.power.hp" to "300", "gps.car.mass.kg" to "1600",
        )
        assertEquals(emptyMap<String, String>(), result)
        // Blank is the default, as the app reads it and the page shows it.
        assertEquals(emptyMap<String, String>(), errors("gps.start" to "", "gps.heading" to "", "gps.car.power.hp" to "", "gps.car.mass.kg" to ""))
        assertEquals(Applies.RECONNECT, EditableSettings.applies("gps.wheel.speed"))
        // The GPS output's coordinator says itself when its switch needs a reconnect (D4).
        assertEquals(Applies.NOW, EditableSettings.applies("gps.enabled"))
        assertEquals(emptyMap<String, String>(), errors("gps.rate" to "10", "gps.nmea" to "modern"))
        for (rate in listOf("1", "3", "7", "20", "")) assertEquals(rate, emptyMap<String, String>(), errors("gps.rate" to rate))
        for (rate in listOf("0", "21", "2.5", "fast")) assertTrue(rate, "gps.rate" in errors("gps.rate" to rate))
        assertTrue("gps.nmea" in errors("gps.nmea" to "ancient"))
        assertEquals(emptyMap<String, String>(), errors("gps.framing" to "messages"))
        assertTrue("gps.framing" in errors("gps.framing" to "pigeons"))
        for (key in listOf("gps.start", "gps.heading", "gps.car.power.hp", "gps.car.mass.kg", "gps.rate", "gps.nmea", "gps.framing")) {
            assertEquals(Applies.NOW, EditableSettings.applies(key))
        }

        assertTrue("gps.start" in errors("gps.start" to "Kyiv"))
        assertTrue("gps.heading" in errors("gps.heading" to "360"))
        assertTrue("gps.car.power.hp" in errors("gps.car.power.hp" to "5"))
        assertTrue("gps.car.mass.kg" in errors("gps.car.mass.kg" to "heavy"))
    }

    @Test
    fun `choices take only the values the app knows`() {
        assertTrue("display.theme" in errors("display.theme" to "neon"))
        assertTrue("display.dock" in errors("display.dock" to "left"))
        assertTrue("display.corners" in errors("display.corners" to "round"))
        assertTrue("altscreen.content" in errors("altscreen.content" to "radar"))
        assertTrue("altscreen.cards" in errors("altscreen.cards" to "bottom"))
        // Neither Google Maps nor Apple Maps takes the turn card away: off is not a place.
        assertTrue("altscreen.cards" in errors("altscreen.cards" to "off"))
        assertTrue("log.level" in errors("log.level" to "loud"))
        for (key in listOf("display.right.hand.drive", "display.safe.draw.outside", "audio.echo.cancellation", "audio.duck.iphone")) {
            assertTrue(key, key in errors(key to "yes"))
        }
    }

    @Test
    fun `the CarPlay screen's numbers are ones the iPhone takes`() {
        assertTrue("display.width" in errors("display.width" to "801"))
        assertTrue("display.height" in errors("display.height" to ""))
        assertTrue("display.fps" in errors("display.fps" to "25"))
        assertTrue("display.fps" in errors("display.fps" to "fast"))
        assertTrue("display.physical.width.mm" in errors("display.physical.width.mm" to "0"))
        assertTrue("display.ppi" in errors("display.ppi" to "abc"))
        assertTrue(errors("display.physical.width.mm" to "", "display.ppi" to " ").isEmpty())
    }

    @Test
    fun `the music's level under prompts is in decibels`() {
        for (wrong in listOf("-31", "1", "half")) {
            assertTrue(wrong, "audio.duck.db" in errors("audio.duck.db" to wrong))
        }
        assertTrue(errors("audio.duck.db" to "-30", "audio.duck.iphone" to "true").isEmpty())
        assertTrue(errors("audio.duck.db" to "0").isEmpty())
    }

    // A view's part of the AltScreen's frame, one of its eighths, and the margins inside it.
    @Test
    fun `a view's part of the altscreen and its margins are checked against the part`() {
        val sized = current + ("altscreen.width" to "1920") + ("altscreen.height" to "720")
        fun errorsAt(vararg changes: Pair<String, String>) = EditableSettings.validate(changes.toMap(), sized)

        assertEquals(emptyMap<String, String>(),
            errorsAt("altscreen.card.area" to "center3", "altscreen.card.safe.top" to "10", "altscreen.card.safe.left" to ""))
        assertTrue("altscreen.card.area" in errorsAt("altscreen.card.area" to "half"))
        assertTrue("altscreen.map.safe.left" in errorsAt("altscreen.map.safe.left" to "-1"))
        // Against the part as it will be: three eighths of 1920 are 720.
        assertTrue("altscreen.card.safe.left" in
            errorsAt("altscreen.card.area" to "left3", "altscreen.card.safe.left" to "360", "altscreen.card.safe.right" to "360"))
        assertEquals(emptyMap<String, String>(),
            errorsAt("altscreen.card.area" to "full", "altscreen.card.safe.left" to "360", "altscreen.card.safe.right" to "360"))
    }

    // The error goes on what made the margins too wide: a narrower part picked under margins already saved goes on the
    // part, never on a margin that is zero; a margin changed goes on that side; a smaller frame on its size.
    @Test
    fun `margins left too wide are blamed on the part, the side, or the size that changed`() {
        val wide = current + ("altscreen.width" to "1440") + ("altscreen.height" to "540") +
            ("altscreen.map.area" to "full") + ("altscreen.map.safe.top" to "0") +
            ("altscreen.map.safe.left" to "400") + ("altscreen.map.safe.right" to "400")
        assertEquals(setOf("altscreen.map.area"), EditableSettings.validate(mapOf("altscreen.map.area" to "center4"), wide).keys)
        assertEquals(setOf("altscreen.map.safe.right"),
            EditableSettings.validate(mapOf("altscreen.map.safe.right" to "1100"), wide).keys)
        assertEquals(setOf("altscreen.width"), EditableSettings.validate(mapOf("altscreen.width" to "800"), wide).keys)
        assertEquals(emptyMap<String, String>(), EditableSettings.validate(mapOf("altscreen.map.safe.top" to "0"), wide))
    }

    // The buffering pause and the fade are times, not pixels.
    @Test
    fun `the music's pause and fade are milliseconds`() {
        for (key in listOf("audio.start.silence.ms", "audio.start.fade.ms")) {
            assertTrue(key, key in errors(key to "-1"))
        }
        assertTrue(errors("audio.start.silence.ms" to "0", "audio.start.fade.ms" to "800").isEmpty())
    }

    // The safe area must leave some of the screen: checked against the size as it will be, changed or not.
    @Test
    fun `a safe area leaves some of the screen`() {
        val wide = errors("display.safe.left" to "400", "display.safe.right" to "500", "display.width" to "800")
        assertTrue(wide.toString(), "display.safe.left" in wide)
        val tall = EditableSettings.validate(mapOf("display.safe.top" to "300"), current + ("display.height" to "480") + ("display.safe.bottom" to "200"))
        assertTrue(tall.toString(), "display.safe.top" in tall)
        assertTrue("display.safe.top" in errors("display.safe.top" to "-1"))
        assertTrue("altscreen.safe.left" in errors("altscreen.safe.left" to "x"))
    }

    @Test
    fun `the iPhone's settings are ones it can have`() {
        assertTrue("device.name" in errors("device.name" to "Ж".repeat(32)))
        assertTrue("device.name" in errors("device.name" to "a\nb"))
        assertTrue(errors("device.name" to "").isEmpty())
        assertTrue("iphone.address" in errors("iphone.address" to "my phone"))
        assertTrue(errors("iphone.address" to "").isEmpty())
        assertTrue("iphone.join.seconds" in errors("iphone.join.seconds" to "0"))
    }

    // The settings' address in a browser: the server binds it as the app starts.
    @Test
    fun `the settings' port is a port, applied at the next start`() {
        assertTrue(errors("ui.port" to "7529").isEmpty())
        for (port in listOf("0", "70000", "port", "")) assertTrue(port, "ui.port" in errors("ui.port" to port))
        assertEquals(Applies.RESTART, EditableSettings.applies("ui.port"))
    }

    @Test
    fun `the network name must fit a Wi-Fi network`() {
        assertTrue("wifi.ssid" in errors("wifi.ssid" to " "))
        assertTrue("wifi.ssid" in errors("wifi.ssid" to "x".repeat(33)))
        assertTrue("wifi.ssid" in errors("wifi.ssid" to "Ж".repeat(17)))
        assertTrue("wifi.ssid" in errors("wifi.ssid" to "a\tb"))
    }

    // WPA takes 8 to 63 printable ASCII characters or 64 hexadecimal digits.
    @Test
    fun `a WPA password must be one the network can have`() {
        assertTrue("wifi.passphrase" in errors("wifi.passphrase" to "1234567"))
        assertTrue("wifi.passphrase" in errors("wifi.passphrase" to "x".repeat(64)))
        assertTrue("wifi.passphrase" in errors("wifi.passphrase" to "пароль-пароль"))
        assertEquals(emptyMap<String, String>(), errors("wifi.passphrase" to "0123456789abcdef".repeat(4)))
    }

    // Choosing WPA for a network whose password is not set asks for one; an open network needs none.
    @Test
    fun `the password is checked against the security chosen with it`() {
        val open = current + ("wifi.security" to "NONE") + ("wifi.passphrase" to "")

        assertEquals(emptyMap<String, String>(), EditableSettings.validate(mapOf("wifi.ssid" to "Cafe"), open))
        val wpa = EditableSettings.validate(mapOf("wifi.security" to "WPA_WPA2"), open)
        assertTrue(wpa.toString(), "wifi.passphrase" in wpa)
        assertEquals(emptyMap<String, String>(), errors("wifi.security" to "NONE", "wifi.passphrase" to ""))
    }

    @Test
    fun `the security must be one the iPhone knows`() {
        assertTrue("wifi.security" in errors("wifi.security" to "wpa2"))
    }

    @Test
    fun `switches take true or false`() {
        for (key in listOf("ui.autoconnect", "ui.autoshow", "altscreen.enabled", "ui.controls.keys")) {
            assertTrue(key, key in errors(key to "yes"))
        }
    }

    // The Keyboard page's Show keys on the buttons: the controls window's page follows it at once.
    @Test
    fun `showing the keys on the controls window's buttons applies at once`() {
        assertTrue(errors("ui.controls.keys" to "true").isEmpty())
        assertEquals(Applies.NOW, EditableSettings.applies("ui.controls.keys"))
    }

    @Test
    fun `sizes must be numbers the windows can have`() {
        assertTrue("window.width" in errors("window.width" to "0"))
        assertTrue("window.height" in errors("window.height" to "big"))
        // The main window is never smaller than 800x480.
        assertTrue("window.width" in errors("window.width" to "799"))
        assertTrue("window.height" in errors("window.height" to "479"))
        assertTrue(errors("window.width" to "800", "window.height" to "480").isEmpty())
        assertTrue("altscreen.width" in errors("altscreen.width" to "1441"))
        assertTrue("altscreen.height" in errors("altscreen.height" to ""))
    }

    // A size is 4096 pixels a side at most.
    @Test
    fun `a size is at most 4096 pixels`() {
        assertTrue(errors("display.width" to "4096", "altscreen.width" to "4096", "window.width" to "4096").isEmpty())
        for (key in listOf("display.width", "display.height", "altscreen.width", "altscreen.height", "window.width", "window.height")) {
            assertTrue(key, key in errors(key to "4098"))
        }
    }

    // What the screen says after a save.
    @Test
    fun `each setting says when a change takes effect`() {
        assertEquals(Applies.RECONNECT, EditableSettings.applies("wifi.ssid"))
        assertEquals(Applies.RECONNECT, EditableSettings.applies("wifi.passphrase"))
        assertEquals(Applies.NOW, EditableSettings.applies("ui.autoshow"))
        assertEquals(Applies.RESTART, EditableSettings.applies("ui.autoconnect"))
        assertEquals(Applies.RESTART, EditableSettings.applies("window.width"))
        assertEquals(Applies.RESTART, EditableSettings.applies("log.carplay.data"))
        // The app reads these once, as it starts.
        for (key in listOf("device.name", "iphone.address", "iphone.join.seconds", "audio.output", "audio.echo.cancellation")) {
            assertEquals(key, Applies.RESTART, EditableSettings.applies(key))
        }
        // CarPlay takes these while it runs, as F11 and Shift+F11 change the AltScreen's.
        assertEquals(Applies.NOW, EditableSettings.applies("display.theme"))
        assertEquals(Applies.NOW, EditableSettings.applies("altscreen.cards"))
        assertEquals(Applies.NOW, EditableSettings.applies("audio.start.silence.ms"))
        assertEquals(Applies.NOW, EditableSettings.applies("audio.start.fade.ms"))
        assertEquals(Applies.NOW, EditableSettings.applies("audio.duck.db"))
        assertEquals(Applies.NOW, EditableSettings.applies("log.level"))
        assertEquals(Applies.NOW, EditableSettings.applies("audio.duck.iphone"))
        // The iPhone learns of the AltScreen as it connects.
        for (key in listOf("altscreen.enabled", "altscreen.width", "altscreen.height", "altscreen.content",
            "altscreen.safe.top", "altscreen.safe.bottom", "altscreen.safe.left", "altscreen.safe.right",
            "altscreen.map.safe.top", "altscreen.map.safe.left", "altscreen.card.safe.bottom", "altscreen.card.safe.right")) {
            assertEquals(key, Applies.RECONNECT, EditableSettings.applies(key))
        }
        // Every part is declared as the iPhone connects: a view moves to another at once.
        for (key in listOf("altscreen.map.area", "altscreen.card.area")) {
            assertEquals(key, Applies.NOW, EditableSettings.applies(key))
        }
        // What the iPhone learns of its screen it asks at each connection.
        for (key in listOf("display.width", "display.height", "display.fps", "display.touch", "display.physical.width.mm",
            "display.physical.height.mm", "display.ppi", "display.dock", "display.right.hand.drive",
            "display.safe.draw.outside", "display.corners", "display.safe.top", "display.safe.bottom", "display.safe.left",
            "display.safe.right")) {
            assertEquals(key, Applies.RECONNECT, EditableSettings.applies(key))
        }
    }
}
