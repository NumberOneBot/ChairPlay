package io.github.numberonebot.chairplay.session

import com.shilapi.xcertplay.airplay.AirPlaySession
import com.shilapi.xcertplay.airplay.AirPlaySessionListener
import com.shilapi.xcertplay.airplay.MediaSink
import io.github.numberonebot.chairplay.carplay.CarPlayConfig
import io.github.numberonebot.chairplay.carplay.CarPlayLinks
import io.github.numberonebot.chairplay.carplay.CarPlayMediaSink
import io.github.numberonebot.chairplay.carplay.DataLink
import io.github.numberonebot.chairplay.carplay.PhoneDataTap
import io.github.numberonebot.chairplay.carplay.reportingFeedback
import io.github.numberonebot.chairplay.config.DesktopConfig
import io.github.numberonebot.chairplay.drive.GpsSettings
import io.github.numberonebot.chairplay.drive.PacedLocation
import io.github.numberonebot.chairplay.media.AudioDuckSink
import io.github.numberonebot.chairplay.phone.LinkStatus
import io.github.numberonebot.chairplay.phone.PhoneConnection
import io.github.numberonebot.chairplay.phone.PhoneLink
import com.shilapi.xcertplay.iap2.message.Iap2Messages
import com.shilapi.xcertplay.iap2.session.Iap2Session
import com.shilapi.xcertplay.iap2.wire.Iap2Frame
import com.shilapi.xcertplay.network.HotspotAddressPolicy
import com.shilapi.xcertplay.transport.BlockingDuplexByteStream
import com.shilapi.xcertplay.transport.Iap2IdentificationConfig
import com.shilapi.xcertplay.transport.Iap2LocationMessages
import com.shilapi.xcertplay.transport.Iap2LocationProvider
import com.shilapi.xcertplay.transport.Iap2WirelessCarPlayEndpoint
import com.shilapi.xcertplay.transport.Iap2WirelessControlClient
import com.shilapi.xcertplay.transport.Iap2WirelessIdentification
import com.shilapi.xcertplay.transport.Iap2WirelessLinkRole
import com.shilapi.xcertplay.transport.forWirelessLink
import java.io.IOException

/**
 * Wireless CarPlay bring-up in the order of upstream CarPlayController.runWireless: network,
 * AirPlay listener, Bonjour, then iAP2 over Bluetooth hands the iPhone the Wi-Fi endpoint. Every
 * bring-up owns one [BringUpStack]. The end of an AirPlay session that went active, or a failure before
 * the iPhone joined over Wi-Fi, closes it and starts the next one exactly once; callbacks from a
 * stack already replaced are ignored. The data the iPhone sends over either iAP2 link (route guidance,
 * now playing, calls, battery, artwork) goes to [data] until its stack is retired.
 */
class WirelessBootstrap internal constructor(
    private val config: DesktopConfig,
    private val sink: MediaSink,
    private val listener: AirPlaySessionListener,
    private val data: PhoneDataTap = PhoneDataTap.NONE,
    private val newParts: () -> WirelessParts,
) : PhoneConnection {
    constructor(config: DesktopConfig, sink: MediaSink, listener: AirPlaySessionListener, data: PhoneDataTap = PhoneDataTap.NONE) :
        this(config, sink, listener, data, { DesktopParts(config, listener::onDebugLog) })

    @Volatile
    override var onStatus: (LinkStatus) -> Unit = {}

    /** Each session that goes active as its link, and null once that one ends; a retired stack's end unheard. */
    @Volatile
    override var onLink: (PhoneLink?) -> Unit = {}

    /** Hears the settings each bring-up offers the iPhone its screens by, read from the file then, on its thread. */
    @Volatile
    var onConnection: (DesktopConfig) -> Unit = {}

    /** Makes the iPhone's GPS for a tunnel that identifies with the spoofed GPS on, as the settings have it then. */
    @Volatile
    var newLocation: (GpsSettings) -> Iap2LocationProvider? = { null }

    private val lock = Any()
    // The music ducks under navigation prompts when the sink can.
    private val ducks = AudioDuckCommands(sink as? AudioDuckSink, ::log)
    private val links = CarPlayLinks({ (sink as? CarPlayMediaSink)?.keyframe(it) }) { onLink(it) }
    @Volatile private var current: BringUpStack? = null
    private var closed = false
    // Counts [stop]s: a failed bring-up's retry runs only if none came while it waited.
    private var stops = 0

    override fun start() {
        synchronized(lock) {
            if (closed || current != null) return
            // The parts are made on the bring-up's thread: one that fails (no MFi files) fails the bring-up.
            val stack = BringUpStack(newParts)
            current = stack
            onStatus(LinkStatus.Starting)
            Thread({ bringUp(stack) }, "chairplay-wireless").apply {
                isDaemon = true
                start()
            }
        }
    }

    override fun restart() {
        synchronized(lock) {
            current?.let(::retire)
            start()
        }
    }

    /** Disconnect: ends the session or bring-up, and a failed one's retry with it, until the next [start]. */
    override fun stop() {
        synchronized(lock) {
            stops++
            current?.let(::retire)
        }
    }

    override fun close() {
        synchronized(lock) {
            closed = true
            current?.let(::retire)
        }
    }

    private fun restart(stack: BringUpStack) {
        synchronized(lock) {
            if (current !== stack) return
            retire(stack)
            start()
        }
    }

    /** Unlike a session end, a failure waits before retrying: most recur at once (Wi-Fi, pairing, MFi). */
    private fun failed(stack: BringUpStack, error: Exception) {
        val stopsBefore = synchronized(lock) {
            if (current !== stack) return
            log("wireless bring-up failed: ${error.javaClass.simpleName}: ${error.message}")
            retire(stack)
            onStatus(LinkStatus.Failed(error.message ?: error.javaClass.simpleName, RETRY_DELAY_MILLIS))
            stops
        }
        Thread.sleep(RETRY_DELAY_MILLIS)
        synchronized(lock) { if (current == null && stops == stopsBefore) start() }
    }

    private fun retire(stack: BringUpStack) {
        current = null
        stack.close()
        data.onSessionEnded()
    }

    /** A retired stack's links may still deliver a frame or two; they must not refill the next session's data. */
    private fun dataFrom(stack: BringUpStack, link: DataLink): (Iap2Frame) -> Unit = { if (!stack.closed) data.onFrame(link, it) }

    private fun log(message: String) = listener.onDebugLog(message)

    private fun trace(message: String) = log(redactTrace(message))

    private fun bringUp(stack: BringUpStack) {
        try {
            val parts = stack.makeParts()
            val network = stack.own(parts.network())
            val info = network.start(NETWORK_TIMEOUT_MILLIS)
            val host = HotspotAddressPolicy.select(info.hostAddresses) ?: info.hostAddress
                ?: throw IOException("Wi-Fi adapter has no usable address")
            val media = parts.media(sink, microphoneEnabled = CarPlayConfig.airPlay(config).microphone).reportingFeedback(links::heard)
            val radio = parts.radioAddress()
            val now = config.now()
            val sessionConfig = CarPlayConfig.airPlayNow(config, now).copy(btMac = radio)
            if (!stack.closed) onConnection(now)
            val sessions = BringUpSessions(stack, listener, ducks, links, { current === stack }, { restart(stack) }, ::log)
            network.validateReady()
            val airPlay = stack.own(AirPlayListener(
                listOf(host) + info.hostAddresses.filter { it != host },
                sessionConfig.port,
                { socket -> AirPlaySession(socket, sessionConfig.copy(port = socket.localPort), config.identity, config.pairings,
                    parts.mfi, sessions, media) },
                { failed(stack, it) },
            ))
            val advertised = sessionConfig.copy(port = airPlay.start())
            log("AirPlay listening on ${host.hostAddress} port ${advertised.port}")
            network.validateReady()
            stack.own(parts.advertise(advertised, config.identity, info.hostAddresses))

            // A stack stopped meanwhile says nothing more: the app shows it idle.
            if (!stack.closed) onStatus(LinkStatus.CallingPhone)
            val rfcomm = stack.own(parts.connectIphone())
            val known = config.iphoneKnown()
            if (!stack.closed) onStatus(LinkStatus.Joining(known))
            val bootstrap = stack.handOver(rfcomm,
                Iap2Session.openWireless(rfcomm, traceContext = "wireless-rfcomm", onTrace = ::trace,
                    onArtwork = { if (!stack.closed) data.onArtwork(DataLink.BLUETOOTH, it) }))
            stack.bootstrap = bootstrap
            val wireless = Iap2WirelessIdentification(radio, info.ssid)
            val identification = desktopIdentification(radio, config.deviceName, now.gps)
            val runtimeIdentification = identification.forWirelessLink(Iap2WirelessLinkRole.RUNTIME_TUNNEL, wireless)
            val endpoint = buildEndpoint(info, host, advertised.port, advertised, config.identity)
            media.setIapTunnelHandler { tunnel ->
                val location = if (now.gps.enabled) newLocation(now.gps) else null
                startTunnelControl(stack, tunnel, runtimeIdentification, endpoint, location)
            }
            stack.own(AutoCloseable { media.setIapTunnelHandler(null) })

            network.validateReady()
            val joinTimeout = if (known) config.iphoneJoinSeconds * 1_000L else CONTROL_LOOP_TIMEOUT_MILLIS
            log("waiting up to ${joinTimeout / 1_000} s for the ${if (known) "known" else "new"} iPhone to join the Wi-Fi")
            val result = parts.control(
                bootstrap,
                identification.forWirelessLink(Iap2WirelessLinkRole.BLUETOOTH_BOOTSTRAP, wireless),
                endpoint,
                joinTimeout,
                onReady = {},
                // keepBluetoothControlAlive: until a handoff, Bluetooth is the only iAP2 channel.
                keepAliveAfterDeadline = { stack.rendered && stack.active.isNotEmpty() },
                onIncoming = dataFrom(stack, DataLink.BLUETOOTH),
                location = null,
            )
            if (stack.closed || stack.handedOff.get()) return
            log("iAP2 Bluetooth control ended ${result.terminal} at ${result.stage}")
            if (!stack.joined) {
                throw IOException("Bluetooth control ended ${result.terminal} before the iPhone joined over Wi-Fi")
            }
        } catch (_: StackClosed) {
        } catch (error: Exception) {
            // The Wi-Fi session outlives its Bluetooth bootstrap (upstream's wirelessActiveReported).
            if (stack.joined || stack.handedOff.get()) {
                if (!stack.closed) log("iAP2 Bluetooth control ended after the iPhone joined over Wi-Fi: ${error.message}")
            } else {
                failed(stack, error)
            }
        }
    }

    /** CarPlayController.startWirelessTunnelControl: runtime iAP2 over the AirPlay type-130 tunnel. */
    private fun startTunnelControl(
        stack: BringUpStack,
        stream: BlockingDuplexByteStream,
        identification: Iap2IdentificationConfig,
        endpoint: Iap2WirelessCarPlayEndpoint,
        location: Iap2LocationProvider?,
    ): Boolean {
        val channel = try {
            stack.own(Iap2Session.openTunnel(stream, traceContext = "wireless-tunnel", onTrace = ::trace,
                onArtwork = { if (!stack.closed) data.onArtwork(DataLink.WIFI, it) }))
        } catch (_: StackClosed) {
            return false
        } catch (error: Exception) {
            log("Could not open the tunneled iAP2 link: ${error.message}")
            return false
        }
        // A GPS that paces itself sends on this link at its own rate; the link's sends are serialised.
        // Built without DiPlay's schema, which allows its one NMEA parameter only once.
        (location as? PacedLocation)?.attach { sentences ->
            val frame = Iap2Messages.buildRaw(Iap2LocationMessages.LOCATION_INFORMATION) { sentences.forEach { string(0, it) } }
            channel.send(frame, LOCATION_SEND_TIMEOUT_MILLIS)
        }
        Thread({
            try {
                val result = stack.parts.control(channel, identification, endpoint,
                    Iap2WirelessControlClient.NO_TIMEOUT_MILLIS, onReady = { stack.onTunnelReady(::log) }, keepAliveAfterDeadline = { false },
                    onIncoming = dataFrom(stack, DataLink.WIFI), location = location)
                if (!stack.closed) log("iAP2 tunnel ended ${result.terminal} at ${result.stage}")
            } catch (error: Exception) {
                if (!stack.closed) log("tunneled iAP2 control failed: ${error.javaClass.simpleName}: ${error.message}")
            }
        }, "chairplay-iap2-tunnel").apply {
            isDaemon = true
            start()
        }
        return true
    }

    private companion object {
        const val NETWORK_TIMEOUT_MILLIS = 10_000L
        /**
         * Upstream CONTROL_LOOP_TIMEOUT_MILLIS (CarPlayController.kt:3143): identification, the user's
         * first-time consent on the iPhone and its Wi-Fi join must all fit in the Bluetooth bootstrap. A known
         * iPhone gets DesktopConfig.iphoneJoinSeconds instead.
         */
        const val CONTROL_LOOP_TIMEOUT_MILLIS = 5 * 60_000L
        const val RETRY_DELAY_MILLIS = 5_000L
        /** A fix held up longer than this is stale; the GPS stops on the failure and starts with the next request. */
        const val LOCATION_SEND_TIMEOUT_MILLIS = 1_000L
    }
}
