package io.github.numberonebot.chairplay.media

import com.shilapi.xcertplay.airplay.AudioCodecKind
import com.shilapi.xcertplay.airplay.AudioFormat
import com.shilapi.xcertplay.airplay.AudioStreamId
import org.junit.Assert.*
import org.junit.Test
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicLong

class BufferedMusicOutputTest : AudioRendererFixture() {
    private val id = AudioStreamId(103, "media")
    // Exercise output timing independently of the codec; the protocol integration test covers AAC.
    private val format = AudioFormat(AudioCodecKind.LPCM, 48_000, 2, 103, "media")

    @Test fun `preloaded music is not shed as realtime latency`() {
        val clock = AtomicLong(1)
        val line = RecordingLine()
        AudioRenderer(lineFactory = { _, _, _ -> line }, clock = clock::get).use { renderer ->
            renderer.start(id, format)
            // 80 packets spanning 1.6 s would trigger the realtime renderer's latency trimming.
            val pcm = constantTenMs(2) + constantTenMs(2)
            repeat(80) { renderer.push(id, header(sample = it * 960) + pcm, it * 960) }
            clock.addAndGet(400_000_000)
            awaitTrue("all prefetched music") { line.size() == pcm.size * 80 }
            assertEquals(0, renderer.droppedPackets(id))
        }
    }

    @Test fun `pause before the audible anchor discards the old output`() {
        val line = RecordingLine()
        AudioRenderer(lineFactory = { _, _, _ -> line }, clock = { 0L }).use { renderer ->
            renderer.start(id, format)
            renderer.push(id, header() + constantTenMs(2), 0)
            renderer.stop(id)
            assertEquals(0, line.size())
            assertEquals("none", renderer.musicStream())
        }
    }

    @Test fun `buffered output obeys volume ducking and fade without changing prompt state`() {
        val clock = AtomicLong(1)
        val lines = java.util.concurrent.CopyOnWriteArrayList<RecordingLine>()
        val prompt = AudioStreamId(100, "default")
        AudioRenderer(lineFactory = { _, _, _ -> RecordingLine().also { lines.add(it) } },
            clock = clock::get, volume = { 0.5f }).use { renderer ->
            assertEquals("none", renderer.musicStream())
            renderer.start(id, format)
            awaitTrue("music line") { lines.size == 1 }
            renderer.start(prompt, format.copy(audioType = "default"))
            awaitTrue("prompt line") { lines.size == 2 }
            assertEquals("buffered", renderer.musicStream())
            renderer.duck(0.5f, 0)
            clock.addAndGet(400_000_000)
            renderer.push(id, header() + constantTenMs(2), 0)
            awaitTrue("ducked music") { lines[0].size() > 0 }
            assertTrue(samples(lines[0].written.toByteArray()).all { it == 0x400 })
            renderer.unduck(0)
            renderer.fadeOut(10)
            clock.addAndGet(20_000_000)
            renderer.push(id, header() + constantTenMs(2), 480)
            awaitTrue("faded music") { lines[0].size() == constantTenMs(2).size * 2 }
            assertTrue(samples(lines[0].written.toByteArray().takeLast(constantTenMs(2).size).toByteArray()).all { it == 0 })
            renderer.stop(id)
            assertEquals("none", renderer.musicStream())
            assertFalse(lines[1].closed)
        }
    }

    @Test fun `buffered starts on the upstream anchor without muting the beginning`() {
        val clock = AtomicLong(1)
        val line = RecordingLine()
        AudioRenderer(lineFactory = { _, _, _ -> line }, clock = clock::get,
            startFade = { StartFade(2000, 800) }).use { renderer ->
            renderer.start(id, format)
            repeat(10) { renderer.push(id, header() + constantTenMs(2), it * 480) }
            Thread.sleep(100)
            assertEquals(0, line.size())
            clock.addAndGet(400_000_000)
            awaitTrue("buffered PCM") { line.size() == 10 * constantTenMs(2).size }
            assertTrue(samples(line.written.toByteArray()).all { it == 0x1000 })
        }
        assertTrue(line.closed)
    }

    @Test fun `stopping buffered playback closes a blocked line before returning`() {
        val writing = CountDownLatch(1)
        val released = CountDownLatch(1)
        val clock = AtomicLong(1)
        val line = object : PcmLine {
            override fun start() {}
            override fun write(data: ByteArray, length: Int) {
                writing.countDown()
                while (released.count > 0) try { released.await() } catch (_: InterruptedException) {}
            }
            override fun close() { released.countDown() }
        }
        val renderer = AudioRenderer(lineFactory = { _, _, _ -> line }, clock = clock::get)
        try {
            renderer.start(id, format)
            repeat(10) { renderer.push(id, header() + constantTenMs(2), it * 480) }
            clock.addAndGet(400_000_000)
            assertTrue(writing.await(3, TimeUnit.SECONDS))
            renderer.stop(id)
            assertEquals(0L, released.count)
        } finally {
            released.countDown()
            renderer.close()
        }
    }
}
