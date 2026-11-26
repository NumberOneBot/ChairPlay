package io.github.numberonebot.chairplay.media

import com.shilapi.xcertplay.airplay.AudioCodecKind
import com.shilapi.xcertplay.airplay.AudioFormat
import com.shilapi.xcertplay.airplay.AudioStreamId
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.CountDownLatch
import java.util.concurrent.atomic.AtomicLong
import java.util.concurrent.TimeUnit

/** AudioRenderer: the jitter buffer: when playback starts and rebuffers, and the late or surplus audio it drops. */
class AudioRendererBufferTest : AudioRendererFixture() {
    @Test
    fun `late packet older than playhead dropped`() {
        val line = RecordingLine()
        val renderer = AudioRenderer(lineFactory = { _, _, _ -> line })
        val id = AudioStreamId(101, "media")
        renderer.start(id, AudioFormat(AudioCodecKind.LPCM, 44_100, 2, 96, "media"))
        val body = ByteArray(8)
        renderer.push(id, header(sample = 0) + body, 0)
        renderer.push(id, header(sample = 4800) + body, 4800)
        renderer.push(id, header(sample = 960) + body, 960)
        assertEquals(1, renderer.droppedPackets(id))
        renderer.close()
        assertTrue(line.closed)
    }

    @Test
    fun `playback starts after the jitter buffer fills`() {
        val line = RecordingLine()
        val renderer = AudioRenderer(lineFactory = { _, _, _ -> line })
        val id = AudioStreamId(100, "alert")
        renderer.start(id, AudioFormat(AudioCodecKind.LPCM, 48_000, 2, 96, "alert"))
        // 10 ms of stereo 16-bit at 48 kHz: the 40 ms buffer of a non-media stream needs four of them.
        val tenMs = ByteArray(480 * 4)
        repeat(3) { renderer.push(id, header(sample = it * 480) + tenMs, it * 480) }
        Thread.sleep(150)
        assertEquals(0, line.size())
        renderer.push(id, header(sample = 3 * 480) + tenMs, 3 * 480)
        awaitTrue("playback") { line.size() == 4 * tenMs.size }
        assertTrue(line.started)
        renderer.stop(id)
        assertTrue(line.closed)
    }

    private val tenMs = ByteArray(480 * 4)

    private fun alertRenderer(line: RecordingLine, clock: AtomicLong, logs: MutableList<String> = CopyOnWriteArrayList()) =
        AudioRenderer({ logs += it }, { _, _, _ -> line }, { clock.get() }, 5)

    private val alert = AudioStreamId(100, "alert")
    private val alertFormat = AudioFormat(AudioCodecKind.LPCM, 48_000, 2, 96, "alert")

    @Test
    fun `starved line rebuffers before writing again`() {
        val line = RecordingLine()
        val clock = AtomicLong(1)
        val logs = CopyOnWriteArrayList<String>()
        val renderer = alertRenderer(line, clock, logs)
        renderer.start(alert, alertFormat)
        repeat(4) { renderer.push(alert, header(sample = it * 480) + tenMs, it * 480) }
        awaitTrue("first playback") { line.size() == 4 * tenMs.size }
        // The 40 ms written have long been played: the next packet finds an empty line.
        clock.addAndGet(TimeUnit.SECONDS.toNanos(1))
        renderer.push(alert, header(sample = 4 * 480) + tenMs, 4 * 480)
        awaitTrue("underrun noticed") { logs.any { "underrun" in it } }
        Thread.sleep(50)
        assertEquals(4 * tenMs.size, line.size())
        repeat(3) { renderer.push(alert, header(sample = (5 + it) * 480) + tenMs, (5 + it) * 480) }
        awaitTrue("playback after rebuffer") { line.size() == 8 * tenMs.size }
        renderer.close()
    }

    @Test
    fun `burst far beyond the target sheds the oldest audio`() {
        val line = RecordingLine()
        val clock = AtomicLong(1)
        val logs = CopyOnWriteArrayList<String>()
        val opened = CountDownLatch(1)
        // The writer waits in open(), so the queue is not drained while the burst arrives.
        val renderer = AudioRenderer({ logs += it }, { _, _, _ -> opened.await(); line }, { clock.get() }, 5)
        renderer.start(alert, alertFormat)
        repeat(200) { renderer.push(alert, header(sample = it * 480) + tenMs, it * 480) }
        val dropped = renderer.droppedPackets(alert)
        // 2 s arrived; the queue was cut to 0.5 s when it passed 1.5 s and has grown by what followed.
        assertTrue("dropped $dropped", dropped in 90..110)
        assertTrue(logs.any { "shed" in it })
        opened.countDown()
        awaitTrue("newest audio played") { line.size() == (200 - dropped) * tenMs.size }
        renderer.close()
    }
}
