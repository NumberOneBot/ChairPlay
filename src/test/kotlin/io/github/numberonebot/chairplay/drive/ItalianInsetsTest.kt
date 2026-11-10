package io.github.numberonebot.chairplay.drive

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** The Italian tachometer's corner cut for the coordinates. */
class ItalianInsetsTest {
    // The cut for the coordinates is as wide as it is deep, from its straight edges out to its circle, which stays on
    // the grey face, short of the knurled ring at the face's edge.
    @Test
    fun `the corner cut is as wide as deep, inside the face`() {
        val (width, depth) = italianSegmentSize()
        assertEquals(width, depth, 1e-6f)
        assertTrue(width > 0.5f)
        assertTrue(ITALIAN_SEGMENT_RADIUS < 1f)
    }

    // Its outline: right of and below its straight edges, inside its circle, and rounded where the edges meet the
    // circle, so no point of it lies in the sharp corners a plain cut would leave there.
    @Test
    fun `the corner cut's outline is rounded where its edges meet its circle`() {
        val outline = italianSegmentOutline()
        assertTrue(outline.size > 20)
        for ((x, y) in outline) {
            assertTrue("$x, $y", x >= ITALIAN_SEGMENT_X - 1e-4f && y >= ITALIAN_SEGMENT_Y - 1e-4f)
            assertTrue("$x, $y", Math.hypot(x.toDouble(), y.toDouble()) <= ITALIAN_SEGMENT_RADIUS + 1e-4)
        }
        // A plain cut's corner at the top edge: where that edge meets the circle. The rounded one stays clear of it.
        val sharp = Math.sqrt((ITALIAN_SEGMENT_RADIUS * ITALIAN_SEGMENT_RADIUS - ITALIAN_SEGMENT_Y * ITALIAN_SEGMENT_Y).toDouble())
        val nearest = outline.minOf { (x, y) -> Math.hypot(x - sharp, (y - ITALIAN_SEGMENT_Y).toDouble()) }
        assertTrue("$nearest", nearest > 0.01)
    }
}
