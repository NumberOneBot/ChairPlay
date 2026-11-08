package io.github.numberonebot.chairplay.session

import com.shilapi.xcertplay.airplay.AirPlaySession
import com.shilapi.xcertplay.airplay.AirPlaySessionListener
import com.shilapi.xcertplay.iap2.session.Iap2Session
import io.github.numberonebot.chairplay.carplay.CarPlayLinks
import java.io.Closeable
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicBoolean

/** One bring-up's resources, closed in reverse order of acquisition, and its handoff state. */
internal class BringUpStack(private val newParts: () -> WirelessParts) : Closeable {
    private val owned = ArrayList<AutoCloseable>()

    /** Set by [makeParts] as the bring-up begins. */
    @Volatile lateinit var parts: WirelessParts
        private set

    fun makeParts(): WirelessParts = newParts().also { parts = it }
    @Volatile var closed = false
        private set

    val active: MutableSet<AirPlaySession> = ConcurrentHashMap.newKeySet()
    /** The iPhone reached the PC over Wi-Fi: an active AirPlay session or a ready iAP2 tunnel. */
    @Volatile var joined = false
    @Volatile var rendered = false
    @Volatile var bootstrap: Iap2Session? = null
    @Volatile var tunnelReady = false
    val handoffRequested = AtomicBoolean()
    val handedOff = AtomicBoolean()

    /** Takes [resource]; a stack closed meanwhile closes it and ends the bring-up. */
    fun <T : AutoCloseable> own(resource: T): T {
        synchronized(this) {
            if (!closed) {
                owned += resource
                return resource
            }
        }
        runCatching { resource.close() }
        throw StackClosed()
    }

    /** [owner] now closes [resource], as an iAP2 session closes its link's stream. */
    fun <T : AutoCloseable> handOver(resource: AutoCloseable, owner: T): T {
        synchronized(this) { owned.remove(resource) }
        return own(owner)
    }

    /** Closes [resource] ahead of the rest of the stack. */
    fun release(resource: AutoCloseable) {
        if (synchronized(this) { owned.remove(resource) }) runCatching { resource.close() }
    }

    /** onWirelessTunnelReady: iAP2 control can now run over Wi-Fi. */
    fun onTunnelReady(log: (String) -> Unit) {
        if (closed) return
        tunnelReady = true
        joined = true
        log("wireless iAP2 tunnel ready; handoffRequested=${handoffRequested.get()}")
        completeHandoff(log)
    }

    /**
     * maybeCompleteWirelessHandoff: once the iPhone has asked to stop using Bluetooth and the tunnel
     * carries iAP2, the Bluetooth bootstrap is closed on purpose; the Wi-Fi session stays.
     */
    fun completeHandoff(log: (String) -> Unit) {
        if (!handoffRequested.get() || !tunnelReady) return
        val bootstrap = bootstrap ?: return
        if (!handedOff.compareAndSet(false, true)) return
        log("wireless handoff ready; closing Bluetooth bootstrap transport")
        release(bootstrap)
    }

    override fun close() {
        val resources = synchronized(this) {
            closed = true
            owned.asReversed().toList().also { owned.clear() }
        }
        resources.forEach { runCatching { it.close() } }
    }
}

/** Thrown by [BringUpStack.own] into a bring-up whose stack was closed meanwhile. */
internal class StackClosed : Exception()

/**
 * What one bring-up's AirPlay sessions report, passed on to [listener] and [links] while its [stack] [isCurrent];
 * [ended] is told when a session that went active ends.
 */
internal class BringUpSessions(
    private val stack: BringUpStack,
    private val listener: AirPlaySessionListener,
    private val ducks: AudioDuckCommands,
    private val links: CarPlayLinks,
    private val isCurrent: () -> Boolean,
    private val ended: () -> Unit,
    private val log: (String) -> Unit,
) : AirPlaySessionListener by listener {
    override fun onSessionActive(session: AirPlaySession) {
        if (stack.closed) return
        stack.active += session
        stack.joined = true
        listener.onSessionActive(session)
        links.active(session)
    }

    /** Only a session that went active ends CarPlay: /info probes and pairing retries also connect. */
    override fun onSessionEnded(session: AirPlaySession) {
        // Any session that ducked, a retired stack's too, must not leave the music ducked.
        ducks.onSessionEnded(session)
        if (!isCurrent()) return
        listener.onSessionEnded(session)
        links.ended(session)
        if (stack.active.remove(session)) ended()
    }

    override fun onVideoFrameRendered(session: AirPlaySession) {
        if (session in stack.active) stack.rendered = true
        listener.onVideoFrameRendered(session)
    }

    override fun onCommand(session: AirPlaySession, type: String, params: Map<String, Any?>) {
        log(describeCommand(type, params))
        ducks.onCommand(session, type, params)
        if (session in stack.active && isBluetoothHandoffCommand(type) &&
            stack.handoffRequested.compareAndSet(false, true)
        ) {
            log("wireless CarPlay Bluetooth handoff requested; tunnelReady=${stack.tunnelReady}")
            stack.completeHandoff(log)
        }
        listener.onCommand(session, type, params)
    }

    private fun isBluetoothHandoffCommand(type: String) =
        type.equals("disableBluetooth", ignoreCase = true) || type.equals("disable-bluetooth", ignoreCase = true)
}
