package io.github.numberonebot.chairplay.ui

import io.github.numberonebot.chairplay.config.SettingsFile
import io.github.numberonebot.chairplay.config.SetupStore
import io.github.numberonebot.chairplay.config.WindowSize
import io.github.numberonebot.chairplay.platform.LocationResult
import io.github.numberonebot.chairplay.input.ControlsConfig
import io.github.numberonebot.chairplay.input.ControlsFile
import java.nio.file.Files
import java.nio.file.Path
import java.util.concurrent.atomic.AtomicInteger

/** What the UiController tests share: a settings file in a folder of its own, what the app was asked, and fakes. */
abstract class UiControllerFixture {
    protected val dir: Path = Files.createTempDirectory("chairplay-ui")

    protected val file: Path = dir.resolve("settings.properties")

    protected val connects = AtomicInteger()

    protected val reconnects = AtomicInteger()

    protected val disconnects = AtomicInteger()

    protected val logged = mutableListOf<String>()

    protected val launched = mutableListOf<List<String>>()

    protected fun controller(
        settings: String = "wifi.ssid=Home\nwifi.passphrase=secret123\nwifi.security=WPA_WPA2\nui.autoconnect=true\n" +
            "ui.autoshow=true\naltscreen.enabled=false\nwindow.width=\nwindow.height=\naltscreen.width=1440\naltscreen.height=542\n",
        autoconnect: Boolean = true,
        windows: Map<String, UiWindow> = emptyMap(),
        controls: UiControls? = null,
        launch: (List<String>) -> Unit = { launched += it },
        audio: () -> AudioDeviceNames = { AudioDeviceNames(listOf("Speakers (Realtek)"), listOf("Microphone Array")) },
        mainWindow: UiMainWindow? = null,
        bindings: ControlsFile? = null,
        checks: SetupChecks? = null,
        elevate: (String) -> Boolean = { elevated += it; consent },
        setupStore: SetupStore? = null,
        about: BuildInfo = BuildInfo(Commit("4e6d50df", "2026-10-23"), Commit("143f4674", "2026-10-21")),
        altscreenWindow: UiMainWindow? = null,
        altscreenFrame: UiSafeAreaFrame? = null,
        onSaved: (Map<String, String>) -> Unit = {},
        demo: UiDemo? = null,
        gps: (() -> Map<String, Any?>)? = null,
        locate: () -> LocationResult = { LocationResult.Unknown },
        systemVersion: () -> String = { "Windows 11 24H2 (build 26300)" },
    ): UiController {
        Files.writeString(file, settings)
        val values = SettingsFile(file).read()
        val model = UiModel(!values["wifi.ssid"].isNullOrBlank(), autoconnect, autoshow = true)
        val setup = SetupInfo(dir.resolve("offline-mfi"), FirewallStatus.MISSING, "netsh advfirewall ...", file, 1280, 720)
        return UiController(SettingsFile(file), model, setup, object : UiActions {
            override fun connect() {
                connects.incrementAndGet()
            }

            override fun disconnect() {
                disconnects.incrementAndGet()
            }

            override fun reconnect() {
                reconnects.incrementAndGet()
            }
        }, logged::add, autoconnect, windows, controls, launch, audio, mainWindow, bindings, applied::add, checks, elevate, setupStore,
            about, systemVersion = systemVersion, altscreenWindow = altscreenWindow, altscreenFrame = altscreenFrame,
            onSaved = onSaved, demo = demo, gps = gps, locate = locate)
    }

    protected class FakeDemo(override var available: Boolean = true) : UiDemo {
        override var playing: String? = null
        override fun play(kind: String?): Boolean {
            if (kind != null && !available) return false
            playing = kind
            return true
        }
    }

    /** The commands run with the administrator's consent, and whether it is given. */
    protected val elevated = mutableListOf<String>()

    protected var consent = true

    /** The bindings each Keyboard page save put in force. */
    protected val applied = mutableListOf<ControlsConfig>()

    protected fun keyboard(): ControlsFile {
        ControlsConfig.load(dir)
        return ControlsFile(dir)
    }

    /** A main window at [width] x [height] that records the sizes it was asked for. */
    protected class FakeMainWindow(override var width: Int, override var height: Int) : UiMainWindow {
        override var available = true
        override var minWidth = 0
        override var minHeight = 0
        override var picture: WindowSize? = null
        val asked = mutableListOf<String>()
        override fun resizeTo(width: Int, height: Int) {
            asked += "${width}x$height"
        }
    }

    /** Controls that record what the page asked of them. */
    protected class FakeControls : UiControls {
        val calls = mutableListOf<String>()
        override fun control(action: String, down: Boolean) = (action != "eject").also { calls += "$action $down" }
        override fun altscreenContent(content: String) = (content == "hud").also { calls += "altscreen $content" }
        override fun volume(level: Int) = true.also { calls += "volume $level" }
        override fun state(): Map<String, Any?> = mapOf("volume" to mapOf("level" to 60, "muted" to false))
    }

    /** A window that only records what it was asked. */
    protected class FakeWindow(override var visible: Boolean, override var available: Boolean = true) : UiWindow {
        override fun setShown(shown: Boolean) {
            visible = shown
        }
    }
}
