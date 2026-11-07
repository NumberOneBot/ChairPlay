package io.github.numberonebot.chairplay.bridge

import io.github.numberonebot.chairplay.config.AreaRect
import io.github.numberonebot.chairplay.config.WindowSize
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

class FrameOutputTest {
    private var now = 0L
    private val logs = mutableListOf<String>()
    private var workers = 0

    /** The worker loop never runs on its own: each test drives one pass at a time with [FrameOutput.pass]. */
    private fun output(
        sink: FrameSink,
        screen: WindowSize = WindowSize(64, 64),
        safe: AreaRect = AreaRect(0, 0, 64, 64),
    ) = FrameOutput(sink, { screen }, { safe }, logs::add, { now }, worker = { workers++; Thread {} })

    @Test fun `publishes main screen frames only`() {
        val sink = FakeFrameSink()
        val output = output(sink)
        output.enabled = true
        output.frame(ALT_SCREEN, testFrame())
        output.pass(0)
        assertEquals(0, sink.count("publish"))
        output.frame(MAIN_SCREEN, testFrame())
        output.pass(0)
        assertEquals(1, sink.count("publish"))
        assertEquals(2, sink.state)
    }

    @Test fun `sharing on over an active stream asks for a picture once`() {
        var asked = 0
        val output = output(FakeFrameSink())
        output.attach { asked++ }
        output.active(MAIN_SCREEN, true)
        output.enabled = true
        output.enabled = true
        assertEquals(1, asked)
    }

    @Test fun `sharing on with no stream asks for nothing`() {
        var asked = 0
        val output = output(FakeFrameSink())
        output.attach { asked++ }
        output.enabled = true
        output.active(MAIN_SCREEN, true)
        assertEquals(0, asked)
    }

    @Test fun `a stopped stream asks for nothing`() {
        var asked = 0
        val output = output(FakeFrameSink())
        output.attach { asked++ }
        output.active(MAIN_SCREEN, true)
        output.active(MAIN_SCREEN, false)
        output.enabled = true
        assertEquals(0, asked)
    }

    @Test fun `sharing that cannot open asks for nothing`() {
        var asked = 0
        val output = output(FakeFrameSink(openError = "no"))
        output.attach { asked++ }
        output.active(MAIN_SCREEN, true)
        output.enabled = true
        assertEquals(0, asked)
    }

    @Test fun `stream stop sets waiting`() {
        val sink = FakeFrameSink()
        val output = output(sink)
        output.enabled = true
        output.active(MAIN_SCREEN, true)
        output.frame(MAIN_SCREEN, testFrame())
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
        val sink = FakeFrameSink()
        val output = output(sink)
        output.enabled = true
        output.active(MAIN_SCREEN, true)
        output.frame(MAIN_SCREEN, testFrame())
        output.pass(0)
        now += 900_000_000L
        output.pass(0)
        assertEquals(2, sink.state)
        now += 200_000_000L
        output.pass(0)
        assertEquals(3, sink.state)
        output.pass(0)
        assertEquals(1, sink.count("state 3"))
        output.frame(MAIN_SCREEN, testFrame())
        output.pass(0)
        assertEquals(2, sink.count("publish"))
        assertEquals(2, sink.state)
    }

    @Test fun `turning off closes the native side`() {
        val sink = FakeFrameSink()
        val output = output(sink)
        output.enabled = true
        output.frame(MAIN_SCREEN, testFrame())
        output.pass(0)
        output.enabled = false
        output.enabled = false
        assertEquals(1, sink.count("close"))
        output.frame(MAIN_SCREEN, testFrame())
        assertFalse(output.pass(0))
        assertEquals(1, sink.count("publish"))
    }

    @Test fun `nothing opens while off`() {
        val sink = FakeFrameSink()
        val output = output(sink)
        output.active(MAIN_SCREEN, true)
        repeat(3) { output.frame(MAIN_SCREEN, testFrame()) }
        output.active(MAIN_SCREEN, false)
        output.close()
        assertEquals(emptyList<String>(), sink.events)
        assertEquals(0, workers)
    }

    @Test fun `the safe rect follows the frame size`() {
        val sink = FakeFrameSink()
        val output = output(sink, WindowSize(1280, 720), AreaRect(100, 20, 1180, 700))
        output.enabled = true
        output.frame(MAIN_SCREEN, testFrame(640, 360))
        output.pass(0)
        output.frame(MAIN_SCREEN, testFrame(1280, 720))
        output.pass(0)
        assertEquals(AreaRect(50, 10, 590, 350), sink.published[0].safe)
        assertEquals(AreaRect(100, 20, 1180, 700), sink.published[1].safe)
        assertEquals(640, sink.published[0].width)
        assertEquals(360, sink.published[0].height)
    }

    @Test fun `the latest frame wins and never overwrites the one being published`() {
        val sink = FakeFrameSink()
        val output = output(sink)
        output.enabled = true
        // Two frames before the worker gets to them: only the second is published.
        output.frame(MAIN_SCREEN, testFrame(luma = 16))
        output.frame(MAIN_SCREEN, testFrame(luma = 235))
        output.pass(0)
        assertEquals(1, sink.count("publish"))
        val white = sink.published[0].firstPixel
        // The decoder delivers twice while the worker publishes: neither waits, nor touches the buffer in use.
        output.frame(MAIN_SCREEN, testFrame(luma = 16))
        sink.onPublish = { bgra ->
            sink.onPublish = {}
            output.frame(MAIN_SCREEN, testFrame(luma = 235))
            output.frame(MAIN_SCREEN, testFrame(luma = 126))
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
        val sink = FakeFrameSink().apply { onPublish = { published.countDown() } }
        var thread: Thread? = null
        val output = FrameOutput(sink, { WindowSize(64, 64) }, { AreaRect(0, 0, 64, 64) }, logs::add, System::nanoTime,
            worker = { FrameOutput.daemon(it).also { started -> thread = started } })
        output.enabled = true
        output.frame(MAIN_SCREEN, testFrame())
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
