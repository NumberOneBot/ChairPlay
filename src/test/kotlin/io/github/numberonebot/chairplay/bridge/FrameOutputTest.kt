package io.github.numberonebot.chairplay.bridge

import io.github.numberonebot.chairplay.config.AreaRect
import io.github.numberonebot.chairplay.config.WindowSize
import io.github.numberonebot.chairplay.media.DecodedFrame
import io.github.numberonebot.chairplay.media.FramePool
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.nio.ByteBuffer
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

class FrameOutputTest {
    private class Published(val width: Int, val height: Int, val safe: AreaRect, val firstPixel: Int)

    /** Records what the output asks of the native side; a publish leaves state 2, as cpb_frame_publish does. */
    private class FakeSink(val openError: String? = null, val publishError: String? = null) : FrameSink {
        val events = mutableListOf<String>()
        val published = mutableListOf<Published>()
        var state = 0
        var onPublish: (ByteBuffer) -> Unit = {}

        override fun open(): String? {
            events += "open"
            if (openError == null) state = 1
            return openError
        }

        override fun publish(bgra: ByteBuffer, width: Int, height: Int, safe: AreaRect): String? {
            events += "publish"
            published += Published(width, height, safe, bgra.getInt(0))
            onPublish(bgra)
            if (publishError == null) state = 2
            return publishError
        }

        override fun state(state: Int) {
            events += "state $state"
            this.state = state
        }

        override fun close() {
            events += "close"
            state = 0
        }

        fun count(event: String) = events.count { it == event }
    }

    private var now = 0L
    private val logs = mutableListOf<String>()
    private var workers = 0

    /** The worker loop never runs on its own: each test drives one pass at a time with [FrameOutput.pass]. */
    private fun output(
        sink: FrameSink,
        screen: WindowSize = WindowSize(64, 64),
        safe: AreaRect = AreaRect(0, 0, 64, 64),
    ) = FrameOutput(sink, { screen }, { safe }, logs::add, { now }, worker = { workers++; Thread {} })

    private fun frame(width: Int = 64, height: Int = 64, luma: Int = 16): DecodedFrame {
        val frame = FramePool().take(width, height)
        for (i in 0 until frame.y.capacity()) frame.y.put(i, luma.toByte())
        for (i in 0 until frame.u.capacity()) frame.u.put(i, 128.toByte())
        for (i in 0 until frame.v.capacity()) frame.v.put(i, 128.toByte())
        return frame
    }

    @Test fun `publishes main screen frames only`() {
        val sink = FakeSink()
        val output = output(sink)
        output.enabled = true
        output.frame(ALT_SCREEN, frame())
        output.pass(0)
        assertEquals(0, sink.count("publish"))
        output.frame(MAIN_SCREEN, frame())
        output.pass(0)
        assertEquals(1, sink.count("publish"))
        assertEquals(2, sink.state)
    }

    @Test fun `stream stop sets waiting`() {
        val sink = FakeSink()
        val output = output(sink)
        output.enabled = true
        output.active(MAIN_SCREEN, true)
        output.frame(MAIN_SCREEN, frame())
        output.pass(0)
        output.active(ALT_SCREEN, false)
        output.pass(0)
        assertEquals(2, sink.state)
        output.active(MAIN_SCREEN, false)
        output.pass(0)
        assertEquals("state 1", sink.events.last())
        // A stopped stream is waiting, not frozen.
        now += 5_000_000_000L
        output.pass(0)
        assertEquals(1, sink.state)
    }

    @Test fun `a picture older than a second is frozen`() {
        val sink = FakeSink()
        val output = output(sink)
        output.enabled = true
        output.active(MAIN_SCREEN, true)
        output.frame(MAIN_SCREEN, frame())
        output.pass(0)
        now += 900_000_000L
        output.pass(0)
        assertEquals(2, sink.state)
        now += 200_000_000L
        output.pass(0)
        assertEquals(3, sink.state)
        output.pass(0)
        assertEquals(1, sink.count("state 3"))
        output.frame(MAIN_SCREEN, frame())
        output.pass(0)
        assertEquals(2, sink.count("publish"))
        assertEquals(2, sink.state)
    }

    @Test fun `turning off closes the native side`() {
        val sink = FakeSink()
        val output = output(sink)
        output.enabled = true
        output.frame(MAIN_SCREEN, frame())
        output.pass(0)
        output.enabled = false
        output.enabled = false
        assertEquals(1, sink.count("close"))
        output.frame(MAIN_SCREEN, frame())
        assertFalse(output.pass(0))
        assertEquals(1, sink.count("publish"))
    }

    @Test fun `nothing opens while off`() {
        val sink = FakeSink()
        val output = output(sink)
        output.active(MAIN_SCREEN, true)
        repeat(3) { output.frame(MAIN_SCREEN, frame()) }
        output.active(MAIN_SCREEN, false)
        output.close()
        assertEquals(emptyList<String>(), sink.events)
        assertEquals(0, workers)
    }

    @Test fun `a failing native side is logged once and stops publishing`() {
        val sink = FakeSink(publishError = "E_FAIL")
        val output = output(sink)
        output.enabled = true
        repeat(3) {
            output.frame(MAIN_SCREEN, frame())
            output.pass(0)
        }
        assertEquals(1, sink.count("publish"))
        assertEquals(1, logs.count { "E_FAIL" in it })
        // The failure let the native side go at once (state 0 for a game); turning off does not close it twice.
        assertEquals(1, sink.count("close"))
        output.enabled = false
        assertEquals(1, sink.count("close"))
        // Off and on tries again.
        output.enabled = true
        output.frame(MAIN_SCREEN, frame())
        output.pass(0)
        assertEquals(2, sink.count("open"))
        assertEquals(2, sink.count("publish"))
    }

    @Test fun `a native side that cannot open is logged and starts nothing`() {
        val sink = FakeSink(openError = "no D3D11 device")
        val output = output(sink)
        output.enabled = true
        output.frame(MAIN_SCREEN, frame())
        assertFalse(output.pass(0))
        assertEquals(listOf("open"), sink.events)
        assertEquals(1, logs.count { "no D3D11 device" in it })
        assertEquals(0, workers)
    }

    @Test fun `the safe rect follows the frame size`() {
        val sink = FakeSink()
        val output = output(sink, WindowSize(1280, 720), AreaRect(100, 20, 1180, 700))
        output.enabled = true
        output.frame(MAIN_SCREEN, frame(640, 360))
        output.pass(0)
        output.frame(MAIN_SCREEN, frame(1280, 720))
        output.pass(0)
        assertEquals(AreaRect(50, 10, 590, 350), sink.published[0].safe)
        assertEquals(AreaRect(100, 20, 1180, 700), sink.published[1].safe)
        assertEquals(640, sink.published[0].width)
        assertEquals(360, sink.published[0].height)
    }

    @Test fun `the latest frame wins and never overwrites the one being published`() {
        val sink = FakeSink()
        val output = output(sink)
        output.enabled = true
        // Two frames before the worker gets to them: only the second is published.
        output.frame(MAIN_SCREEN, frame(luma = 16))
        output.frame(MAIN_SCREEN, frame(luma = 235))
        output.pass(0)
        assertEquals(1, sink.count("publish"))
        val white = sink.published[0].firstPixel
        // The decoder delivers twice while the worker publishes: neither waits, nor touches the buffer in use.
        output.frame(MAIN_SCREEN, frame(luma = 16))
        sink.onPublish = { bgra ->
            sink.onPublish = {}
            output.frame(MAIN_SCREEN, frame(luma = 235))
            output.frame(MAIN_SCREEN, frame(luma = 126))
            assertEquals(BLACK, bgra.getInt(0))
        }
        output.pass(0)
        output.pass(0)
        assertEquals(3, sink.count("publish"))
        assertEquals(BLACK, sink.published[1].firstPixel)
        val grey = sink.published[2].firstPixel
        assertTrue(grey != BLACK && grey != white)
        output.pass(0)
        assertEquals(3, sink.count("publish"))
    }

    @Test fun `the worker thread publishes and stops`() {
        val published = CountDownLatch(1)
        val sink = FakeSink().apply { onPublish = { published.countDown() } }
        var thread: Thread? = null
        val output = FrameOutput(sink, { WindowSize(64, 64) }, { AreaRect(0, 0, 64, 64) }, logs::add, System::nanoTime,
            worker = { FrameOutput.daemon(it).also { started -> thread = started } })
        output.enabled = true
        output.frame(MAIN_SCREEN, frame())
        assertTrue(published.await(10, TimeUnit.SECONDS))
        output.enabled = false
        assertFalse(thread!!.isAlive)
        assertEquals("close", sink.events.last())
    }

    private companion object {
        const val MAIN_SCREEN = 110
        const val ALT_SCREEN = 111
        /** Limited-range black as BGRA bytes 0, 0, 0, 255, read as a big-endian int. */
        const val BLACK = 0x000000FF
    }
}
