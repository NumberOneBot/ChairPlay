package io.github.numberonebot.chairplay.media

import com.shilapi.xcertplay.airplay.AudioCodecKind
import com.shilapi.xcertplay.airplay.AudioFormat
import com.shilapi.xcertplay.airplay.AudioStreamId
import com.shilapi.xcertplay.airplay.VideoCodec
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.ByteArrayOutputStream
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicLong

/** A VideoWindow that never runs opens no window, so the sink is tested whole. */
class DesktopMediaSinkTest {
    private fun resource(name: String) =
        javaClass.getResourceAsStream("/h264/$name")!!.use { it.readBytes() }

    @Test
    fun `recovery handler that ends the stream does not free the decoder mid-decode`() {
        val sink = DesktopMediaSink(VideoWindow(320, 240), AudioRenderer())
        val handlerDone = CountDownLatch(1)
        val handlerThread = arrayOfNulls<Thread>(1)
        sink.setVideoRecoveryHandler(110) {
            handlerThread[0] = Thread.currentThread()
            // What a failed forceKeyFrame send does: the session closes and stops the stream.
            sink.onScreenStreamActive(110, false)
            handlerDone.countDown()
        }
        sink.onVideoCodec(110, VideoCodec.H264)
        sink.onVideoConfig(110, resource("avcC.bin"))
        // Slice NALs with a corrupt body: the decoder rejects them.
        val corrupt = byteArrayOf(0, 0, 0, 1, 0x65, 0x00, 0x00, 0x00, 0x03, 0x7f, 0x01)
        sink.onVideoFrame(110, corrupt, 0, 0)
        val caller = Thread.currentThread()
        assertTrue("recovery was never requested", handlerDone.await(5, TimeUnit.SECONDS))
        assertTrue("recovery ran on the decoding thread", handlerThread[0] !== caller)
        // The stream is gone; frames after it are dropped rather than touching freed memory.
        sink.onVideoFrame(110, resource("frame0.h264"), 0, 0)
        sink.close()
    }

    // At 1920x1080 the iPhone can send the same configuration again right after the keyframe, and no keyframe after it.
    // A new decoder would wait for one; the running one goes on with the frames that follow, as AndroidMediaSink does.
    @Test
    fun `the same configuration again keeps the decoder`() {
        val sink = DesktopMediaSink(VideoWindow(320, 240), AudioRenderer())
        var keyframesAsked = 0
        sink.setVideoRecoveryHandler(110) { keyframesAsked++ }
        sink.onVideoCodec(110, VideoCodec.H264)
        sink.onVideoConfig(110, resource("avcC.bin"))
        sink.onVideoFrame(110, resource("frame0.h264"), 0, 0)

        sink.onVideoConfig(110, resource("avcC.bin"))
        sink.onVideoFrame(110, resource("frame1.h264"), 0, 0)
        sink.close()

        assertEquals(0, keyframesAsked)
    }

    // The AltScreen's state goes to the iPhone once per stream it sets up, on that stream's first picture: not on each
    // picture, nor on a decoder configured again, which a view asked for can bring and would ask again, in a loop.
    @Test
    fun `an AltScreen stream set up is reported once, at its first picture`() {
        val altscreen = VideoWindow(320, 240)
        val sink = DesktopMediaSink(VideoWindow(320, 240), AudioRenderer(), altscreen = altscreen)
        var reported = 0
        sink.onAltscreenStream = { reported++ }

        altscreen.onFrameShown()
        sink.onScreenStreamActive(111, true)
        altscreen.onFrameShown()
        altscreen.onFrameShown()
        assertEquals(1, reported)

        sink.onScreenStreamActive(111, true)
        altscreen.onFrameShown()
        assertEquals(2, reported)
        sink.close()
    }

    private class RecordingLine : PcmLine {
        val written = ByteArrayOutputStream()
        override fun start() {}
        @Synchronized override fun write(data: ByteArray, length: Int) { written.write(data, 0, length) }
        @Synchronized fun bytes(): ByteArray = written.toByteArray()
        override fun playedFrames(): Long? = null
        override fun close() {}
    }

    // audio.duck.db: the music under a navigation prompt is as loud as set, whatever level the iPhone asks for.
    @Test
    fun `a prompt ducks the music to the level set`() {
        val line = RecordingLine()
        val clock = AtomicLong(1)
        val renderer = AudioRenderer({}, { _, _, _ -> line }, { clock.get() }, 5)
        val sink = DesktopMediaSink(VideoWindow(320, 240), renderer, duckSetting = { DuckSetting(0.5f, iPhoneFirst = false) })
        val media = AudioStreamId(102, "media")
        renderer.start(media, AudioFormat(AudioCodecKind.LPCM, 48_000, 2, 96, "media"))

        sink.onAudioDuck(true, mapOf("volume" to 0.1))
        clock.addAndGet(TimeUnit.SECONDS.toNanos(1))
        // 10 ms of big-endian LPCM a packet, every sample 0x1000.
        repeat(6) { n ->
            val rtp = ByteArray(12).also { it[0] = 0x80.toByte() } + ByteArray(480 * 4) { if (it % 2 == 0) 0x10 else 0 }
            renderer.push(media, rtp, n * 480)
        }
        val deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(5)
        while (line.bytes().size < 6 * 480 * 4) {
            assertTrue("timed out waiting for playback", System.nanoTime() < deadline)
            Thread.sleep(5)
        }

        val samples = line.bytes().toList().chunked(2).map { (low, high) -> ((high.toInt() shl 8) or (low.toInt() and 0xff)).toShort().toInt() }
        assertTrue(samples.distinct().toString(), samples.all { it == 0x0800 })
        renderer.close()
    }
}
