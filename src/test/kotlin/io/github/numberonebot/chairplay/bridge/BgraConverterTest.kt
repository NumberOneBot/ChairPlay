package io.github.numberonebot.chairplay.bridge

import io.github.numberonebot.chairplay.media.DecodedFrame
import io.github.numberonebot.chairplay.media.FramePool
import org.junit.Test
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue

class BgraConverterTest {
    private fun solid(width: Int, height: Int, y: Int, u: Int, v: Int): DecodedFrame {
        val frame = FramePool().take(width, height)
        for (i in 0 until frame.y.capacity()) frame.y.put(i, y.toByte())
        for (i in 0 until frame.u.capacity()) frame.u.put(i, u.toByte())
        for (i in 0 until frame.v.capacity()) frame.v.put(i, v.toByte())
        return frame
    }

    private fun first(converter: BgraConverter, frame: DecodedFrame): IntArray {
        val out = converter.convert(frame, converter.buffer(frame.width, frame.height))
        return IntArray(4) { out.get(it).toInt() and 0xFF }
    }

    @Test
    fun `limited black and white become full range`() {
        BgraConverter().use { converter ->
            val black = first(converter, solid(64, 64, 16, 128, 128))
            assertEquals(listOf(0, 0, 0, 255), black.toList())
            val white = first(converter, solid(64, 64, 235, 128, 128))
            for (c in 0..2) assertTrue("channel $c was ${white[c]}", white[c] in 253..255)
            assertEquals(255, white[3])
        }
    }

    @Test
    fun `BT 709 red`() {
        BgraConverter().use { converter ->
            val px = first(converter, solid(64, 64, 63, 102, 240))
            assertTrue("R was ${px[2]}", px[2] >= 250)
            assertTrue("G was ${px[1]}", px[1] <= 5)
            assertTrue("B was ${px[0]}", px[0] <= 5)
        }
    }

    @Test
    fun `odd sizes convert`() {
        BgraConverter().use { converter ->
            val out = converter.convert(solid(801, 481, 16, 128, 128), converter.buffer(801, 481))
            assertEquals(801 * 481 * 4, out.capacity())
            assertTrue(out.isDirect)
        }
    }

    @Test
    fun `buffer is reused while the size holds`() {
        BgraConverter().use { converter ->
            val a = converter.buffer(64, 32)
            assertTrue(a === converter.buffer(64, 32))
            assertEquals(64 * 32 * 4, converter.buffer(64, 32).capacity())
            assertEquals(16 * 16 * 4, converter.buffer(16, 16).capacity())
        }
    }
}
