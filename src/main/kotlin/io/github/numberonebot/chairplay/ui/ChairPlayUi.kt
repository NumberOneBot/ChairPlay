package io.github.numberonebot.chairplay.ui

import io.github.numberonebot.chairplay.bluetooth.WindowsBluetooth
import io.github.numberonebot.chairplay.bluetooth.pairedIphoneName
import io.github.numberonebot.chairplay.config.DesktopConfig
import io.github.numberonebot.chairplay.network.WlanApi
import io.github.numberonebot.chairplay.config.SettingsFile
import io.github.numberonebot.chairplay.config.SetupStore
import io.github.numberonebot.chairplay.input.ControlsConfig
import io.github.numberonebot.chairplay.input.ControlsFile
import io.github.numberonebot.chairplay.session.BringUpStatus
import io.github.numberonebot.chairplay.session.addFirewallRules
import io.github.numberonebot.chairplay.session.firewallHint
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

/**
 * The start and settings screens as the app runs them: the state machine, the settings file, the HTTP bridge on
 * 127.0.0.1, and the page in the main window ([view]), or in the default browser without WebView2. [wake] makes
 * the window's loop lay the page out again after a change; [actions] connect and reconnect.
 *
 * CHAIRPLAY_UI_PORT fixes the bridge's port, for Vite's dev server; CHAIRPLAY_UI_DEBUG=1 allows the web view's
 * developer tools.
 */
class ChairPlayUi(
    dir: Path,
    config: DesktopConfig,
    firewall: FirewallStatus,
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
    /** Reads the firewall again, quietly, for the live setup checks. */
    readFirewall: () -> FirewallStatus = { firewall },
    /** Hears the settings each save changed, for those that apply at once. */
    onSaved: (Map<String, String>) -> Unit = {},
    /** The HUD's demos on the AltScreen, for the Experiments page. */
    demo: UiDemo? = null,
    /** The GPS car as the GPS page shows it. */
    gps: (() -> Map<String, Any?>)? = null,
    /** The GPS car's calls: drive mode, the car driven by a script, and the CarPlay screens saved. */
    private val driving: UiDriving? = null,
) : Closeable {
    val model = UiModel(config.wifi != null, config.autoconnect, config.autoshow, setupDone = SetupStore(dir).done())

    private val checks = SetupChecks(
        pairedIphone = { pairedIphoneName(WindowsBluetooth.pairedDevices(), config.iphoneAddress) },
        pcWifi = { WlanApi.currentConnections().firstOrNull()?.ssid },
        ethernet = io.github.numberonebot.chairplay.network.EthernetAdapters::current,
        firewall = readFirewall,
        startFirewall = firewall,
        active = { model.state.showsUi },
        mfiPresent = { mfiFilesPresent(config.mfiDirectory().toPath()) },
    )

    private val controller = UiController(
        SettingsFile(dir.resolve(DesktopConfig.SETTINGS_FILE)),
        model,
        SetupInfo(
            mfiDirectory = config.mfiDirectory().toPath(),
            firewall = firewall,
            firewallCommands = firewallHint(config.airPlay.port, ProcessHandle.current().info().command().orElse("java.exe")),
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
        elevate = ::addFirewallRules,
        setupStore = SetupStore(dir),
        onSaved = onSaved,
        demo = demo,
        gps = gps,
    )

    private val checking = checks.start { controller.setupChanged() }

    /** Tells the page that a window was shown, hidden, or resized. */
    fun windowsChanged() = controller.windowsChanged()

    /** Tells the controls window's page that what it shows changed: the volume, the AltScreen's content, Alt held. */
    fun controlsChanged() = controller.controlsChanged()

    /** Tells the GPS page that the car or the iPhone's listening changed. */
    fun gpsChanged() = controller.gpsChanged()

    /** Saves [changes] from one of the app's own keys, as a page saves them: the app and an open page hear of it alike. */
    fun saveSettings(changes: Map<String, String>) = controller.saveSettings(changes)

    private val api = AppUiApi(controller) { driving }

    private val server = UiServer(api, pageAssets(), System.getenv("CHAIRPLAY_UI_PORT")?.toIntOrNull() ?: 0, log = log)

    /** The page in the main window; give it to the window before it opens. */
    val view: EmbeddedUi

    /**
     * The controls window's page, when there are [controls]; give it to that window before it opens. The window keeps
     * the keyboard, and without WebView2 the window stays empty rather than opening a second browser tab.
     */
    val controlsView: EmbeddedUi?

    init {
        server.start()
        log("UI at ${server.url}")
        val userData = Path.of(System.getenv("LOCALAPPDATA") ?: System.getProperty("java.io.tmpdir"), "ChairPlay", "WebView2")
        val debug = System.getenv("CHAIRPLAY_UI_DEBUG") == "1"
        view = EmbeddedUi(
            server.url,
            showsUi = { model.state.showsUi },
            backend = { WebView2(userData, debug) },
            openBrowser = WebView2::openInBrowser,
            log = log,
            onEmbedded = { embedded ->
                controller.embedded = embedded
                if (embedded) log("UI in the main window (WebView2)")
            },
        )
        controlsView = controls?.let {
            EmbeddedUi(
                server.url.replaceFirst("/?", "/controls?"),
                showsUi = { true },
                backend = { WebView2(userData, debug) },
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

    fun bringUp(status: BringUpStatus) = model.bringUp(status)

    fun sessionActive() = model.sessionActive()

    fun sessionEnded() = model.sessionEnded()

    /**
     * The car button on the CarPlay home screen: the settings screen covers CarPlay. Returns false when the page is
     * not in the window, where the browser shows the settings instead.
     */
    fun carButton(): Boolean {
        model.carButton()
        return controller.embedded
    }

    override fun close() {
        checking.close()
        server.close()
    }

    private companion object {
        /** The built page, and the window's own icon as the page's. */
        fun pageAssets(): (String) -> ByteArray? {
            val page = classpathAssets()
            return { name -> if (name == "favicon.png") classpathAssets("/icons/")("chairplay-32.png") else page(name) }
        }
    }
}
