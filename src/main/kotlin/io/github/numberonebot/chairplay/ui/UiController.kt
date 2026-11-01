package io.github.numberonebot.chairplay.ui

import io.github.numberonebot.chairplay.drive.WindowsLocation
import io.github.numberonebot.chairplay.config.SettingsFile
import io.github.numberonebot.chairplay.config.SetupStore
import io.github.numberonebot.chairplay.config.WindowSize
import io.github.numberonebot.chairplay.input.ControlsConfig
import io.github.numberonebot.chairplay.input.ControlsFile
import io.github.numberonebot.chairplay.media.AudioDevices
import java.time.format.DateTimeFormatter

/**
 * The screens' side of the app: the state the page shows, the settings it reads and writes in [settings], and the
 * actions it asks for. The stored Wi-Fi password never leaves: the page and the log learn only whether it is set.
 */
class UiController(
    private val settings: SettingsFile,
    val model: UiModel,
    private val setup: SetupInfo,
    private val actions: UiActions,
    private val log: (String) -> Unit,
    /** ui.autoconnect as the app started: the first Wi-Fi network saved then starts the connection. */
    private val autoconnect: Boolean = true,
    /** The windows the settings screen's triggers show and hide, by name; one left out is not there. */
    private val windows: Map<String, UiWindow> = emptyMap(),
    /** The controls window's buttons; null when there is no such window. */
    private val controls: UiControls? = null,
    /** Starts a program with its arguments, for [open]. */
    private val launch: (List<String>) -> Unit = { ProcessBuilder(it).start() },
    /** The sound devices the Audio page offers, as Java Sound names them. */
    private val audioDevices: () -> AudioDeviceNames = {
        AudioDeviceNames(AudioDevices.outputs().map { it.name }, AudioDevices.inputs().map { it.name })
    },
    /** The main window, whose size the CarPlay page compares with the picture's; null in tests without one. */
    private val mainWindow: UiMainWindow? = null,
    /** controls.properties, which the Keyboard page changes; null where there is none to change. */
    private val bindings: ControlsFile? = null,
    /** Puts bindings the Keyboard page saved in force: the next key press reads them. */
    private val onBindings: (ControlsConfig) -> Unit = {},
    /** The setup read again as the PC changes; null keeps the start's reading of the firewall. */
    private val checks: SetupChecks? = null,
    /** Runs commands with the administrator's consent (Windows asks for it); true when they ran and succeeded. */
    private val elevate: (String) -> Boolean = { false },
    /** Whether Get started is done; null counts it as done. */
    private val setupStore: SetupStore? = null,
    /** Which build this is, for the About page. */
    private val about: BuildInfo = BuildInfo.load(),
    /** The Windows version, for the details of a report. */
    private val windowsVersion: () -> String = ::windowsVersion,
    /** The AltScreen's window, for the AltScreen page's Actual size; null without an AltScreen. */
    private val altscreenWindow: UiMainWindow? = null,
    /** The AltScreen's window drawing a safe area, for the AltScreen page's Show frame; null without an AltScreen. */
    private val altscreenFrame: UiSafeAreaFrame? = null,
    /** Hears each save that changed settings, with the changed ones, for those that apply at once. */
    private val onSaved: (Map<String, String>) -> Unit = {},
    /** The HUD's demos on the AltScreen, for the Experiments page; null without them. */
    private val demo: UiDemo? = null,
    /** The GPS car as the GPS page shows it; null without one. */
    private val gps: (() -> Map<String, Any?>)? = null,
    /** Asks Windows where this PC is, for the GPS page. */
    private val locate: () -> WindowsLocation.Result = WindowsLocation::read,
) : UiApi {
    private val places = UiPlaces(setup, launch, log, about, windowsVersion)

    private val settingsApi = UiSettings(
        settings, model, setup, log, audioDevices, bindings, onBindings, onSaved,
        connects = { autoconnect && setupDone }, connect = ::connectNow, changed = ::changed,
    )

    /** True while the page is in the main window; false when it opened in a browser instead. */
    @Volatile
    var embedded = false
        set(value) {
            field = value
            changed()
        }

    private val lock = Object()

    @Volatile
    override var version = 0L
        private set

    /** Get started is done: an iPhone has connected since the last Forget. */
    @Volatile
    private var setupDone = setupStore?.done() ?: true

    /** The phase the model was last in, so the setup is marked done on the change into a session alone. */
    private var lastPhase = model.state.phase

    init {
        model.addListener { state ->
            val entered = state.phase == Phase.ACTIVE && lastPhase != Phase.ACTIVE
            lastPhase = state.phase
            // The first CarPlay session ends Get started, for this start and the next; Forget during it stays.
            if (entered && !setupDone) {
                setupDone = true
                runCatching { setupStore?.save(true) }.onFailure { log("setup done not remembered: $it") }
            }
            changed()
        }
    }

    /** Tells the page that a window was shown or hidden, by its trigger or by its own close button, or resized. */
    fun windowsChanged() = changed()

    /** The picture is the one the window shows now: a changed display.width comes with the next connection. */
    override fun actualSize(window: String): Boolean {
        val target = when (window) {
            "main" -> mainWindow
            "altscreen" -> altscreenWindow
            else -> null
        }?.takeIf { it.available } ?: return false
        val picture = target.pictureOr(display)
        target.resizeTo(picture.width, picture.height)
        return true
    }

    // The picture a window shows before its stream says otherwise: the display the app started with.
    private val display = WindowSize(setup.displayWidth, setup.displayHeight)

    override fun window(name: String, visible: Boolean): Boolean {
        val window = windows[name]?.takeIf { it.available } ?: return false
        window.setShown(visible)
        return true
    }

    /** Tells the page that what the controls window shows changed: the volume, the AltScreen's content, Alt held. */
    fun controlsChanged() = changed()

    /** Tells the page that the live setup checks found a change. */
    fun setupChanged() = changed()

    /** Tells the GPS page that the car or the iPhone's listening changed. */
    fun gpsChanged() = changed()

    /** The insets as saved, so the frame previews them before a restart puts them in force; blank is none. */
    override fun showAltscreenFrame(view: String?): Boolean {
        val frame = altscreenFrame ?: return false
        val values = runCatching { settings.read() }.getOrDefault(emptyMap())
        val insets = altscreenFrameInsets(values, view) ?: return false
        val (width, height) = altscreenFrameSize(values)
        frame.show(insets.top, insets.bottom, insets.left, insets.right, width, height)
        return true
    }

    override fun forget(): Boolean {
        val store = setupStore ?: return false
        runCatching { store.forget() }.onFailure {
            log("could not forget the iPhone: $it")
            return false
        }
        setupDone = false
        log("iPhone forgotten: its pairing records are deleted; Get started opens")
        changed()
        return true
    }

    // The controls' volume, which tells the pages of the change itself.
    override fun volume(level: Int): Boolean = controls?.volume(level) ?: false

    override fun windowsLocation(): Map<String, Any?> = locate().toPage()

    override fun demo(kind: String?): Boolean {
        val demo = demo ?: return false
        if (kind != null && kind !in DEMOS) return false
        if (!demo.play(kind)) return false
        log(if (kind == null) "HUD demo stopped" else "HUD demo playing: $kind")
        changed()
        return true
    }

    /** The commands the page offers to copy, run as they are, so the two ways add the same rules. */
    override fun addFirewallRule(): Boolean {
        val added = runCatching { elevate(setup.firewallCommands) }.getOrDefault(false)
        log(if (added) "firewall rules added" else "firewall rules not added: no consent, or netsh failed")
        checks?.firewallNow()
        changed()
        return added
    }

    override fun controls(): Map<String, Any?>? = settingsApi.controls()

    override fun saveControls(changes: Map<String, String>): SaveResult = settingsApi.saveControls(changes)

    override fun control(action: String, down: Boolean): Boolean = controls?.control(action, down) ?: false

    override fun key(name: String, phase: String, everything: Boolean): Boolean =
        controls?.key(name, phase, everything) ?: false

    override fun altscreenContent(content: String): Boolean = controls?.altscreenContent(content) ?: false

    override fun open(place: String): Boolean = places.open(place)
    override fun folderSizes(): Map<String, Long> = places.folderSizes()

    private fun changed() {
        synchronized(lock) {
            version++
            lock.notifyAll()
        }
    }

    override fun awaitChange(seen: Long, timeoutMillis: Long): Long {
        val deadline = System.nanoTime() + timeoutMillis * 1_000_000
        synchronized(lock) {
            while (version == seen) {
                val left = (deadline - System.nanoTime()) / 1_000_000
                if (left <= 0) break
                lock.wait(left)
            }
            return version
        }
    }

    override fun state(): Map<String, Any?> {
        val state = model.state
        val values = runCatching { settings.read() }.getOrDefault(emptyMap())
        return linkedMapOf(
            "version" to version, "settingsVersion" to settingsApi.version,
            "phase" to state.phase.name.lowercase(),
            "screen" to state.screen.name.lowercase(),
            "error" to state.error?.let { mapOf("message" to it.message, "at" to it.at.format(TIME)) },
            "knownIphone" to state.knownIphone,
            "canShowCarPlay" to state.canShowCarPlay,
            "autoshow" to state.autoshow,
            "waiting" to state.waiting,
            "demo" to demo?.let { mapOf("playing" to it.playing, "available" to it.available) },
            "gps" to gps?.invoke(),
            "embedded" to embedded,
            "wifi" to mapOf(
                "ssid" to values["wifi.ssid"].orEmpty(),
                "security" to (values["wifi.security"]?.trim()?.ifEmpty { null } ?: "WPA_WPA2"),
                "passphraseSet" to !values["wifi.passphrase"].isNullOrEmpty(),
            ),
            "windows" to WINDOWS.associateWith { name ->
                val window = windows[name]?.takeIf { it.available }
                mapOf("available" to (window != null), "visible" to (window?.visible ?: false))
            },
            "mainWindow" to mainWindow?.sizes(display),
            "altscreenWindow" to altscreenWindow?.takeIf { it.available }?.sizes(display),
            "altscreenWells" to altscreenWellsState(values),
            "controls" to controls?.state(),
            "controlsKeys" to (values["ui.controls.keys"]?.trim() == "true"),
            "setupDone" to setupDone,
            "accent" to setup.accent,
            "about" to about.toPage(places.details()),
            "reconnectPending" to state.reconnectPending.sorted(),
            "restartPending" to state.restartPending.sorted(),
            "setup" to linkedMapOf(
                "mfiFolder" to setup.mfiDirectory.toString(),
                "mfiPresent" to mfiFilesPresent(setup.mfiDirectory),
                "firewall" to (checks?.snapshot?.firewall ?: setup.firewall).name.lowercase(),
                "pairedIphone" to checks?.snapshot?.pairedIphone,
                "pcWifi" to checks?.snapshot?.pcWifi,
                *networkSetupFields(checks?.snapshot, values["network.ethernet.adapter"].orEmpty()),
                "firewallCommands" to setup.firewallCommands,
                "settingsFile" to setup.settingsFile.toString(),
            ),
        )
    }

    override fun savedPassphrase(): String? = settingsApi.savedPassphrase()

    override fun settings(): Map<String, Any?> = settingsApi.settings()

    override fun saveSettings(changes: Map<String, String>): SaveResult = settingsApi.save(changes)

    override fun connect(): String? {
        if (model.state.phase == Phase.SETUP) return "Enter the Wi-Fi network first"
        connectNow()
        return null
    }

    private fun connectNow() {
        model.connecting()
        actions.connect()
    }

    override fun reconnect() = actions.reconnect()

    override fun disconnect() {
        actions.disconnect()
        model.disconnected()
    }

    override fun show(screen: String): Boolean {
        val target = Screen.entries.firstOrNull { it.name.equals(screen, ignoreCase = true) } ?: return false
        return model.show(target)
    }

    private companion object {
        val TIME: DateTimeFormatter = DateTimeFormatter.ofPattern("HH:mm:ss")
        /** The windows the page has triggers for, each in the state whether it is there or not. */
        val WINDOWS = listOf("altscreen", "controls")
        /** The HUD demos the Experiments page plays: the made-up route, and the lanes switching under one maneuver. */
        val DEMOS = listOf("maneuvers", "lanes")
    }
}
