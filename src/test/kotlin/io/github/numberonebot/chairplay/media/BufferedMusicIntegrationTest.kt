package io.github.numberonebot.chairplay.media

import com.shilapi.xcertplay.airplay.*
import io.github.numberonebot.chairplay.carplay.CarPlayMediaSink
import io.github.numberonebot.chairplay.phone.ScreenKind
import org.junit.Assert.*
import org.junit.Test
import java.io.DataOutputStream
import java.math.BigInteger
import java.net.Socket
import java.nio.ByteBuffer
import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.atomic.AtomicLong

/** Encrypted TCP AAC through the pinned upstream buffer and the actual desktop decoder/output. */
class BufferedMusicIntegrationTest : AudioRendererFixture() {
    @Test fun `pause resumes prefed AAC and flush removes the old desktop output`() {
        val clock = AtomicLong(1)
        val delivered = AtomicInteger()
        val lines = CopyOnWriteArrayList<RecordingLine>()
        val key = ByteArray(32) { (it + 1).toByte() }
        val format = AudioFormat(AudioCodecKind.AAC_LC, 48_000, 2, 103, "media")
        val units = encodeAac(48_000, 2, 3).take(3)
        val expected = AacPcmDecoder(48_000, 2).use { decoder -> units.flatMap { decoder.decode(it).toList() }.toByteArray() }
        AudioRenderer(lineFactory = { _, _, _ -> RecordingLine().also { lines.add(it) } }, clock = clock::get,
            startFade = { StartFade(2000, 800) }).use { renderer ->
            CarPlayMediaSink(ScreenDecoders(mapOf(ScreenKind.MAIN to VideoWindow(320, 240))), renderer).use { desktop ->
                val sink = object : MediaSink by desktop, BufferedAudioStatusSink by desktop {
                    override fun onAudioRtp(id: AudioStreamId, format: AudioFormat, rtp: ByteArray, sample: Int) {
                        desktop.onAudioRtp(id, format, rtp, sample)
                        delivered.incrementAndGet()
                    }
                }
                BufferedAudioStream(key, format, sink, nanoTime = clock::get).use { stream ->
                    stream.start()
                    Socket("127.0.0.1", stream.port).use { socket ->
                        val out = DataOutputStream(socket.getOutputStream())
                        units.forEachIndexed { index, payload ->
                            val header = ByteBuffer.allocate(12).apply {
                                put(0x80.toByte()); put(0x60.toByte()); putShort(index.toShort()); putInt(index * 1024); putInt(0)
                            }.array()
                            val tail = ByteBuffer.allocate(8).putLong(index.toLong()).array()
                            val nonce = ByteArray(12).also { tail.copyInto(it, 4) }
                            val sealed = AirPlayCrypto.chachaSeal(key, nonce, payload, header.copyOfRange(4, 12))
                            val body = header + sealed + tail
                            out.writeShort(body.size + 2); out.write(body)
                        }
                        out.flush()
                        stream.setRate(0L, 1, BigInteger.ZERO)
                        awaitTrue("prefed AAC") { delivered.get() == 3 && lines.size == 1 }
                        stream.setRate(null, 0, BigInteger.ZERO)
                        assertTrue(lines[0].closed)
                        assertEquals(0, lines[0].size())
                        assertEquals("none", renderer.musicStream())
                        awaitTrue("desktop paused snapshot") { desktop.bufferedAudio.state()?.get("state") == "paused" }
                        assertEquals(0.1, desktop.bufferedAudio.state()?.get("seconds"))

                        stream.setRate(null, 1, BigInteger.ZERO)
                        awaitTrue("resumed AAC") { delivered.get() == 6 && lines.size == 2 }
                        clock.addAndGet(400_000_000)
                        awaitTrue("decoded stereo") { lines[1].size() == expected.size }
                        assertArrayEquals(expected, lines[1].written.toByteArray())
                        assertTrue(samples(expected).any { it != 0 })
                        assertEquals("buffered", renderer.musicStream())

                        stream.flush(2048L)
                        assertTrue(lines[1].closed)
                        awaitTrue("seek output") { delivered.get() == 7 && lines.size == 3 }
                        clock.addAndGet(400_000_000)
                        val tailPcm = AacPcmDecoder(48_000, 2).use { it.decode(units.last()) }
                        awaitTrue("PCM after seek") { lines[2].size() == tailPcm.size }
                        assertArrayEquals(tailPcm, lines[2].written.toByteArray())
                    }
                }
                awaitTrue("closed buffered output") { renderer.musicStream() == "none" }
                assertTrue(lines.all { it.closed })
                assertNull(desktop.bufferedAudio.state())
            }
        }
    }
}
