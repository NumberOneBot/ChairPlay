package io.github.numberonebot.chairplay.ui

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.Closeable
import java.net.InetAddress
import java.util.concurrent.CompletableFuture
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

/** This PC's addresses that another device on its network opens the settings at, with ui.network on. */
class LanAddressesTest {
    private fun ip(text: String): InetAddress = InetAddress.getByName(text)

    /** Stands in for the daemon timer: the test runs the walk itself. */
    private class Schedule {
        var task: (() -> Unit)? = null
        var periodMillis = 0L
        var stopped = false
        val every: (Long, () -> Unit) -> Closeable = { period, task ->
            this.task = task
            periodMillis = period
            Closeable { stopped = true }
        }

        fun run() = task!!()
    }

    /** [LanAddresses.current] on another thread, which must answer within a second however long the walk takes. */
    private fun currentAtOnce(addresses: LanAddresses): List<InetAddress> =
        CompletableFuture.supplyAsync { addresses.current() }.get(1, TimeUnit.SECONDS)

    // The settings' event stream asks on every state; walking the adapters is slow on a PC with many of them.
    @Test
    fun `answers at once with nothing before the first walk`() {
        val walk = CountDownLatch(1)
        val addresses = LanAddresses(read = { walk.await(); listOf(ip("192.168.1.20")) })
        try {
            assertEquals(emptyList<InetAddress>(), currentAtOnce(addresses))
        } finally {
            walk.countDown()
        }
    }

    @Test
    fun `answers at once with the last list while the adapters are walked again`() {
        val walk = CountDownLatch(1)
        var reads = 0
        val schedule = Schedule()
        val addresses = LanAddresses(
            read = { if (reads++ == 0) listOf(ip("192.168.1.20")) else { walk.await(); listOf(ip("10.0.0.7")) } },
            every = schedule.every,
        )
        addresses.start()
        schedule.run()

        val again = Thread { schedule.run() }.apply { start() }
        try {
            assertEquals(listOf(ip("192.168.1.20")), currentAtOnce(addresses))
        } finally {
            walk.countDown()
            again.join(5_000)
        }
        assertEquals(listOf(ip("10.0.0.7")), currentAtOnce(addresses))
    }

    // A browser takes an IPv6 link-local address only with a zone id; the page lists IPv4 addresses, in any range.
    @Test
    fun `keeps the IPv4 addresses but loopback`() {
        val schedule = Schedule()
        val addresses = LanAddresses(
            read = { listOf(ip("127.0.0.1"), ip("192.168.1.20"), ip("fe80::1"), ip("100.64.0.5"), ip("fd12::5")) },
            every = schedule.every,
        )
        addresses.start()
        schedule.run()

        assertEquals(listOf(ip("192.168.1.20"), ip("100.64.0.5")), addresses.current())
    }

    // An address the PC gets from a new network counts within ten seconds.
    @Test
    fun `walks every ten seconds from start to close`() {
        var reads = 0
        val schedule = Schedule()
        val addresses = LanAddresses(read = { reads++; emptyList() }, every = schedule.every)

        addresses.current()
        assertEquals(0, reads)
        assertEquals(null, schedule.task)

        addresses.start()
        assertEquals(10_000L, schedule.periodMillis)
        addresses.close()
        assertTrue(schedule.stopped)
    }

    // A failed walk would otherwise stop the timer for good.
    @Test
    fun `keeps the last list when a walk fails`() {
        var fails = false
        val schedule = Schedule()
        val addresses = LanAddresses(
            read = { if (fails) error("no answer") else listOf(ip("192.168.1.20")) },
            every = schedule.every,
        )
        addresses.start()
        schedule.run()
        fails = true
        schedule.run()

        assertEquals(listOf(ip("192.168.1.20")), addresses.current())
    }
}
