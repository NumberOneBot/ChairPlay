package io.github.numberonebot.chairplay.platform.macos

import io.github.numberonebot.chairplay.bridge.FrameSink
import io.github.numberonebot.chairplay.bridge.TelemetrySource
import io.github.numberonebot.chairplay.config.AreaRect
import io.github.numberonebot.chairplay.input.GlobalKeyboardCallbacks
import io.github.numberonebot.chairplay.input.GlobalKeyboardSource
import io.github.numberonebot.chairplay.platform.Bluetooth
import io.github.numberonebot.chairplay.platform.DesktopShell
import io.github.numberonebot.chairplay.platform.Firewall
import io.github.numberonebot.chairplay.platform.GameBridge
import io.github.numberonebot.chairplay.platform.HardwareVideo
import io.github.numberonebot.chairplay.platform.KeepAliveTuning
import io.github.numberonebot.chairplay.platform.LocationResult
import io.github.numberonebot.chairplay.platform.LocationSource
import io.github.numberonebot.chairplay.platform.OneCopyClaim
import io.github.numberonebot.chairplay.platform.PcNetwork
import io.github.numberonebot.chairplay.platform.Platform
import io.github.numberonebot.chairplay.platform.SystemAudio
import io.github.numberonebot.chairplay.platform.WindowHooks
import io.github.numberonebot.chairplay.ui.WebViewBackend
import io.github.numberonebot.chairplay.ui.WebViewUnavailable
import io.github.numberonebot.chairplay.update.UpdateResult
import java.nio.ByteBuffer
import java.nio.file.Path

/** macOS platform adapters; each is created lazily when first accessed. */
internal class MacPlatform : Platform {
    override val shell: DesktopShell by lazy { MacShell() }
    override val oneCopy: OneCopyClaim by lazy { MacOneCopy() }
    override val keepAlive: KeepAliveTuning by lazy { MacKeepAlive() }
    override val location: LocationSource by lazy { MacLocation }
    override val firewall: Firewall by lazy { MacFirewall() }
    override val network: PcNetwork by lazy { MacNetwork() }
    override val bluetooth: Bluetooth by lazy { MacBluetooth }
    override val audio: SystemAudio by lazy { MacAudio() }
    override val video: HardwareVideo? by lazy { null }
    override val windowHooks: WindowHooks by lazy { MacWindows() }
    override val bridge: GameBridge by lazy { MacBridge }

    override fun globalKeyboard(callbacks: GlobalKeyboardCallbacks): GlobalKeyboardSource =
        throw UnsupportedOperationException("Global shortcuts are not supported on macOS yet")

    override fun webView(userData: Path, debug: Boolean): WebViewBackend =
        throw WebViewUnavailable("Embedded web view is not available on macOS; opening in browser")

    override fun updateDialog(result: UpdateResult): Boolean {
        val (heading, text) = when (result) {
            is UpdateResult.Available -> "ChairPlay ${result.latest} is available" to
                (result.current?.let { "You have version $it." } ?: "You have a development build from ${result.built}.") +
                " Open the releases page on GitHub to download the update."
            UpdateResult.Current -> "ChairPlay is up to date" to "No newer release is available"
            UpdateResult.Failed -> "Couldn't check for updates" to "Check your internet connection and try again"
            UpdateResult.Development -> "This is a development build" to
                "This build has no release version to compare. Open the releases page on GitHub to see the latest version."
        }
        val offerOpen = result is UpdateResult.Available || result == UpdateResult.Development
        shell.showError("$heading\n\n$text")
        return offerOpen
    }

    override fun openFile(title: String, filterName: String, patterns: List<String>): Path? = null
}

private object MacBridge : GameBridge {
    override val frames: FrameSink = MacFrames
    override val telemetry: TelemetrySource = MacTelemetry
}

private object MacFrames : FrameSink {
    override fun open(): String? = null
    override fun publish(bgra: ByteBuffer, width: Int, height: Int, safe: AreaRect): String? = null
    override fun state(state: Int) {}
    override fun close() {}
}

private object MacTelemetry : TelemetrySource {
    override fun open(): String? = null
    override fun read(into: ByteBuffer): Boolean = false
    override fun close() {}
}

private object MacLocation : LocationSource {
    override fun read(): LocationResult = LocationResult.Unknown
}
