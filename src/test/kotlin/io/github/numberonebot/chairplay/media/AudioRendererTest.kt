package io.github.numberonebot.chairplay.media

import com.shilapi.xcertplay.airplay.AudioCodecKind
import com.shilapi.xcertplay.airplay.AudioFormat
import com.shilapi.xcertplay.airplay.AudioStreamId
import com.shilapi.xcertplay.media.EchoReference
import org.concentus.OpusApplication
import org.concentus.OpusEncoder
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.concurrent.CopyOnWriteArrayList
import kotlin.math.PI
import kotlin.math.sin

/** AudioRenderer: RTP payloads, the decoders, broken streams and packets, and a call's echo reference. */
class AudioRendererTest : AudioRendererFixture() {
    @Test
    fun `strips rtp header with csrc`() {
        val payload = byteArrayOf(9, 8, 7)
        assertArrayEquals(payload, rtpPayload(header(cc = 0) + payload))
        assertArrayEquals(payload, rtpPayload(header(cc = 2) + payload))
        // The payload starts at byte 20 when two CSRC entries follow the fixed header.
        val rtp = ByteArray(24) { it.toByte() }.also { it[0] = 0x82.toByte() }
        assertEquals(20.toByte(), rtpPayload(rtp)[0])
        assertEquals(0, rtpPayload(ByteArray(11)).size)
    }

    @Test
    fun `lpcm swapped to little endian`() {
        assertArrayEquals(byteArrayOf(0x34, 0x12), lpcmBigEndianToLittle(byteArrayOf(0x12, 0x34)))
        assertArrayEquals(byteArrayOf(2, 1, 4, 3), lpcmBigEndianToLittle(byteArrayOf(1, 2, 3, 4, 5)))
    }

    @Test
    fun `opus packet decodes to 960 samples at 48k`() {
        val encoder = OpusEncoder(48_000, 2, OpusApplication.OPUS_APPLICATION_AUDIO)
        val pcm = ShortArray(960 * 2) { (sin(2 * PI * 440 * (it / 2) / 48_000) * 8000).toInt().toShort() }
        val packet = ByteArray(1275)
        val length = encoder.encode(pcm, 0, 960, packet, 0, packet.size)
        OpusPcmDecoder(48_000, 2).use { decoder ->
            assertEquals(960 * 2 * 2, decoder.decode(packet.copyOf(length)).size)
        }
    }

    @Test
    fun `aac access unit decodes to 1024 samples`() {
        val units = encodeAac(44_100, 2)
        AacPcmDecoder(44_100, 2).use { decoder ->
            assertEquals(1024 * 2 * 2, decoder.decode(units.first()).size)
        }
    }

    // As AndroidMediaSink does, a call stream hands what it writes, and the line's queue, to the echo reference.
    @Test
    fun `a call stream feeds its echo reference with what it writes`() {
        val line = RecordingLine().apply { played = 0L }
        val renderer = AudioRenderer(lineFactory = { _, _, _ -> line })
        val id = AudioStreamId(100, "telephony")
        val reference = EchoReference(48_000)
        renderer.start(id, AudioFormat(AudioCodecKind.LPCM, 48_000, 1, 100, "telephony"), reference)
        // 10 ms of mono big-endian LPCM at 48 kHz, every sample 0x1000; four fill the 40 ms jitter buffer.
        val tenMs = ByteArray(480 * 2) { if (it % 2 == 0) 0x10 else 0 }
        repeat(4) { renderer.push(id, header(sample = it * 480) + tenMs, it * 480) }
        awaitTrue("playback") { line.size() == 4 * tenMs.size }

        val out = ShortArray(480)
        assertTrue("the reference hears the call", reference.read(out, System.nanoTime() + 20_000_000L))
        assertTrue(out.all { it == 0x1000.toShort() })
        renderer.stop(id)
    }

    @Test
    fun `undecodable stream is dropped without throwing`() {
        val logs = CopyOnWriteArrayList<String>()
        val renderer = AudioRenderer(log = { logs += it }, lineFactory = { _, _, _ -> RecordingLine() })
        val id = AudioStreamId(101, "media")
        renderer.start(id, AudioFormat(AudioCodecKind.OPUS, 48_000, 6, 96, "media"))
        renderer.push(id, header() + ByteArray(8), 0)
        renderer.stop(id)
        assertEquals(1, logs.size)
        renderer.close()
    }

    @Test
    fun `a corrupt packet between good ones costs only its own audio`() {
        val units = encodeAac(44_100, 2, count = 6)
        val corrupt = ByteArray(64) { 0xff.toByte() }
        // The packet must really make the decoder throw, or this test proves nothing.
        AacPcmDecoder(44_100, 2).use { decoder ->
            assertTrue(runCatching { decoder.decode(corrupt) }.isFailure)
        }
        val line = RecordingLine()
        val logs = CopyOnWriteArrayList<String>()
        val renderer = AudioRenderer(log = { logs += it }, lineFactory = { _, _, _ -> line })
        val id = AudioStreamId(101, "media")
        renderer.start(id, AudioFormat(AudioCodecKind.AAC_LC, 44_100, 2, 96, "media"))
        val packets = units.take(3) + listOf(corrupt) + units.drop(3)
        packets.forEachIndexed { index, payload -> renderer.push(id, header(sample = index * 1024) + payload, index * 1024) }

        // Six good access units of 1024 stereo 16-bit samples each.
        awaitTrue("audio after the corrupt packet") { line.size() == 6 * 1024 * 2 * 2 }
        assertTrue(logs.toString(), logs.any { "decode errors" in it })
        assertTrue(logs.none { "stopped" in it })
        renderer.close()
    }

}
