package io.github.numberonebot.chairplay.ui

import io.github.numberonebot.chairplay.bluetooth.pairedIphoneName
import io.github.numberonebot.chairplay.carplay.CarPlayConfig
import io.github.numberonebot.chairplay.config.DesktopConfig
import io.github.numberonebot.chairplay.gps.GpsCommands
import io.github.numberonebot.chairplay.config.SettingsFile
import io.github.numberonebot.chairplay.config.SetupStore
import io.github.numberonebot.chairplay.config.CarPlayTime
import io.github.numberonebot.chairplay.config.SupportStore
import java.util.Timer
import kotlin.concurrent.timerTask
import io.github.numberonebot.chairplay.input.ControlsConfig
import io.github.numberonebot.chairplay.input.ControlsFile
import io.github.numberonebot.chairplay.phone.LinkStatus
import io.github.numberonebot.chairplay.platform.Platforms
import io.github.numberonebot.chairplay.appLog
import io.github.numberonebot.chairplay.log.LogLevel
import io.github.numberonebot.chairplay.update.UpdateAnnouncement
import io.github.numberonebot.chairplay.update.UpdateChecks
import java.io.Closeable
import java.nio.file.Path

/**
 * What the GPS page and its experiments ask of the app through the API: see [UiApi.driveMode], [UiApi.gpsDrive] and
 * [UiApi.screenshot].
 */
interface UiDriving {
    fun driveMode(on: Boolean): Boolean

    fun gpsDrive(throttle: Double, brake: Double, steer: Double): Boolean

    fun screenshot(): Boolean
}

/** What the start screen hears of the phone's sessions: one going active, one ending, and the car button. */
interface UiSessions {
    fun sessionActive()

    fun sessionEnded()

    /** The settings screen covers the phone's. */
    fun carButton()
}

/**
 * The start and settings screens as the app runs them: the state machine, the settings file, the HTTP bridge on
 * 127.0.0.1, and the page in the main window ([view]), or in the default browser without WebView2. [wake] makes
 * the window's loop lay the page out again after a change; [actions] connect and reconnect.
 *
 * CHAIRPLAY_UI_PORT sets the bridge's port in place of ui.port, for Vite's dev server; CHAIRPLAY_UI_DEBUG=1 allows
 * the web view's developer tools.
 */
class ChairPlayUi(
    dir: Path,
    config: DesktopConfig,
    /** The start's read of the firewall, with its log lines: run on a thread of its own, the windows not waiting. */
    checkFirewall: () -> FirewallStatus,
    actions: UiActions,
    wake: () -> Unit,
    private val log: (String) -> Unit,
    /** The windows the settings screen's triggers show and hide, by name ("altscreen", "controls"). */
    windows: Map<String, UiWindow> = emptyMap(),
    /** The controls window's buttons; with them, [controlsView] is the page that shows them. */
    controls: UiControls? = null,
    /** The main window, for the CarPlay page's Actual size. */
    mainWindow: UiMainWindow? = null,
    /** The AltScreen's window, for the AltScreen page's Actual size. */
    altscreenWindow: UiMainWindow? = null,
    /** The AltScreen's window drawing its safe area, for the AltScreen page's Show frame. */
    altscreenFrame: UiSafeAreaFrame? = null,
    /** Puts the bindings the Keyboard page saved in force. */
    onBindings: (ControlsConfig) -> Unit = {},
    /** Reads the firewall again, quietly, for the setup checks the pages ask for. */
    readFirewall: () -> FirewallStatus = checkFirewall,
    /** Hears the settings each save changed, for those that apply at once. */
    onSaved: (Map<String, String>) -> Unit = {},
    /** The HUD's demos on the AltScreen, for the Experiments page. */
    demo: UiDemo? = null,
    /** The GPS car as the GPS page shows it. */
    gps: (() -> Map<String, Any?>)? = null,
    /** The GPS car's calls: drive mode, the car driven by a script, and the CarPlay screens saved. */
    private val driving: UiDriving? = null,
    bufferedAudio: () -> Map<String, Any>? = { null },
    /** The GPS output's commands, once the app made them. */
    private val gpsCommands: () -> GpsCommands? = { null },
    /** Track playback's commands (drive/track/TrackCommands.kt); false before the app made them. */
    trackCommands: (String) -> Boolean = { false },
    /** The GPS method the coordinator has in force, read without its lock; null before it exists. */
    gpsApplied: () -> String? = { null },
    /** The screen turned back to CarPlay, as a car's CarPlay button turns it: a game's car display shows CarPlay too. */
    carPlayShown: () -> Unit = {},
) : Closeable, UiSessions {
    val model = UiModel(config.wifi != null, config.autoconnect, config.autoshow, setupDone = SetupStore(dir).done())
        .apply { onCarPlayShown(carPlayShown) }
    private val settingsFile = SettingsFile(dir.resolve(DesktopConfig.SETTINGS_FILE))
    // A release found stays for the About page (updates.latest), called on the check's worker once the UI is up. A build
    // without a version compares by its commit's day (owner, 2026-11-12).
    private val updates = BuildInfo.load().let { build ->
        UpdateChecks(build.version, built = build.app?.date?.let { runCatching { java.time.LocalDate.parse(it) }.getOrNull() },
            remember = { controller.updateFound(it.version.toString(), it.published) }, log = log)
    }

    // The toast of an update just installed, from this build's version and its release's highlight.
    private val announcement = BuildInfo.load().let { build ->
        UpdateAnnouncement(dir, build.version, build.highlight, Platforms.current::updateToast, log)
    }

    /** Called once the app's windows are open: the update check and the update's toast, each on its own daemon thread. */
    fun checkUpdatesAtStart() {
        updates.start(!settingsFile.read()["updates.check.on.start"].orEmpty().trim().equals("false", true))
        announcement.start()
    }

    private val checks = SetupChecks(
        pairedIphone = { pairedIphoneName(Platforms.current.bluetooth.pairedDevices(), config.iphoneAddress) },
        pcWifi = { Platforms.current.network.wifiConnections().firstOrNull()?.ssid },
        ethernet = Platforms.current.network::ethernetAdapters,
        firewall = readFirewall,
        startFirewall = FirewallStatus.UNKNOWN,
        mfiPresent = { mfiFilesPresent(config.mfiDirectory().toPath()) },
        // A warning, as the log keeps by default: a report of no network can then say why.
        log = { appLog.log(LogLevel.WARNING, it) },
    )

    private val controller = UiController(
        settingsFile,
        model,
        SetupInfo(
            mfiDirectory = config.mfiDirectory().toPath(),
            firewall = FirewallStatus.UNKNOWN,
            firewallCommands = Platforms.current.firewall.hint(CarPlayConfig.PORT, config.uiServer.firewallPort),
            settingsFile = dir.resolve(DesktopConfig.SETTINGS_FILE),
            displayWidth = config.displayWidth,
            displayHeight = config.displayHeight,
            accent = config.accentText,
        ),
        actions,
        log,
        config.autoconnect,
        windows,
        controls,
        mainWindow = mainWindow,
        altscreenWindow = altscreenWindow,
        altscreenFrame = altscreenFrame,
        bindings = ControlsFile(dir),
        onBindings = onBindings,
        checks = checks,
        elevate = Platforms.current.firewall::addRules,
        setupStore = SetupStore(dir, config.pairings::clear),
        onSaved = onSaved,
        demo = demo,
        gps = gps,
        bufferedAudio = bufferedAudio,
        gpsApplied = gpsApplied,
    )

    // The firewall's rules take about a second to walk: Get started shows Not checked until they are read. A read that
    // fails still ends the start's, so the pages' checks read the firewall again.
    init {
        Thread({
            runCatching(checkFirewall).getOrDefault(FirewallStatus.UNKNOWN).let { if (checks.firewallChecked(it)) controller.setupChanged() }
        }, "firewall-check").apply { isDaemon = true }.start()
    }

    /** Tells the page that a window was shown, hidden, or resized. */
    fun windowsChanged() = controller.windowsChanged()

    /** Tells the controls window's page that what it shows changed: the volume, the AltScreen's content, Alt held. */
    fun controlsChanged() = controller.controlsChanged()

    /** Tells the GPS page that the car or the iPhone's listening changed. */
    fun gpsChanged() = controller.gpsChanged()

    /** Saves [changes] from one of the app's own keys, as a page saves them: the app and an open page hear of it alike. */
    fun saveSettings(changes: Map<String, String>) = controller.saveSettings(changes)

    /** The GPS output coordinator's own save of gps.method, gps.enabled and gps.usb.device. */
    fun saveGps(changes: Map<String, String>) = controller.saveGps(changes)

    // The coffee card's state, and the CarPlay time it waits for: a session is live while the model is ACTIVE, which
    // every end (the iPhone's, Disconnect, a dropped link) leaves. Saved every few minutes too; a change tells the pages.
    private val support = SupportStore(dir)
    private val carPlayTime = CarPlayTime(support)
    private val timeSaver = Timer("carplay-time", true).apply {
        model.addListener { state -> runCatching { carPlayTime.live(state.phase == Phase.ACTIVE) }.onFailure { log("CarPlay time not saved: $it") } }
        scheduleAtFixedRate(timerTask { saveCarPlayTime() }, TIME_SAVE_MILLIS, TIME_SAVE_MILLIS)
    }

    private fun saveCarPlayTime() = runCatching {
        val due = support.due()
        carPlayTime.save()
        if (support.due() != due) controller.changed()
    }.onFailure { log("CarPlay time not saved: $it") }

    private val api = AppUiApi(controller, { updates.check() }, checks, gpsCommands, trackCommands, support) { driving }

    private val server = UiServer(
        api,
        pageAssets(),
        System.getenv("CHAIRPLAY_UI_PORT")?.toIntOrNull() ?: config.uiServer.port,
        network = config.uiServer.network,
        openBrowser = Platforms.current.shell::openInBrowser,
        log = log,
    )

    /** The page in the main window; give it to the window before it opens. */
    val view: EmbeddedUi

    /**
     * The controls window's page, when there are [controls]; give it to that window before it opens. The window keeps
     * the keyboard, and without WebView2 the window stays empty rather than opening a second browser tab.
     */
    val controlsView: EmbeddedUi?

    init {
        server.start()
        log("UI at ${server.browserUrl}")
        // From now on the bridge's status file tells a launcher that ChairPlay runs, its version and this port.
        runCatching { Platforms.current.bridge.frames.announce(server.port, BuildInfo.load().version.orEmpty()) }
            .getOrElse { it.toString() }?.let { log("bridge status file not made: $it") }
        // WebView2's profile (its cache) stays with the app's other data, so a copied or moved folder takes it along.
        val userData = dir.resolve("webview2")
        val debug = System.getenv("CHAIRPLAY_UI_DEBUG") == "1"
        // ?window tells the window's page from a browser tab's (page.ts): only it shows the app's own window.
        view = EmbeddedUi(
            "${server.url}?window",
            showsUi = { model.state.showsUi },
            backend = { Platforms.current.webView(userData, debug) },
            openBrowser = Platforms.current.shell::openInBrowser,
            log = log,
            onEmbedded = { embedded ->
                controller.embedded = embedded
                if (embedded) log("UI in the main window (WebView2)")
            },
        )
        controlsView = controls?.let {
            EmbeddedUi(
                "${server.url}controls",
                showsUi = { true },
                backend = { Platforms.current.webView(userData, debug) },
                openBrowser = null,
                log = { message -> log("controls window: $message") },
                onEmbedded = {},
                takesKeyboard = false,
            )
        }
        model.addListener { wake() }
    }

    /** The page covers the window, so keys and the wheel are its, not CarPlay's. */
    val coversWindow: Boolean get() = controller.embedded && model.state.showsUi

    /** Whether the app connects as it starts: ui.autoconnect, once there is a Wi-Fi network. */
    val connectsAtStart: Boolean get() = model.state.phase == Phase.STARTING

    fun bringUp(status: LinkStatus) = model.bringUp(status)

    /** A bring-up read the settings as [now]: the pages mark a change against what its connection runs with. */
    fun connected(now: DesktopConfig) = controller.connected(now.values)

    override fun sessionActive() = model.sessionActive()

    override fun sessionEnded() = model.sessionEnded()

    /** The car button on the CarPlay home screen: the settings screen covers CarPlay, or shows in the browser. */
    override fun carButton() = model.carButton()

    override fun close() {
        timeSaver.cancel()
        saveCarPlayTime()
        updates.close()
        server.close()
    }

    private companion object {
        /** How often a live session's CarPlay time is saved: a crash loses no more. */
        const val TIME_SAVE_MILLIS = 5 * 60_000L

        /** The built page, and the window's own icon as the page's. */
        fun pageAssets(): (String) -> ByteArray? {
            val page = classpathAssets()
            return { name -> if (name == "favicon.png") classpathAssets("/icons/")("chairplay-32.png") else page(name) }
        }
    }
}
