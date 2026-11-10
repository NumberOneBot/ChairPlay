package io.github.numberonebot.chairplay.drive

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.hypot

/** The British coordinates' box, cut by its well's circle. */
class GaugeInsetsTest {
    // A box from inside a circle out past it: nothing of it outside the circle, what was inside as it was, and the cut
    // following the circle from the top edge round to the bottom one.
    @Test
    fun `a box reaching out of a circle is cut by it`() {
        val outline = roundedRectInCircle(0.5f, -0.2f, 0.7f, 0.4f, 0.05f, 0f, 0f, 1f)
        assertTrue(outline.all { (x, y) -> hypot(x, y) <= 1f + 1e-4f })
        assertTrue(outline.any { (x, y) -> Math.abs(hypot(x, y) - 1f) < 1e-4f && Math.abs(y) < 0.01f })
        // The left edge, well inside the circle, is where the box's is.
        assertTrue(outline.any { (x, y) -> x == 0.5f && Math.abs(y) < 0.01f })
        assertEquals(0.5f, outline.minOf { it.first }, 1e-6f)
        assertEquals(1f, outline.maxOf { it.first }, 1e-4f)
    }

    @Test
    fun `a box inside a circle keeps its outline`() {
        val outline = roundedRectInCircle(-0.2f, -0.1f, 0.4f, 0.2f, 0.02f, 0f, 0f, 1f)
        assertEquals(-0.2f, outline.minOf { it.first }, 1e-6f)
        assertEquals(0.2f, outline.maxOf { it.first }, 1e-6f)
        assertEquals(-0.1f, outline.minOf { it.second }, 1e-6f)
        assertEquals(0.1f, outline.maxOf { it.second }, 1e-6f)
    }
}
