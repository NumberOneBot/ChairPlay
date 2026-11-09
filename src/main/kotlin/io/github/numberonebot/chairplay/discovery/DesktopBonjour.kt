package io.github.numberonebot.chairplay.discovery

import com.shilapi.xcertplay.airplay.AirPlayConfig
import com.shilapi.xcertplay.airplay.AirPlayIdentity
import java.io.BufferedInputStream
import java.io.ByteArrayOutputStream
import java.io.Closeable
import java.io.IOException
import java.io.InputStream
import java.net.Inet4Address
import java.net.InetAddress
import java.net.InetSocketAddress
import java.net.Socket
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.Executors
import javax.jmdns.JmDNS
import javax.jmdns.ServiceEvent
import javax.jmdns.ServiceInfo
import javax.jmdns.ServiceListener

/**
 * Whether a resolved control service may be probed from the registry bound to [local]: any host on the
 * LAN can publish one, so only a unicast address of the same family qualifies, loopback only on loopback.
 */
internal fun isProbeTarget(address: InetAddress, local: InetAddress): Boolean =
    (address is Inet4Address) == (local is Inet4Address) &&
        !address.isMulticastAddress && !address.isAnyLocalAddress &&
        (!address.isLoopbackAddress || local.isLoopbackAddress)

/** Reads one CRLF- or LF-terminated line of at most [MAX_STATUS_LINE_BYTES]. */
internal fun readStatusLine(input: InputStream): String {
    val line = ByteArrayOutputStream()
    while (true) {
        val byte = input.read()
        if (byte < 0) {
            if (line.size() == 0) throw IOException("no status line")
            break
        }
        if (byte == '\n'.code) break
        if (line.size() >= MAX_STATUS_LINE_BYTES) throw IOException("status line too long")
        line.write(byte)
    }
    return line.toString(Charsets.US_ASCII).trimEnd('\r')
}

private const val MAX_STATUS_LINE_BYTES = 512

/**
 * Publishes the receiver's `_airplay._tcp` service, one interface-bound JmDNS per address as in
 * upstream's interface-mDNS mode (CarPlayBonjour.start): a JmDNS instance joins only its own
 * address family's multicast group. Each instance also browses for the iPhone's
 * `_carplay-ctrl._tcp` service and asks it to connect, as upstream's interfaceListener and probe do.
 */
class DesktopBonjour(
    private val config: AirPlayConfig,
    private val identity: AirPlayIdentity,
    private val addresses: List<InetAddress>,
    private val log: (String) -> Unit = {},
) : Closeable {
    private val instances = mutableListOf<JmDNS>()
    private val seen = ConcurrentHashMap.newKeySet<String>()
    // One worker, as upstream: probes run one at a time, off the JmDNS callback threads.
    private val worker = Executors.newSingleThreadExecutor { Thread(it, "chairplay-carplay-ctrl").apply { isDaemon = true } }
    @Volatile private var closed = false

    private val controlListener = object : ServiceListener {
        override fun serviceAdded(event: ServiceEvent) {
            if (closed) return
            log("bonjour: CarPlay control service ${event.name} added")
            event.dns.requestServiceInfo(event.type, event.name, true)
        }

        override fun serviceRemoved(event: ServiceEvent) {
            seen.remove("${event.name}|${event.dns.inetAddress is Inet4Address}")
        }

        override fun serviceResolved(event: ServiceEvent) {
            if (closed) return
            val info = event.info
            val ipv4 = event.dns.inetAddress is Inet4Address
            val address = info.inetAddresses.firstOrNull { isProbeTarget(it, event.dns.inetAddress) }
            if (address == null || info.port !in 1..65535) {
                log("bonjour: CarPlay control service ${event.name} has no usable ${if (ipv4) "IPv4" else "IPv6"} endpoint")
                return
            }
            // A failed probe on one family must not suppress the other family's endpoint.
            if (!seen.add("${event.name}|$ipv4")) return
            log("bonjour: CarPlay control service ${event.name} at ${address.hostAddress} port ${info.port}")
            worker.execute { probe(event.name, address, info.port, event.dns.inetAddress) }
        }
    }

    @Synchronized
    fun start() {
        for (address in addresses) {
            val dns = JmDNS.create(address, "carplay-${config.deviceId.replace(":", "")}")
            instances.add(dns)
            dns.addServiceListener("${CarPlayTxt.CARPLAY_CONTROL_SERVICE_TYPE}.local.", controlListener)
            dns.registerService(ServiceInfo.create(
                "${CarPlayTxt.AIRPLAY_SERVICE_TYPE}.local.", config.deviceName, config.port,
                0, 0, CarPlayTxt.airPlayTxtRecords(config, identity),
            ))
        }
    }

    /** Upstream CarPlayBonjour.probe: a few attempts, each reading only the status line. */
    private fun probe(name: String, address: InetAddress, port: Int, source: InetAddress) {
        repeat(MAX_PROBE_ATTEMPTS) { attempt ->
            if (closed) return
            try {
                val status = Socket().use { socket ->
                    socket.bind(InetSocketAddress(source, 0))
                    socket.connect(InetSocketAddress(address, port), CONNECT_TIMEOUT_MILLIS)
                    socket.soTimeout = READ_TIMEOUT_MILLIS
                    val request = CarPlayTxt.connectRequest(
                        checkNotNull(address.hostAddress), port, config.sourceVersion, config.deviceId,
                    )
                    socket.getOutputStream().apply {
                        write(request.toByteArray(Charsets.US_ASCII))
                        flush()
                    }
                    readStatusLine(BufferedInputStream(socket.getInputStream()))
                }
                log("bonjour: connect request to $name answered \"$status\" (attempt ${attempt + 1})")
                return
            } catch (error: IOException) {
                if (closed) return
                log("bonjour: connect request to $name failed (attempt ${attempt + 1}): ${error.javaClass.simpleName}: ${error.message}")
            }
            if (attempt + 1 < MAX_PROBE_ATTEMPTS) {
                try {
                    Thread.sleep(PROBE_RETRY_DELAY_MILLIS)
                } catch (_: InterruptedException) {
                    return
                }
            }
        }
    }

    @Synchronized
    override fun close() {
        closed = true
        worker.shutdownNow()
        for (dns in instances) {
            runCatching { dns.removeServiceListener("${CarPlayTxt.CARPLAY_CONTROL_SERVICE_TYPE}.local.", controlListener) }
            runCatching { dns.unregisterAllServices() }
            runCatching { dns.close() }
        }
        instances.clear()
    }

    private companion object {
        // Upstream CarPlayBonjour constants.
        const val MAX_PROBE_ATTEMPTS = 7
        const val PROBE_RETRY_DELAY_MILLIS = 1_500L
        const val CONNECT_TIMEOUT_MILLIS = 3_000
        const val READ_TIMEOUT_MILLIS = 3_000
    }
}
