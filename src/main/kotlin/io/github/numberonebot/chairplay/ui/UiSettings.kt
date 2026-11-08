package io.github.numberonebot.chairplay.ui

import io.github.numberonebot.chairplay.config.Applies
import io.github.numberonebot.chairplay.config.EditableSettings
import io.github.numberonebot.chairplay.config.SettingsFile
import io.github.numberonebot.chairplay.input.ControlAction
import io.github.numberonebot.chairplay.input.ControlsConfig
import io.github.numberonebot.chairplay.input.ControlsFile
import io.github.numberonebot.chairplay.input.ControlsSave
import io.github.numberonebot.chairplay.input.WheelAction

/**
 * The settings as the pages read and save them: settings.properties ([settings]) with when each change applies, the
 * sound devices ([audioDevices]) beside them, and controls.properties ([bindings]) for the Keyboard page. The stored
 * Wi-Fi password never leaves: the page and the log learn only whether it is set. A save, and each connection's reading
 * of the settings ([connected]), tell the [model] what waits for a reconnection or a restart; a save tells [onSaved]
 * what changed, [onBindings] the bindings to put in force, and the pages ([changed]); a Wi-Fi network saved [connects]
 * when the controller says the first one should.
 */
internal class UiSettings(
    private val settings: SettingsFile,
    private val model: UiModel,
    private val setup: SetupInfo,
    private val log: (String) -> Unit,
    private val audioDevices: () -> AudioDeviceNames,
    private val bindings: ControlsFile?,
    private val onBindings: (ControlsConfig) -> Unit,
    private val onSaved: (Map<String, String>) -> Unit,
    private val connects: () -> Boolean,
    private val connect: () -> Unit,
    private val changed: () -> Unit,
) {
    /** The settings as the app started with them, against which a change waits for the next start. */
    private val startup: Map<String, String> = settings.read()

    /** The settings the connection under way, or the last one, was made with; null before a bring-up read them. */
    private var connection: Map<String, String>? = null

    /** Keeps [connection] and what the model is told waits in step, as a save and a bring-up's reading may meet. */
    private val pendingLock = Any()

    /** Moves on with each settings save, for the state's settingsVersion, so an open page re-reads what it does not stream. */
    @Volatile
    var version = 0L
        private set

    fun savedPassphrase(): String? = settings.read()[PASSPHRASE]?.ifEmpty { null }

    fun settings(): Map<String, Any?> {
        val values = settings.read()
        return linkedMapOf(
            "values" to EditableSettings.keys.filter { it != PASSPHRASE }.associateWith { values[it].orEmpty() },
            "passphraseSet" to !values[PASSPHRASE].isNullOrEmpty(),
            "applies" to EditableSettings.keys.associateWith { appliesName(EditableSettings.applies(it)) },
            "display" to mapOf("width" to setup.displayWidth, "height" to setup.displayHeight),
            "audio" to runCatching { audioDevices() }.getOrElse { AudioDeviceNames(emptyList(), emptyList()) }
                .let {
                    mapOf(
                        "outputs" to it.outputs,
                        "inputs" to it.inputs,
                        "defaultOutput" to it.defaultOutput,
                        "defaultInput" to it.defaultInput,
                    )
                },
        )
    }

    fun save(changes: Map<String, String>): SaveResult {
        val normalized = changes.mapValues { (key, value) -> normalize(key, value) }
        val saved = synchronized(this) {
            val current = settings.read()
            val errors = EditableSettings.validate(normalized, current)
            if (errors.isNotEmpty()) return SaveResult(errors, emptyMap())
            settings.write(normalized)
            normalized.filter { (key, value) -> current[key] != value }
        }
        val applies = normalized.keys.associateWith { appliesName(EditableSettings.applies(it)) }
        if (saved.isNotEmpty()) log("settings saved: ${saved.keys.joinToString()}")
        saved["ui.autoshow"]?.let { model.setAutoshow(it == "true") }
        if (saved.isNotEmpty()) onSaved(saved)
        updatePending()
        val now = settings.read()
        // While Get started is open it owns the first Connect: a network saved before its password must not start
        // a bring-up without one.
        if (!now["wifi.ssid"].isNullOrBlank() && model.wifiSaved() && connects()) {
            log("Wi-Fi network set; connecting")
            connect()
        }
        version++
        changed()
        return SaveResult(emptyMap(), applies)
    }

    /** A bring-up read [values]: its connection runs with them, so a change saved since waits for the next one. */
    fun connected(values: Map<String, String>) {
        synchronized(pendingLock) {
            connection = values
            updatePending()
        }
    }

    /**
     * What waits, as the settings file has it now: a restart key unlike the start's value, and while a connection runs,
     * a reconnect key unlike the value it was made with. A value set back is in force again, and waits no more.
     */
    private fun updatePending() = synchronized(pendingLock) {
        val now = settings.read()
        fun differs(key: String, from: Map<String, String>) = normalize(key, now[key].orEmpty()) != normalize(key, from[key].orEmpty())
        val phase = model.state.phase
        val made = connection?.takeIf { phase != Phase.SETUP && phase != Phase.IDLE }
        val restart = EditableSettings.keys.filter { EditableSettings.applies(it) == Applies.RESTART && differs(it, startup) }
        val reconnect = made?.let { EditableSettings.keys.filter { key -> EditableSettings.applies(key) == Applies.RECONNECT && differs(key, it) } }
        model.pending(reconnect.orEmpty().toSet(), restart.toSet())
    }

    /**
     * The Keyboard page's bindings: each action's keys, defaults and fixed keys, and the wheel and repeat settings as
     * controls.properties names them; null when the app has none to change.
     */
    fun controls(): Map<String, Any?>? {
        val config = bindings?.read() ?: return null
        return linkedMapOf(
            "actions" to ControlAction.entries.associate { action ->
                action.key to mapOf(
                    "keys" to config.keys(action),
                    "defaults" to action.defaults.split(',').map(String::trim).filter(String::isNotEmpty),
                    "fixed" to config.fixedKeys(action),
                )
            },
            "settings" to linkedMapOf(
                "global.enabled" to config.globalEnabled.toString(),
                "wheel.action" to if (config.wheel == WheelAction.NONE) "none" else "knob",
                "wheel.invert" to config.wheelInvert.toString(),
                "wheel.notches.per.step" to config.notchesPerStep.toString(),
                "key.repeat.every" to config.repeatEvery.toString(),
                "key.zoom.repeat.every" to config.zoomRepeatEvery.toString(),
            ),
        )
    }

    /** A save also tells the controls window's page, whose buttons show each action's first key. */
    fun saveControls(changes: Map<String, String>): SaveResult {
        val file = bindings ?: return SaveResult(changes.keys.associateWith { "The app has no key bindings to change" }, emptyMap())
        return when (val result = file.save(changes)) {
            is ControlsSave.Refused -> SaveResult(mapOf(result.key to result.message), emptyMap())
            is ControlsSave.Saved -> {
                onBindings(result.config)
                log("key bindings saved: ${result.changes.keys.joinToString()}")
                changed()
                SaveResult(emptyMap(), result.changes.keys.associateWith { "now" })
            }
        }
    }

    private fun normalize(key: String, value: String): String = when (key) {
        // A network name and a password are taken as typed: spaces may belong to them.
        "wifi.ssid", PASSPHRASE -> value
        else -> EditableSettings.normalized(key, value)
    }

    private fun appliesName(applies: Applies) = applies.name.lowercase()

    private companion object {
        const val PASSPHRASE = "wifi.passphrase"
    }
}
