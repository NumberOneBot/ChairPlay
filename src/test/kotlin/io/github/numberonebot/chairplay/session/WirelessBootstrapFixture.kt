package io.github.numberonebot.chairplay.session

import com.shilapi.xcertplay.airplay.AirPlayConfig
import com.shilapi.xcertplay.airplay.AirPlayIdentity
import com.shilapi.xcertplay.airplay.AirPlayMediaHandler
import com.shilapi.xcertplay.airplay.AirPlaySessionListener
import com.shilapi.xcertplay.airplay.CarPlayMediaEngine
import com.shilapi.xcertplay.airplay.MediaSink
import io.github.numberonebot.chairplay.carplay.PhoneDataTap
import io.github.numberonebot.chairplay.config.DesktopConfig
import io.github.numberonebot.chairplay.config.WindowSize
import io.github.numberonebot.chairplay.config.writeTestSettings
import io.github.numberonebot.chairplay.phone.LinkStatus
import io.github.numberonebot.chairplay.phone.PhoneLink
import com.shilapi.xcertplay.iap2.session.Iap2Session
import com.shilapi.xcertplay.iap2.wire.Iap2Frame
import com.shilapi.xcertplay.mfi.MfiAuthenticator
import com.shilapi.xcertplay.network.WirelessHotspotBackend
import com.shilapi.xcertplay.network.WirelessHotspotInfo
import com.shilapi.xcertplay.network.WirelessHotspotManager
import com.shilapi.xcertplay.transport.BlockingDuplexByteStream
import com.shilapi.xcertplay.transport.Iap2IdentificationConfig
import com.shilapi.xcertplay.transport.Iap2LocationProvider
import com.shilapi.xcertplay.transport.Iap2WirelessCarPlayEndpoint
import com.shilapi.xcertplay.transport.Iap2WirelessControlClient
import com.shilapi.xcertplay.transport.Iap2WirelessControlResult
import com.shilapi.xcertplay.transport.Iap2WirelessSecurity
import org.junit.Assert.assertTrue
import java.io.Closeable
import java.io.IOException
import java.net.InetAddress
import java.net.InetSocketAddress
import java.net.ServerSocket
import java.net.Socket
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.StandardOpenOption
import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.CountDownLatch
import java.util.concurrent.LinkedBlockingDeque
import java.util.concurrent.LinkedBlockingQueue
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.atomic.AtomicReference

/** What the WirelessBootstrap tests share: a bootstrap over fakes on 127.0.0.1, an RTSP client, and waits. */
abstract class WirelessBootstrapFixture {
    internal data class Holder(val size: WindowSize?)

    /** A bootstrap over fakes on 127.0.0.1; each stack's parts and advertised port are recorded. */
    internal inner class Harness(
        tap: PhoneDataTap = PhoneDataTap.NONE,
        knownIphone: Boolean = false,
        settings: String = "",
        sink: MediaSink = object : MediaSink {},
        location: Iap2LocationProvider? = null,
        /** Hears the sessions after the fixture's own log; the default hears nothing. */
        sessions: AirPlaySessionListener = object : AirPlaySessionListener {},
        /** Hears each status as it is queued in [statuses]. */
        onStatus: (LinkStatus) -> Unit = {},
        /** Hears each link as it goes live, and null as it ends. */
        onLink: (PhoneLink?) -> Unit = {},
    ) : Closeable {
        val loopback: InetAddress = InetAddress.getByName("127.0.0.1")
        val logs = CopyOnWriteArrayList<String>()
        val stacks = AtomicInteger()
        val parts = LinkedBlockingDeque<FakeParts>()
        val previousPortFree = AtomicBoolean()
        val statuses = LinkedBlockingQueue<LinkStatus>()
        val advertised = LinkedBlockingQueue<AirPlayConfig>()
        val displays = LinkedBlockingQueue<WindowSize>()
        // The AltScreen's size each bring-up read, null while it is off (a queue holds no nulls).
        val altscreens = LinkedBlockingQueue<Holder>()
        val dir: Path = Files.createTempDirectory("chairplay-bootstrap")
        private val previousPort = AtomicInteger()
        private val advertisedPorts = LinkedBlockingQueue<Int>()
        private val bootstrap = dir.let { dir ->
            writeTestSettings(dir)
            if (settings.isNotEmpty()) Files.writeString(dir.resolve("settings.properties"), "\n$settings\n", StandardOpenOption.APPEND)
            // An AirPlay pairing record, as the first session with this iPhone leaves: its 32-byte key in hex. A shorter
            // one is damaged, moved out of pairings/, and leaves the iPhone unknown.
            if (knownIphone) Files.createDirectories(dir.resolve("pairings")).resolve("6970686f6e65").let { Files.writeString(it, "00".repeat(32)) }
            val listener = object : AirPlaySessionListener by sessions {
                override fun onDebugLog(message: String) {
                    logs += message
                    sessions.onDebugLog(message)
                }
            }
            WirelessBootstrap(DesktopConfig(dir), sink, listener, tap) {
                if (stacks.incrementAndGet() > 1) previousPortFree.set(bindable(loopback, previousPort.get()))
                FakeParts(info(listOf(loopback))) { config ->
                    previousPort.set(config.port)
                    advertisedPorts.add(config.port)
                    advertised.add(config)
                }.also(parts::add)
            }.also {
                it.newLocation = { location }
                it.onStatus = { status ->
                    statuses.add(status)
                    onStatus(status)
                }
                it.onLink = onLink
                it.onConnection = { now ->
                    displays.add(WindowSize(now.displayWidth, now.displayHeight))
                    altscreens.add(Holder(now.altscreen?.size))
                }
            }
        }

        init {
            bootstrap.start()
        }

        /** Reconnect: ends this bring-up and starts the next. */
        fun restart() = bootstrap.restart()

        fun nextPort(): Int = advertisedPorts.poll(10, TimeUnit.SECONDS)!!

        override fun close() = bootstrap.close()
    }

    protected fun info(addresses: List<InetAddress>) = WirelessHotspotInfo(
        ssid = "Home",
        passphrase = "secret123",
        security = Iap2WirelessSecurity.WPA_WPA2,
        channel = 36,
        frequencyMHz = 5180,
        bssid = null,
        interfaceName = "wlan0",
        hostAddress = addresses.first(),
        bandLabel = "5GHz",
        backend = WirelessHotspotBackend.EXISTING_WIFI,
        hostAddresses = addresses,
    )

    private fun bindable(address: InetAddress, port: Int): Boolean = try {
        ServerSocket().use { it.bind(InetSocketAddress(address, port)) }
        true
    } catch (_: IOException) {
        false
    }

    protected fun assertOk(status: String) = assertTrue(status, status.startsWith("RTSP/1.0 200"))

    protected fun awaitTrue(condition: () -> Boolean) {
        val deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(10)
        while (!condition()) {
            check(System.nanoTime() < deadline) { "condition not met in 10 s" }
            Thread.sleep(10)
        }
    }

    /** Plain RTSP as an iPhone sends it before pairing; reads each response whole. */
    protected class RtspClient(address: InetAddress, port: Int) : Closeable {
        private val socket = Socket(address, port)
        private val input = socket.getInputStream()
        private var cseq = 0

        fun request(method: String, path: String, body: ByteArray = ByteArray(0)): String {
            val head = "$method $path RTSP/1.0\r\nCSeq: ${++cseq}\r\nContent-Length: ${body.size}\r\n\r\n"
            socket.getOutputStream().apply {
                write(head.toByteArray() + body)
                flush()
            }
            val lines = generateSequence { readLine() }.takeWhile { it.isNotEmpty() }.toList()
            val length = lines.firstOrNull { it.startsWith("Content-Length:", ignoreCase = true) }
                ?.substringAfter(':')?.trim()?.toInt() ?: 0
            input.readNBytes(length)
            return lines.first()
        }

        private fun readLine(): String {
            val line = StringBuilder()
            while (true) {
                val byte = input.read()
                check(byte >= 0) { "connection closed" }
                if (byte == '\n'.code) return line.toString().removeSuffix("\r")
                line.append(byte.toChar())
            }
        }

        override fun close() = socket.close()
    }

    /** No Wi-Fi, Bluetooth or MFi. Each iAP2 run reports ready, then lasts as long as its link. */
    internal class FakeParts(
        private val info: WirelessHotspotInfo,
        private val onAdvertise: (AirPlayConfig) -> Unit,
    ) : WirelessParts {
        val link = SilentStream()
        val tunnelHandler = AtomicReference<((BlockingDuplexByteStream) -> Boolean)?>()
        val readyRuns = AtomicInteger()
        val bootstrapTimeout = LinkedBlockingQueue<Long>()
        /** Each iAP2 run's identification and location, Bluetooth's first. */
        val runs = LinkedBlockingQueue<Pair<Iap2IdentificationConfig, Iap2LocationProvider?>>()
        override val mfi: MfiAuthenticator? = null

        override fun network() = object : WirelessHotspotManager {
            override fun start(timeoutMillis: Long) = info
            override fun close() {}
        }

        override fun radioAddress() = "02:00:00:00:00:01"

        override fun media(sink: MediaSink, microphoneEnabled: Boolean): AirPlayMediaHandler {
            val engine = CarPlayMediaEngine(sink, microphoneEnabled)
            return object : AirPlayMediaHandler by engine {
                override fun setIapTunnelHandler(handler: ((BlockingDuplexByteStream) -> Boolean)?) {
                    tunnelHandler.set(handler)
                    engine.setIapTunnelHandler(handler)
                }
            }
        }

        override fun advertise(config: AirPlayConfig, identity: AirPlayIdentity, addresses: List<InetAddress>): Closeable {
            onAdvertise(config)
            return Closeable {}
        }

        override fun connectIphone(): BlockingDuplexByteStream = link

        override fun control(
            session: Iap2Session,
            identification: Iap2IdentificationConfig,
            endpoint: Iap2WirelessCarPlayEndpoint,
            timeoutMillis: Long,
            onReady: () -> Unit,
            keepAliveAfterDeadline: () -> Boolean,
            onIncoming: (Iap2Frame) -> Unit,
            location: Iap2LocationProvider?,
        ): Iap2WirelessControlResult {
            runs.add(identification to location)
            if (timeoutMillis != Iap2WirelessControlClient.NO_TIMEOUT_MILLIS) bootstrapTimeout.add(timeoutMillis)
            onIncoming(Iap2Frame(POWER_UPDATE, ByteArray(0)))
            // Only the tunnel runs without a deadline.
            if (timeoutMillis == Iap2WirelessControlClient.NO_TIMEOUT_MILLIS) {
                readyRuns.incrementAndGet()
                onReady()
            }
            // Like the real client, a dead link ends the run with an exception.
            while (!session.isClosed) session.recv(50)
            throw IOException("iAP2 link ended")
        }
    }

    protected companion object {
        const val POWER_UPDATE = 0xae01
    }

    /** Never speaks; [endFromPeer] is the iPhone dropping the link, [close] is ours. */
    internal class SilentStream : BlockingDuplexByteStream {
        val closes = AtomicInteger()
        private val ended = CountDownLatch(1)
        fun endFromPeer() = ended.countDown()
        override fun send(data: ByteArray) {}
        override fun recv(maxBytes: Int, timeoutMillis: Long): ByteArray? =
            if (ended.await(timeoutMillis.coerceAtMost(100), TimeUnit.MILLISECONDS)) ByteArray(0) else null
        override fun close() {
            closes.incrementAndGet()
            ended.countDown()
        }
    }
}
