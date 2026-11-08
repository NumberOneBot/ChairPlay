package com.shilapi.xcertplay.airplay

import java.io.Closeable
import java.net.DatagramPacket
import java.net.DatagramSocket
import java.net.InetAddress
import java.net.InetSocketAddress
import java.nio.ByteBuffer
import java.util.concurrent.CopyOnWriteArrayList

/**
 * The iPhone's side of the timing port on loopback: it records when each of NtpClock's requests arrives and, while
 * [answering], replies with its own clock, which reads [PHONE_UNIX_SECONDS] (in the anchor's networkTimeSecs) at start.
 */
internal class FakeTimingPeer(@Volatile var answering: Boolean = true) : Closeable {
    private val socket = DatagramSocket(InetSocketAddress(InetAddress.getLoopbackAddress(), 0))
    private val startNs = System.nanoTime()
    val requestTimesNs = CopyOnWriteArrayList<Long>()
    val port: Int get() = socket.localPort

    init {
        Thread(::serve, "fake-timing-peer").apply { isDaemon = true; start() }
    }

    private fun serve() {
        val buffer = ByteArray(64)
        while (!socket.isClosed) {
            val packet = DatagramPacket(buffer, buffer.size)
            try { socket.receive(packet) } catch (_: Exception) { return }
            if (packet.length < 32 || (buffer[1].toInt() and 0xff) != 210) continue
            requestTimesNs.add(System.nanoTime())
            if (!answering) continue
            val now = phoneNtp()
            val response = ByteBuffer.allocate(32).put(0x80.toByte()).put(211.toByte())
                .putShort(7).putInt(0).putLong(ByteBuffer.wrap(buffer).getLong(24)).putLong(now).putLong(now).array()
            runCatching { socket.send(DatagramPacket(response, response.size, packet.socketAddress)) }
        }
    }

    /** NTP64 on the phone's clock; the bits of an unsigned value, as NtpClock reads them. */
    private fun phoneNtp(): Long {
        val elapsed = System.nanoTime() - startNs
        val fraction = (elapsed % 1_000_000_000L) * (1L shl 32) / 1_000_000_000L
        return ((PHONE_UNIX_SECONDS + NTP_UNIX_OFFSET + elapsed / 1_000_000_000L) shl 32) + fraction
    }

    /** Waits until [count] requests arrived, at most [timeoutMs]. */
    fun awaitRequests(count: Int, timeoutMs: Long): Boolean {
        val deadline = System.nanoTime() + timeoutMs * 1_000_000L
        while (requestTimesNs.size < count && System.nanoTime() < deadline) Thread.sleep(10)
        return requestTimesNs.size >= count
    }

    /** Milliseconds between request [index] and the one before it. */
    fun gapMs(index: Int): Long = (requestTimesNs[index] - requestTimesNs[index - 1]) / 1_000_000L

    override fun close() = socket.close()

    companion object {
        const val PHONE_UNIX_SECONDS = 100_000L
        private const val NTP_UNIX_OFFSET = 2_208_988_800L
    }
}
