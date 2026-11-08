package io.github.numberonebot.chairplay.ui

import java.net.InetAddress

/**
 * What the UI's HTTP server on [port] asks of a request, in place of a secret: that it names this PC by a loopback
 * name with this port ([hostAllowed]), so a web site whose own name resolves to 127.0.0.1 (DNS rebinding) is refused;
 * that a POST comes from no page or from the page at that same address ([originAllowed]), so another site's page
 * cannot change anything; and that the saved Wi-Fi password goes only to this PC ([fromThisPc]). Names are compared
 * as text and never looked up.
 */
internal class RequestChecks(private val port: Int) {
    /** The request's `Host` header names 127.0.0.1, localhost or [::1], in any case, with this server's port. */
    fun hostAllowed(host: String?): Boolean {
        val (name, hostPort) = split(host?.lowercase() ?: return false) ?: return false
        return hostPort == port && name in LOOPBACK_NAMES
    }

    /** A POST's `Origin`: none (not a browser's page), or the page at the request's [host] itself, ignoring case. */
    fun originAllowed(origin: String?, host: String): Boolean = origin == null || origin.equals("http://$host", ignoreCase = true)

    /** The client is on this PC. */
    fun fromThisPc(remote: InetAddress): Boolean = remote.isLoopbackAddress

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
