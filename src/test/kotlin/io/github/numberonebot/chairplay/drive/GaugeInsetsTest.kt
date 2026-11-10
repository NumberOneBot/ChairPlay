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

    // The compass arrow turns in place about its middle: it is set in once, by the farthest it reaches whichever way it
    // points (its tail's corners, and its outline's half stroke), in its size.
    @Test
    fun `the arrow is set in by the farthest it reaches`() {
        assertEquals((Math.hypot(4.5, 7.4).toFloat() + 0.65f) / 20, arrowReach(), 1e-5f)
    }

    // The cut's own edge, on either side of the circle: one unbroken run of the outline's points on the circle, from one
    // end of the cut to the other, and nothing of the box's own sides.
    @Test
    fun `the cut's edge runs along the circle from end to end`() {
        for (x in listOf(0.5f, -1.2f)) {
            val outline = roundedRectInCircle(x, -0.2f, 0.7f, 0.4f, 0.05f, 0f, 0f, 1f)
            val edge = circleEdge(outline, 0f, 0f, 1f)
            val onCircle = outline.count { (px, py) -> Math.abs(hypot(px, py) - 1f) < 1e-4f }
            assertEquals("$x", onCircle, edge.size)
            assertTrue("$x", edge.all { (px, py) -> Math.abs(hypot(px, py) - 1f) < 1e-4f })
            // Neighbours along the run, never a jump across the box.
            assertTrue("$x", edge.zipWithNext().all { (a, b) -> hypot(a.first - b.first, a.second - b.second) < 0.05f })
            // Its ends at the box's top and bottom, within the outline's step along a side.
            assertEquals("$x", 0.2f, Math.abs(edge.first().second), 5e-3f)
            assertEquals("$x", 0.2f, Math.abs(edge.last().second), 5e-3f)
            assertTrue("$x", edge.first().second * edge.last().second < 0)
        }
        assertTrue(circleEdge(roundedRectInCircle(-0.2f, -0.1f, 0.4f, 0.2f, 0.02f, 0f, 0f, 1f), 0f, 0f, 1f).isEmpty())
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
