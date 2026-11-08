package io.github.numberonebot.chairplay.bridge

import io.github.numberonebot.chairplay.config.AreaRect
import io.github.numberonebot.chairplay.config.WindowSize
import io.github.numberonebot.chairplay.media.DecodedFrame
import io.github.numberonebot.chairplay.phone.ScreenKind
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Test
import java.nio.ByteBuffer

/** How a [FrameOutput] fails: logged once, the native side let go, and nothing thrown at the decoder or the worker. */
class FrameOutputFailureTest {
    private val logs = mutableListOf<String>()
    private var workers = 0

    private fun output(sink: FrameSink, converters: () -> PictureConverter = ::BgraConverter) =
        FrameOutput(sink, { WindowSize(64, 64) }, { AreaRect(0, 0, 64, 64) }, logs::add, { 0L },
            worker = { workers++; Thread {} }, converters = converters)

    /** Throws [error] from every conversion, as a direct buffer or a native allocation out of memory does. */
    private class ThrowingConverter(private val error: Throwable) : PictureConverter {
        override fun convert(frame: DecodedFrame, out: ByteBuffer): ByteBuffer = throw error
        override fun close() {}
    }

    @Test fun `a failing native side is logged once and stops publishing`() {
        val sink = FakeFrameSink(publishError = "E_FAIL")
        val output = output(sink)
        output.enabled = true
        repeat(3) {
            output.frame(MAIN_SCREEN, testFrame())
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
        output.frame(MAIN_SCREEN, testFrame())
        output.pass(0)
        assertEquals(2, sink.count("open"))
        assertEquals(2, sink.count("publish"))
    }

    @Test fun `a native side that cannot open is logged and starts nothing`() {
        val sink = FakeFrameSink(openError = "no D3D11 device")
        val output = output(sink)
        output.enabled = true
        output.frame(MAIN_SCREEN, testFrame())
        assertFalse(output.pass(0))
        assertEquals(listOf("open"), sink.events)
        assertEquals(1, logs.count { "no D3D11 device" in it })
        assertEquals(0, workers)
    }

    // The decoder's thread goes on to show the picture; the worker lets the native side go (state 0 for a game).
    @Test fun `an error converting a picture is logged once and lets the native side go`() {
        val sink = FakeFrameSink()
        val output = output(sink) { ThrowingConverter(OutOfMemoryError("Direct buffer memory")) }
        output.enabled = true
        repeat(2) { output.frame(MAIN_SCREEN, testFrame()) }
        assertFalse(output.pass(0))
        assertEquals(0, sink.count("publish"))
        assertEquals(1, sink.count("close"))
        assertEquals(0, sink.state)
        assertEquals(1, logs.count { "Direct buffer memory" in it })
        output.frame(MAIN_SCREEN, testFrame())
        assertEquals(1, logs.count { "Direct buffer memory" in it })
    }

    // An error on the worker would end its thread with the status still saying a picture flows.
    @Test fun `an error publishing is logged once and lets the native side go`() {
        val sink = FakeFrameSink(publishThrows = OutOfMemoryError("Java heap space"))
        val output = output(sink)
        output.enabled = true
        output.frame(MAIN_SCREEN, testFrame())
        assertFalse(output.pass(0))
        assertEquals(1, sink.count("close"))
        assertEquals(0, sink.state)
        assertEquals(1, logs.count { "Java heap space" in it })
        output.frame(MAIN_SCREEN, testFrame())
        assertFalse(output.pass(0))
        assertEquals(1, sink.count("publish"))
    }

    private companion object {
        val MAIN_SCREEN = ScreenKind.MAIN
    }
}
