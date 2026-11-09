package io.github.numberonebot.chairplay.ui

import io.github.numberonebot.chairplay.network.physicalInterfaceAddresses
import java.io.Closeable
import java.net.Inet4Address
import java.net.InetAddress
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit

/**
 * This PC's addresses that another device opens the settings at with ui.network on: its physical adapters' IPv4
 * addresses, in any range, never a virtual switch's (Hyper-V's or WSL's vEthernet), which no other device reaches (a
 * browser takes an IPv6 link-local address only with a zone id, so the page lists IPv4 ones only).
 * [read] walks the adapters, which is slow on a PC with many of them, and the Host check and the settings' event stream
 * ask on every request and every state: from [start] to [close], a daemon timer ([every]) walks them at once and then
 * every [periodMillis], so an address the PC gets from a new network counts within that time, and [current] gives the
 * last walk's list without waiting (none before the first walk ends).
 */
internal class LanAddresses(
    private val read: () -> List<InetAddress> = ::physicalInterfaceAddresses,
    private val every: (Long, () -> Unit) -> Closeable = ::everyOnDaemon,
    private val periodMillis: Long = 10_000,
    private val log: (String) -> Unit = {},
) : Closeable {
    @Volatile private var cached: List<InetAddress> = emptyList()
    private var timer: Closeable? = null

    fun current(): List<InetAddress> = cached

    @Synchronized
    fun start() {
        if (timer == null) timer = every(periodMillis, ::refresh)
    }

    @Synchronized
    override fun close() {
        timer?.close()
        timer = null
    }

    private fun refresh() {
        try {
            cached = read().filter { it is Inet4Address && !it.isLoopbackAddress }
        } catch (error: Exception) {
            log("Could not list the network addresses: $error")
        }
    }
}

/** Runs [task] at once and then [periodMillis] after each run ends, on a daemon thread, until closed. */
private fun everyOnDaemon(periodMillis: Long, task: () -> Unit): Closeable {
    val scheduler = Executors.newSingleThreadScheduledExecutor { Thread(it, "lan-addresses").apply { isDaemon = true } }
    scheduler.scheduleWithFixedDelay({ task() }, 0, periodMillis, TimeUnit.MILLISECONDS)
    return Closeable { scheduler.shutdownNow() }
}
