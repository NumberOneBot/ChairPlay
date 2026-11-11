package io.github.numberonebot.chairplay.config

import com.shilapi.xcertplay.transport.Iap2WirelessSecurity
import io.github.numberonebot.chairplay.drive.GpsSettings

/** When a changed setting takes effect. */
enum class Applies {
    /** At once. */
    NOW,

    /** With the next connection to the iPhone; Reconnect now gets there sooner. */
    RECONNECT,

    /** When the app starts again. */
    RESTART,
}

/**
 * The settings the start and settings screens change, how each is checked and when a change takes effect. The
 * checks match [DesktopConfig]'s, so a saved value never stops the next start, and add what a Wi-Fi network allows.
 */
object EditableSettings {
    /** [caseless]: a switch or a choice, saved in lower case as the app reads it; [choices]: a choice's names. */
    private class Setting(val applies: Applies, val caseless: Boolean = false, val choices: Set<String>? = null, val check: (String) -> String?)

    private fun switch(applies: Applies) = Setting(applies, caseless = true, check = ::checkBoolean)
    private fun choice(vararg values: String, applies: Applies = Applies.RESTART) = Setting(applies, caseless = true, choices = values.toSet()) { value ->
        if (value.trim().lowercase() in values) null else "Must be one of ${values.joinToString()}"
    }

    // Before the settings, which take them as they initialise: altscreen.<group>.area's presets and custom.
    private val AREA_CHOICES = (AreaPreset.entries.map { it.key } + AreaChoice.Custom.KEY).toTypedArray()

    // Everything the app reads only as it starts applies after a restart: the iPhone's, the screens', and the sound's.
    // What the iPhone learns of its screen it asks at each connection, so that applies at the next one.
    private val settings: Map<String, Setting> = mapOf(
        "network.ethernet.adapter" to Setting(Applies.RECONNECT) { value ->
            if (value.isEmpty() || value.matches(Regex("[a-fA-F0-9{}-]{36,38}"))) null else "Select an Ethernet adapter"
        },
        "wifi.ssid" to Setting(Applies.RECONNECT, check = ::checkSsid),
        // Checked together with the security in validate().
        "wifi.passphrase" to Setting(Applies.RECONNECT) { null },
        "wifi.security" to Setting(Applies.RECONNECT, check = ::checkSecurity),
        "ui.autoconnect" to switch(Applies.RESTART),
        "updates.check.on.start" to switch(Applies.NOW),
        "ui.autoshow" to switch(Applies.NOW),
        "ui.controls.keys" to switch(Applies.NOW),
        "ui.tint" to choice(*TINTS.toTypedArray(), applies = Applies.NOW),
        "ui.network" to switch(Applies.RESTART),
        "ui.port" to Setting(Applies.RESTART) { if (it.trim().toIntOrNull() in 1..65535) null else "Enter a port from 1 to 65535" },
        "keyboard.text.input" to switch(Applies.RECONNECT),
        "device.name" to Setting(Applies.RESTART, check = ::checkDeviceName),
        "iphone.address" to Setting(Applies.RESTART, check = ::checkAddress),
        "iphone.join.seconds" to Setting(Applies.RESTART) { checkPositive(it, "of seconds") },
        "display.width" to Setting(Applies.RECONNECT, check = ::checkPositiveEven),
        "display.height" to Setting(Applies.RECONNECT, check = ::checkPositiveEven),
        "display.fps" to Setting(Applies.RECONNECT, check = ::checkFps),
        "video.codecs" to choice("h264", "h264+h265", applies = Applies.RECONNECT),
        "video.decoder" to choice("hardware", "software", applies = Applies.RECONNECT),
        "display.physical.width.mm" to Setting(Applies.RECONNECT, check = ::checkOptionalPositive),
        "display.physical.height.mm" to Setting(Applies.RECONNECT, check = ::checkOptionalPositive),
        "display.ppi" to Setting(Applies.RECONNECT, check = ::checkOptionalPositive),
        // CarPlay turns dark or light while it runs, as a car's headlights tell it to.
        "display.theme" to choice("system", "light", "dark", "phone", applies = Applies.NOW),
        "display.dock" to choice("auto", "driver", "bottom", applies = Applies.RECONNECT),
        "display.right.hand.drive" to switch(Applies.RECONNECT),
        "display.safe.draw.outside" to switch(Applies.RECONNECT),
        "display.share" to switch(Applies.NOW),
        "display.corners" to choice("rounded", "square", applies = Applies.RECONNECT),
        "display.touch" to choice("swipe", "tap", "none", applies = Applies.RECONNECT),
        "display.safe.top" to Setting(Applies.RECONNECT, check = ::checkNonNegative),
        "display.safe.bottom" to Setting(Applies.RECONNECT, check = ::checkNonNegative),
        "display.safe.left" to Setting(Applies.RECONNECT, check = ::checkNonNegative),
        "display.safe.right" to Setting(Applies.RECONNECT, check = ::checkNonNegative),
        "window.width" to Setting(Applies.RESTART) { checkOptionalSize(it, MAIN_WINDOW_MIN_SIZE.width) },
        "window.height" to Setting(Applies.RESTART) { checkOptionalSize(it, MAIN_WINDOW_MIN_SIZE.height) },
        "window.always.on.top" to switch(Applies.NOW),
        "altscreen.always.on.top" to switch(Applies.NOW),
        "window.front.on.call" to switch(Applies.NOW),
        // The iPhone learns of the AltScreen as it connects; the turn card moves while it runs, as Shift+F11 moves it.
        "altscreen.enabled" to switch(Applies.RECONNECT),
        "altscreen.width" to Setting(Applies.RECONNECT, check = ::checkPositiveEven),
        "altscreen.height" to Setting(Applies.RECONNECT, check = ::checkPositiveEven),
        "altscreen.content" to choice("map", "map-card", "hud", "card", applies = Applies.RECONNECT),
        "altscreen.cards" to choice("auto", "left", "right", "top", applies = Applies.NOW),
        "altscreen.hud.stream" to switch(Applies.RECONNECT),
        "altscreen.safe.top" to Setting(Applies.RECONNECT, check = ::checkNonNegative),
        "altscreen.safe.bottom" to Setting(Applies.RECONNECT, check = ::checkNonNegative),
        "altscreen.safe.left" to Setting(Applies.RECONNECT, check = ::checkNonNegative),
        "altscreen.safe.right" to Setting(Applies.RECONNECT, check = ::checkNonNegative),
        // The margins checked against their part as well, in validate.
        // Every part is declared as the iPhone connects, so a view moves to another at once; its margins are in /info.
        *AreaGroup.entries.map { areaKey(it) to choice(*AREA_CHOICES, applies = Applies.NOW) }.toTypedArray(),
        // Each group's custom part is declared as the iPhone connects, its rectangle in /info; checked against the frame in validate.
        *AreaGroup.entries.map { customKey(it) to Setting(Applies.RECONNECT, check = ::checkAreaRect) }.toTypedArray(),
        *AreaGroup.entries.flatMap { group -> SAFE_EDGES.map { safeKey(group, it) to Setting(Applies.RECONNECT, check = ::checkOptionalNonNegative) } }
            .toTypedArray(),
        "audio.music.mode" to choice("standard", "buffered", applies = Applies.RECONNECT),
        // "default" or a device's name; a name no device has stops the next start, as a typo in the file would.
        "audio.output" to Setting(Applies.RESTART) { null },
        "audio.input" to Setting(Applies.RESTART) { null },
        "audio.echo.cancellation" to switch(Applies.RESTART),
        // The next music stream starts so.
        "audio.start.silence.ms" to Setting(Applies.NOW, check = ::checkMillis),
        "audio.start.fade.ms" to Setting(Applies.NOW, check = ::checkMillis),
        // The next prompt ducks so.
        "audio.duck.db" to Setting(Applies.NOW, check = ::checkDuckDecibels),
        "audio.duck.iphone" to switch(Applies.NOW),
        // The data log opens as the app starts.
        "log.carplay.data" to switch(Applies.RESTART),
        "log.level" to choice("error", "warning", "info", "debug", "trace", applies = Applies.NOW),
        // The iPhone learns which sentences the GPS sends as it connects; the car is placed and changed at once.
        // gps.enabled is shown here but saved by the GPS output's coordinator alone, which says when a reconnect is due.
        "gps.enabled" to switch(Applies.NOW),
        "gps.wheel.speed" to switch(Applies.RECONNECT),
        "gps.start" to Setting(Applies.NOW, check = ::checkStart),
        "gps.heading" to Setting(Applies.NOW) {
            if (it.isBlank() || GpsSettings.heading(it) != null) null else "Enter a heading from 0 to 359 degrees"
        },
        "gps.heading.send" to switch(Applies.NOW),
        "gps.external" to switch(Applies.NOW),
        "gps.car.power.hp" to Setting(Applies.NOW) { checkNumber(it, GpsSettings.POWER_HP, "a power", "hp") },
        "gps.car.mass.kg" to Setting(Applies.NOW) { checkNumber(it, GpsSettings.MASS_KG, "a mass", "kg") },
        "gps.car.handling" to choice("gentle", "normal", "sharp", applies = Applies.NOW),
        "gps.gauges" to choice("large", "small", applies = Applies.NOW),
        "gps.gauges.style" to choice("german", "italian", "british", applies = Applies.NOW),
        "gps.wheel" to choice("heading", "steering", applies = Applies.NOW),
        "gps.units" to choice("metric", "imperial", applies = Applies.NOW),
        // The next fix goes so.
        "gps.rate" to Setting(Applies.NOW) { checkWhole(it, GpsSettings.RATE, "a rate", "fixes a second") },
        "gps.nmea" to choice("classic", "modern", applies = Applies.NOW),
        "gps.framing" to choice("joined", "messages", "params", applies = Applies.NOW),
    )

    /** The keys the screens may change. */
    val keys: Set<String> get() = settings.keys

    /** When a change of [key], one of [keys], takes effect. */
    fun applies(key: String): Applies = settings.getValue(key).applies

    /** Whether [key], one of [keys], is a switch or a choice, saved in lower case. */
    fun caseless(key: String): Boolean = settings[key]?.caseless ?: false
    /** [value] of [key] as the pages show it: a choice the app does not know is blank, as the app reads its default. */
    fun shown(key: String, value: String): String = settings[key]?.choices?.let { if (value.trim().lowercase() in it) value else "" } ?: value

    /**
     * [value] of [key] as it is saved: trimmed, a switch or a choice in lower case, a custom rectangle on the grid as the
     * app reads it (one that is wrong stays as typed, for its error).
     */
    fun normalized(key: String, value: String): String = when {
        caseless(key) -> value.trim().lowercase()
        AreaGroup.entries.any { customKey(it) == key } ->
            AreaRect.parseValid(value)?.snapped()?.toString() ?: value.trim()
        else -> value.trim()
    }

    /**
     * The problems with [changes] to the settings [current], by key, as sentences for the screen; empty when all can
     * be saved. The password is checked against the security it goes with, changed or not.
     */
    fun validate(changes: Map<String, String>, current: Map<String, String>): Map<String, String> {
        val errors = linkedMapOf<String, String>()
        for ((key, value) in changes) {
            val setting = settings[key]
            if (setting == null) {
                errors[key] = "$key cannot be changed here; edit settings.properties instead"
                continue
            }
            setting.check(value)?.let { errors[key] = it }
        }
        if (("wifi.passphrase" in changes || "wifi.security" in changes) && "wifi.security" !in errors) {
            val security = (changes["wifi.security"] ?: current["wifi.security"] ?: "WPA_WPA2").trim()
            val passphrase = changes["wifi.passphrase"] ?: current["wifi.passphrase"].orEmpty()
            checkPassphrase(passphrase, security)?.let { errors["wifi.passphrase"] = it }
        }
        for (screen in listOf("display", "altscreen")) checkSafeArea(screen, changes, current, errors)
        checkViewParts(changes, current, errors)
        return errors
    }

    /**
     * A safe area must leave some of [screen]'s picture, as [DesktopConfig] requires: the two sides of each axis
     * together less than the size, both taken as they will be after [changes]. The error goes on a side that changed.
     */
    private fun checkSafeArea(screen: String, changes: Map<String, String>, current: Map<String, String>, errors: MutableMap<String, String>) {
        fun value(key: String) = (changes[key] ?: current[key])?.trim()?.ifEmpty { null }
        for ((first, second, size, axis) in listOf(
            listOf("left", "right", "width", "left and right"),
            listOf("top", "bottom", "height", "top and bottom"),
        )) {
            val keys = listOf("$screen.safe.$first", "$screen.safe.$second", "$screen.$size")
            if (keys.none { it in changes } || keys.any { it in errors }) continue
            val a = value(keys[0])?.toIntOrNull() ?: 0
            val b = value(keys[1])?.toIntOrNull() ?: 0
            val total = value(keys[2])?.toIntOrNull() ?: continue
            if (a + b >= total) {
                val key = keys.take(2).firstOrNull { it in changes } ?: keys[0]
                errors[key] = "The $axis safe areas together must be less than the $size ($total px)"
            }
        }
    }

    private fun checkSsid(value: String): String? = when {
        value.isBlank() -> "The network name is empty"
        value.any(Char::isISOControl) -> "The network name cannot hold control characters"
        value.toByteArray(Charsets.UTF_8).size > 32 -> "A network name is at most 32 bytes (fewer letters outside English)"
        else -> null
    }

    private val securities = Iap2WirelessSecurity.entries.map { it.name }

    private fun checkSecurity(value: String): String? =
        if (value.trim() in securities) null else "The security must be one of ${securities.joinToString()}"

    private fun checkPassphrase(value: String, security: String): String? {
        val hex = value.all { it in '0'..'9' || it in 'a'..'f' || it in 'A'..'F' }
        val ascii = value.all { it in ' '..'~' }
        return when (security) {
            "NONE" -> null
            "WEP" -> if ((ascii && value.length in setOf(5, 13)) || (hex && value.length in setOf(10, 26))) null
                else "A WEP password is 5 or 13 characters, or 10 or 26 hexadecimal digits"
            else -> if ((ascii && value.length in 8..63) || (hex && value.length == 64)) null
                else "A WPA password is 8 to 63 characters (English letters, digits and symbols), or 64 hexadecimal digits"
        }
    }
}
