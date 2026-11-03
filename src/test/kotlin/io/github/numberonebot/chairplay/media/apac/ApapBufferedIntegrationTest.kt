package io.github.numberonebot.chairplay.media.apac

import com.shilapi.xcertplay.airplay.*
import org.junit.Assert.*
import org.junit.Test
import java.math.BigInteger
import java.net.Socket
import java.nio.ByteBuffer
import java.util.concurrent.CopyOnWriteArrayList

class ApapBufferedIntegrationTest {
    @Test fun `APAP TCP stream reaches spatial output without passing through RTP callbacks`() {
        val devices = CopyOnWriteArrayList<TestSpatialDevice>()
        val key = ByteArray(32) { it.toByte() }
        val worker = SpatialWorker({ TestSpatialDevice().also(devices::add) },
            { object : SpatialPacketDecoder {
                override fun decode(unit: ApapAccessUnit) = SpatialDecoded(SpatialCodec.AAC, 2,
                    ShortArray(2048) { unit.audio[0].toShort() })
                override fun discontinuity() {}
                override fun close() {}
            } }, { 1f }, { 1f }, {}, emptyMap(), startDelayNs = 0)
        val source = ApapStreamSource(key, worker)
        val format = AudioStreamCodec.fromFormatBits(0x800000, 103, "media")
        val sink = object : MediaSink {
            override fun onAudioStarted(id: AudioStreamId, format: AudioFormat, firstSample: Int) { fail("APAP started RTP renderer") }
            override fun onAudioRtp(id: AudioStreamId, format: AudioFormat, rtp: ByteArray, sample: Int) { fail("APAP reached RTP decoder") }
        }
        val stream = BufferedAudioStream(key, format, sink, apap = source)
        try {
            stream.start()
            Socket("127.0.0.1", stream.port).use { socket ->
                socket.getOutputStream().write(packet(key, 1, 0))
                socket.getOutputStream().write(packet(key, 1, 0)) // Retransmission after a control transition.
                socket.getOutputStream().write(packet(key, 2, 1024))
                Thread.sleep(50)
                assertTrue(devices.isEmpty())
                stream.setRate(0, 1, BigInteger.ZERO)
                await { (devices.firstOrNull()?.samples?.size ?: 0) >= 2 }
                Thread.sleep(80)
                assertEquals(2, devices[0].samples.size)
                assertEquals(21_168_000, stream.audioBufferBytes)
                assertTrue(devices[0].samples[0].all { it == 1.toShort() })
                stream.setRate(null, 0, BigInteger.ZERO)
                assertTrue(devices[0].closed)
                assertEquals(0, stream.anchor()!!["rate"])
            }
        } finally { stream.close() }
        assertTrue(worker.cleanupComplete)
        assertTrue(stream.outputCleanupComplete)
    }

    private fun packet(key: ByteArray, sequence: Int, timestamp: Long): ByteArray {
        val header = ByteBuffer.allocate(15).putLong(timestamp).putInt(48000)
            .put(0).put(0).put(sequence.toByte()).array()
        val cookie = aacCookie()
        val plain = byteArrayOf(18, cookie.size.toByte()) + cookie + byteArrayOf(0, sequence.toByte())
        val nonce = ByteArray(12).also { it[11] = sequence.toByte() }
        val sealed = AirPlayCrypto.chachaSeal(key, nonce, plain, header.copyOfRange(0, 12))
        val body = header + sealed + nonce.copyOfRange(4, 12)
        return ByteBuffer.allocate(4).putInt(body.size + 4).array() + body
    }
    private fun await(condition: () -> Boolean) {
        val deadline = System.nanoTime() + 3_000_000_000
        while (!condition() && System.nanoTime() < deadline) Thread.sleep(5)
        assertTrue(condition())
    }
}
