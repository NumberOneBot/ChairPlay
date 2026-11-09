package io.github.numberonebot.chairplay.platform

import io.github.numberonebot.chairplay.bridge.FakeFrameSink
import io.github.numberonebot.chairplay.bridge.FrameSink
import io.github.numberonebot.chairplay.bridge.TelemetrySource
import io.github.numberonebot.chairplay.drive.FakeTelemetrySource
import io.github.numberonebot.chairplay.input.GlobalKeyboardCallbacks
import io.github.numberonebot.chairplay.input.GlobalKeyboardSource
import io.github.numberonebot.chairplay.log.AppLog
import io.github.numberonebot.chairplay.media.DefaultAudioDevices
import io.github.numberonebot.chairplay.media.apac.SpatialDevice
import io.github.numberonebot.chairplay.network.EthernetAdapter
import io.github.numberonebot.chairplay.network.WlanConnection
import io.github.numberonebot.chairplay.ui.FirewallStatus
import io.github.numberonebot.chairplay.ui.WebViewBackend
import io.github.numberonebot.chairplay.ui.WebViewUnavailable
import io.github.numberonebot.chairplay.update.UpdateResult
import java.io.ByteArrayOutputStream
import java.io.Closeable
import java.io.InputStream
import java.io.PipedInputStream
import java.io.PipedOutputStream
import java.net.InetAddress
import java.net.Socket
import java.nio.file.Path
import java.util.UUID
import java.util.concurrent.atomic.AtomicInteger

/** A platform for tests: each answer is a field the test sets, and nothing reaches the real system. */
internal class FakePlatform(
    override val shell: FakeShell = FakeShell(),
    override val oneCopy: OneCopyClaim = OneCopyClaim { _, _ -> Closeable { } },
    override val keepAlive: KeepAliveTuning = FakeKeepAlive(),
    override val location: LocationSource = LocationSource { LocationResult.Unknown },
    override val firewall: FakeFirewall = FakeFirewall(),
    override val network: FakeNetwork = FakeNetwork(),
    override val bluetooth: FakeBluetooth = FakeBluetooth(),
    override val audio: FakeAudio = FakeAudio(),
    override val video: HardwareVideo? = null,
    override val windowHooks: FakeWindowHooks = FakeWindowHooks(),
    override val bridge: FakeGameBridge = FakeGameBridge(),
    var updateAnswer: Boolean = false,
) : Platform {
    val shownUpdates = mutableListOf<UpdateResult>()

    /** The global keyboard sources asked for, each with the callbacks it was given. */
    val keyboard = mutableListOf<FakeGlobalKeyboard>()

    override fun globalKeyboard(callbacks: GlobalKeyboardCallbacks): GlobalKeyboardSource =
        FakeGlobalKeyboard(callbacks).also { keyboard += it }

    override fun webView(userData: Path, debug: Boolean): WebViewBackend = throw WebViewUnavailable("the fake has no web view")

    override fun updateDialog(result: UpdateResult): Boolean {
        shownUpdates += result
        return updateAnswer
    }
}

/** A global keyboard that counts its starts and closes and is ready when started. */
internal class FakeGlobalKeyboard(val callbacks: GlobalKeyboardCallbacks) : GlobalKeyboardSource {
    var starts = 0
    var closes = 0

    override fun start() {
        starts++
        callbacks.ready()
    }

    override fun close() {
        closes++
    }
}

/** Window hooks that touch no window: each GLFW handle is its own native one, and each hook it is asked for is kept. */
internal class FakeWindowHooks(var closedByUser: Boolean = false) : WindowHooks {
    val calls = mutableListOf<String>()
    val flashes = mutableListOf<Pair<Long, Boolean>>()

    override fun nativeHandle(window: Long): Long = window

    override fun paintTitleBar(window: Long, dialog: Boolean) {
        calls += "paintTitleBar($window, $dialog)"
    }

    override fun watchClose(window: Long): CloseWatch {
        calls += "watchClose($window)"
        return CloseWatch { closedByUser }
    }

    override fun dragByPicture(window: Long) {
        calls += "dragByPicture($window)"
    }

    override fun flash(window: Long, on: Boolean) {
        flashes += window to on
    }
}

/** Bluetooth with the [radio] and [paired] devices the test set; each RFCOMM connection it records gets [link]. */
internal class FakeBluetooth(
    var radio: String = "00:11:22:33:44:55",
    var paired: List<PairedDevice> = emptyList(),
    var link: () -> RfcommLink = { FakeRfcommLink() },
) : Bluetooth {
    val connected = mutableListOf<Pair<String, UUID>>()

    override fun radioAddress(): String = radio

    override fun pairedDevices(): List<PairedDevice> = paired

    override fun connectRfcomm(address: String, service: UUID): RfcommLink {
        connected += address to service
        return link()
    }
}

/** An RFCOMM link whose peer the test plays: [receive] feeds its input, [output] keeps what was sent. */
internal class FakeRfcommLink : RfcommLink {
    private val peer = PipedOutputStream()
    private val closeCount = AtomicInteger()

    override val input: InputStream = PipedInputStream(peer)
    override val output = ByteArrayOutputStream()

    val closes: Int get() = closeCount.get()

    fun receive(bytes: ByteArray) {
        peer.write(bytes)
        peer.flush()
    }

    /** Ends the input as the peer hanging up does. */
    override fun close() {
        closeCount.incrementAndGet()
        peer.close()
    }
}

/** A firewall that answers [state] and records the rules it was asked to add. */
internal class FakeFirewall(var state: FirewallStatus = FirewallStatus.UNKNOWN, var consent: Boolean = true) : Firewall {
    val added = mutableListOf<String>()

    override fun check(port: Int, uiPort: Int?, log: AppLog): FirewallStatus = state

    override fun status(uiPort: Int?): FirewallStatus = state

    override fun hint(port: Int, uiPort: Int?): String = "add rule for $port,$uiPort"

    override fun addRules(commands: String): Boolean {
        added += commands
        return consent
    }
}

/** A desktop shell that answers what the test set, and records what it was asked to show or open. */
internal class FakeShell(
    var name: String = "Windows 11 24H2 (build 26300)",
    var dark: Boolean? = null,
    var directory: Path = Path.of(System.getProperty("java.io.tmpdir")),
) : DesktopShell {
    val errors = mutableListOf<String>()
    val browsed = mutableListOf<String>()

    override fun systemName(): String = name

    override fun appsDark(): Boolean? = dark

    override fun dataDirectory(): Path = directory

    override fun showError(text: String) {
        errors += text
    }

    override fun openInBrowser(url: String) {
        browsed += url
    }

    override fun openCommand(target: String): List<String> = listOf("open", target)

    override fun textFileCommand(file: Path): List<String> = listOf("edit", file.toString())

    override fun systemPage(page: SystemPage): String = "page:${page.name}"
}

/** Keep-alive tuning that remembers the last values it was given. */
internal class FakeKeepAlive : KeepAliveTuning {
    var tuned: List<Int> = emptyList()

    override fun tune(socket: Socket, idleSeconds: Int, intervalSeconds: Int, count: Int, deadlineSeconds: Int) {
        tuned = listOf(idleSeconds, intervalSeconds, count, deadlineSeconds)
    }
}

/** A PC network that answers what the test set. */
internal class FakeNetwork(
    var wifi: () -> List<WlanConnection> = { emptyList() },
    var adapters: List<EthernetAdapter> = emptyList(),
    var addresses: List<InetAddress> = emptyList(),
) : PcNetwork {
    override fun wifiConnections(): List<WlanConnection> = wifi()

    override fun ethernetAdapters(): List<EthernetAdapter> = adapters

    override fun physicalAddresses(): List<InetAddress> = addresses
}

/** Sound devices the test set: the defaults and the outputs spatial audio can open (none can be opened). */
internal class FakeAudio(
    var defaults: DefaultAudioDevices = DefaultAudioDevices(null, null),
    override val spatial: SpatialOutputs = FakeSpatialOutputs(),
) : SystemAudio {
    override fun defaultDevices(): DefaultAudioDevices = defaults
}

/** Spatial outputs listing [devices], the first being the default; opening one fails as a missing library does. */
internal class FakeSpatialOutputs(var devices: List<OutputDevice> = emptyList()) : SpatialOutputs {
    override fun devices(): List<OutputDevice> = devices

    override fun defaultDevice(): OutputDevice = devices.firstOrNull() ?: throw java.io.IOException("No default output")

    override fun open(deviceId: String?, channels: Int): SpatialDevice = throw UnsatisfiedLinkError("fake outputs open nothing")
}

/** A game bridge made of the sink and source the test gives. */
internal class FakeGameBridge(
    override val frames: FrameSink = FakeFrameSink(),
    override val telemetry: TelemetrySource = FakeTelemetrySource(),
) : GameBridge
