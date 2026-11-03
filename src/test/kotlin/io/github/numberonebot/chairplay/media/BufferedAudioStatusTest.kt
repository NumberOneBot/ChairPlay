package io.github.numberonebot.chairplay.media

import com.shilapi.xcertplay.airplay.*
import org.junit.Assert.*
import org.junit.Test
import java.io.DataOutputStream
import java.math.BigInteger
import java.net.Socket
import java.nio.ByteBuffer
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.atomic.AtomicLong

class BufferedAudioStatusTest : AudioRendererFixture() {
    @Test fun `format changes notify the UI without requiring a buffer size change`() {
        val monitor = BufferedAudioMonitor()
        val owner = Any()
        monitor.bufferedAudioOpened(owner)
        val plain = BufferedAudioStatus("playing", 48_000, 48_000, 4096)
        monitor.bufferedAudioUpdated(owner, plain)
        assertTrue(monitor.poll())
        assertFalse(monitor.state()!!.containsKey("multichannelOutput"))
        for (output in listOf("surround", "downmix", null)) {
            monitor.bufferedAudioUpdated(owner, plain.copy(multichannelOutput = output))
            assertTrue(monitor.poll())
            assertEquals(output, monitor.state()?.get("multichannelOutput"))
            assertFalse(monitor.poll())
        }
        monitor.bufferedAudioClosed(owner)
        assertNull(monitor.state())
    }

    @Test fun `close cannot leave a late opened monitor behind`() {
        val monitor = BufferedAudioMonitor()
        val opened = java.util.concurrent.CountDownLatch(1)
        val release = java.util.concurrent.CountDownLatch(1)
        val closing = java.util.concurrent.CountDownLatch(1)
        val executor = java.util.concurrent.Executors.newFixedThreadPool(2)
        val sink = object : MediaSink, BufferedAudioStatusSink by monitor {
            override fun bufferedAudioOpened(owner: Any) {
                opened.countDown()
                check(release.await(3, java.util.concurrent.TimeUnit.SECONDS))
                monitor.bufferedAudioOpened(owner)
            }
        }
        val stream = BufferedAudioStream(ByteArray(32), AudioFormat(AudioCodecKind.AAC_LC, 48_000, 2, 103, "media"), sink)
        try {
            val start = executor.submit { stream.start() }
            assertTrue(opened.await(3, java.util.concurrent.TimeUnit.SECONDS))
            val stop = executor.submit { closing.countDown(); stream.close() }
            assertTrue(closing.await(3, java.util.concurrent.TimeUnit.SECONDS))
            assertThrows(java.util.concurrent.TimeoutException::class.java) { stop.get(100, java.util.concurrent.TimeUnit.MILLISECONDS) }
            release.countDown()
            start.get(3, java.util.concurrent.TimeUnit.SECONDS)
            stop.get(3, java.util.concurrent.TimeUnit.SECONDS)
            assertNull(monitor.state())
        } finally {
            release.countDown()
            stream.close()
            executor.shutdownNow()
        }
    }

    @Test fun `receiver reports queued and prefed audio through pause flush drain and close`() {
        val monitor = BufferedAudioMonitor()
        val delivered = AtomicInteger()
        val sink = object : MediaSink, BufferedAudioStatusSink by monitor {
            override fun onAudioRtp(id: AudioStreamId, format: AudioFormat, rtp: ByteArray, sample: Int) {
                delivered.incrementAndGet()
            }
        }
        val clock = AtomicLong(1)
        val format = AudioFormat(AudioCodecKind.AAC_LC, 48_000, 2, 103, "media")
        val key = ByteArray(32) { it.toByte() }
        BufferedAudioStream(key, format, sink, nanoTime = clock::get).use { stream ->
            stream.start()
            assertEquals("empty", monitor.state()?.get("state"))
            Socket("127.0.0.1", stream.port).use { socket ->
                val out = DataOutputStream(socket.getOutputStream())
                repeat(60) { index ->
                    val header = ByteBuffer.allocate(12).apply {
                        put(0x80.toByte()); put(0x60.toByte()); putShort(index.toShort()); putInt(index * 1024); putInt(0)
                    }.array()
                    val tail = ByteBuffer.allocate(8).putLong(index.toLong()).array()
                    val nonce = ByteArray(12).also { tail.copyInto(it, 4) }
                    val body = header + AirPlayCrypto.chachaSeal(key, nonce, ByteArray(index + 1) { 0x21 }, header.copyOfRange(4, 12)) + tail
                    out.writeShort(body.size + 2); out.write(body)
                }
                out.flush()
                awaitTrue("preloaded seconds") { monitor.state()?.get("seconds") == 1.3 }
                assertEquals(2550L, monitor.state()?.get("bytes"))
                stream.setRate(0L, 1, BigInteger.ZERO)
                awaitTrue("prefed audio") { delivered.get() > 0 }
                // Moving packets to the renderer must not count them twice or remove unheard audio.
                assertEquals(1.3, monitor.state()?.get("seconds"))
                assertEquals(2550L, monitor.state()?.get("bytes"))
                clock.addAndGet(900_000_000)
                awaitTrue("audible position") { monitor.state() == mapOf("state" to "playing", "seconds" to 0.8, "bytes" to 1998L) }
                awaitTrue("remaining frames delivered") { delivered.get() == 60 }
                stream.setRate(null, 0, BigInteger.ZERO)
                awaitTrue("paused") { monitor.state()?.get("state") == "paused" }
                assertEquals(1962L, monitor.state()?.get("bytes"))
                val paused = monitor.state()
                clock.addAndGet(5_000_000_000)
                Thread.sleep(60)
                assertEquals(paused, monitor.state())
                stream.flush(50 * 1024L)
                awaitTrue("flushed buffer") { monitor.state()?.get("seconds") == 0.2 }
                assertEquals(675L, monitor.state()?.get("bytes"))
                stream.setRate(50 * 1024L, 1, BigInteger.ZERO)
                awaitTrue("seek feed") { delivered.get() >= 70 }
                clock.addAndGet(1_000_000_000)
                awaitTrue("empty buffer") { monitor.state() == mapOf("state" to "empty", "seconds" to 0.0, "bytes" to 0L) }
            }
        }
        assertNull(monitor.state())
    }

    @Test fun `empty stream stays empty without incoming audio even after playback was requested`() {
        val monitor = BufferedAudioMonitor()
        val sink = object : MediaSink, BufferedAudioStatusSink by monitor {}
        val format = AudioFormat(AudioCodecKind.AAC_LC, 48_000, 2, 103, "media")
        BufferedAudioStream(ByteArray(32), format, sink).use { stream ->
            stream.start()
            assertEquals("empty", monitor.state()?.get("state"))
            stream.setRate(0L, 1, BigInteger.ZERO)
            Thread.sleep(100)
            assertEquals(mapOf("state" to "empty", "seconds" to 0.0, "bytes" to 0L), monitor.state())
            stream.setRate(null, 0, BigInteger.ZERO)
            awaitTrue("explicit pause") { monitor.state()?.get("state") == "paused" }
        }
    }

    @Test fun `partial frames and timestamp wrap do not inflate buffered time`() {
        val status = BufferedAudioStatus.snapshot(1, true, true, 1, listOf(0xffff_ff00L, 768L), 256L, 48_000, 3456)
        assertEquals(2560L, status.samples)
        assertEquals(0.1, status.toPage()["seconds"])
        assertEquals(3456L, status.toPage()["bytes"])
        assertEquals(0L, BufferedAudioStatus.snapshot(1, true, true, 0, listOf(0L), 2048L, 48_000, 0).samples)
    }

    @Test fun `retired receiver cannot clear or update the replacement and poll only reports changes`() {
        val monitor = BufferedAudioMonitor()
        val old = Any()
        val current = Any()
        monitor.bufferedAudioOpened(old)
        assertTrue(monitor.poll())
        assertFalse(monitor.poll())
        monitor.bufferedAudioOpened(current)
        monitor.bufferedAudioUpdated(current, BufferedAudioStatus("paused", 48_000, 48_000, 4096))
        monitor.bufferedAudioUpdated(old, BufferedAudioStatus("playing", 96_000, 48_000, 8192))
        monitor.bufferedAudioClosed(old)
        assertEquals(mapOf("state" to "paused", "seconds" to 1.0, "bytes" to 4096L), monitor.state())
        assertTrue(monitor.poll())
        monitor.bufferedAudioUpdated(current, BufferedAudioStatus("paused", 48_000, 48_000, 4097))
        assertTrue(monitor.poll())
        monitor.bufferedAudioClosed(current)
        assertTrue(monitor.poll())
        assertNull(monitor.state())
    }
}
