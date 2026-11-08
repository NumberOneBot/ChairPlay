package io.github.numberonebot.chairplay.ui

import java.net.InetAddress

/**
 * What the UI's HTTP server on [port] asks of a request in place of a secret: that its `Host` names this PC by a
 * loopback name with this port, or, with ui.network on, by one of its own addresses on the network ([lan]), so a web
 * site whose own name resolves to this PC (DNS rebinding) is refused. Names are compared as text and never looked up.
 */
internal class HostCheck(private val port: Int, private val lan: () -> List<InetAddress> = { emptyList() }) {
    /** [host] names 127.0.0.1, localhost, [::1] (in any case) or an address of [lan], with this server's port. */
    fun allowed(host: String?): Boolean {
        val (name, hostPort) = split(host?.lowercase() ?: return false) ?: return false
        return hostPort == port && (name in LOOPBACK_NAMES || lan().any { it.hostAddress == name })
    }

    /** `name:port` or `[address]:port` into the name (brackets kept) and the port; null without a port. */
    private fun split(host: String): Pair<String, Int>? {
        val colon = host.lastIndexOf(':')
        if (colon <= 0) return null
        val name = host.substring(0, colon)
        // An IPv6 address without brackets has colons of its own and no port to tell from them.
        if (':' in name && !(name.startsWith('[') && name.endsWith(']'))) return null
        val number = host.substring(colon + 1)
        if (number.isEmpty() || !number.all { it in '0'..'9' } || number.length > 5) return null
        return name to number.toInt()
    }

    private companion object {
        val LOOPBACK_NAMES = setOf("127.0.0.1", "localhost", "[::1]")
    }
}
