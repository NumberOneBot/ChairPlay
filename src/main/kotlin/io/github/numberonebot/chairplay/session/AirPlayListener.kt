package io.github.numberonebot.chairplay.session

import com.shilapi.xcertplay.airplay.AirPlaySession
import com.shilapi.xcertplay.network.AirPlayPortSelector
import java.io.Closeable
import java.io.IOException
import java.net.InetAddress
import java.net.ServerSocket
import java.net.Socket

/**
 * The AirPlay control listener, ported from CarPlayVpnService.startAirPlayServer/acceptLoop: one
 * socket per address on a common port, [port] first and the next free one when it is taken.
 * Closing it closes the sessions it accepted. An accept failure while open goes to [onError].
 */
class AirPlayListener(
    private val bind: List<InetAddress>,
    private val port: Int,
    private val sessionFactory: (Socket) -> AirPlaySession,
    private val onError: (IOException) -> Unit = {},
) : Closeable {
    private val servers = mutableListOf<ServerSocket>()
    private val sessions = mutableListOf<AirPlaySession>()
    private var closed = false

    @Synchronized
    fun start(): Int {
        check(!closed && servers.isEmpty()) { "AirPlayListener starts once" }
        servers += AirPlayPortSelector.bindAll(bind, port)
        for (server in servers) {
            Thread({ accept(server) }, "airplay-accept").apply {
                isDaemon = true
                start()
            }
        }
        return servers.first().localPort
    }

    private fun accept(server: ServerSocket) {
        try {
            while (true) {
                val socket = server.accept()
                socket.tcpNoDelay = true
                socket.keepAlive = true
                socket.setSoLinger(true, 0)
                val session = synchronized(this) {
                    if (closed) {
                        socket.close()
                        return
                    }
                    sessionFactory(socket).also { sessions += it }
                }
                session.start()
            }
        } catch (error: IOException) {
            if (synchronized(this) { !closed }) onError(error)
        }
    }

    override fun close() {
        val accepted = synchronized(this) {
            closed = true
            servers.forEach { runCatching { it.close() } }
            servers.clear()
            sessions.toList().also { sessions.clear() }
        }
        // Outside the lock: a session's close reports its end, and that listener may close this one.
        accepted.forEach { runCatching { it.close() } }
    }
}

/**
 * Windows Firewall drops unsolicited inbound connections, so the iPhone would see the receiver but
 * never reach it. The control port is the listener's, and AirPlayPortSelector falls back to the ten
 * ports above it when 7000 is taken; the event, data and media streams bind ephemeral ports, which
 * Windows takes from its dynamic range 49152-65535. JmDNS answers the iPhone's mDNS queries on UDP 5353. With
 * [uiPort] (ui.network on), a fourth line opens the settings' port; a free port taken for a run instead of it lands in
 * 49152-65535, open already.
 */
fun firewallHint(port: Int, programPath: String, uiPort: Int? = null): String {
    val rule = "netsh advfirewall firewall add rule name=\"$FIREWALL_RULE\" dir=in action=allow program=\"$programPath\""
    return "$rule protocol=TCP localport=$port-${port + 10},49152-65535\n" +
        "$rule protocol=UDP localport=49152-65535\n" +
        "$rule protocol=UDP localport=5353" +
        (uiPort?.let { "\n$rule protocol=TCP localport=$it" } ?: "")
}
