package io.github.numberonebot.chairplay.input

import io.github.numberonebot.chairplay.phone.TouchPoint
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class TouchMapperTest {
    private val bars = Size(1600, 720)
    private val video = Size(1280, 720)

    @Test
    fun `centre maps to centre`() {
        val contact = mapTouch(PointerEvent(640.0, 360.0, true), Size(1280, 720), video)!!
        assertEquals(0.5, contact.x, 1e-9)
        assertEquals(0.5, contact.y, 1e-9)
    }

    @Test
    fun `letterbox bars ignored`() {
        assertNull(mapTouch(PointerEvent(100.0, 360.0, true), bars, video))
        assertNull(mapTouch(PointerEvent(1500.0, 360.0, true), bars, video))
    }

    @Test
    fun `letterbox offset removed`() {
        val contact = mapTouch(PointerEvent(160.0, 0.0, true), bars, video)!!
        assertEquals(0.0, contact.x, 1e-9)
        assertEquals(0.0, contact.y, 1e-9)
    }

    @Test
    fun `release keeps down false and id 0`() {
        val contact = mapTouch(PointerEvent(800.0, 360.0, false), bars, video)!!
        assertEquals(TouchPoint(0, 0.5, 0.5, false), contact)
    }

    @Test
    fun `bars above and below are ignored`() {
        val tall = Size(1280, 1000)
        assertNull(mapTouch(PointerEvent(640.0, 100.0, true), tall, video))
        assertEquals(0.0, mapTouch(PointerEvent(0.0, 140.0, true), tall, video)!!.y, 1e-9)
    }

    @Test
    fun `clamped mapping pins an outside point to the edge`() {
        val contact = mapTouch(PointerEvent(50.0, 800.0, true), bars, video, clamp = true)!!
        assertEquals(0.0, contact.x, 1e-9)
        assertEquals(1.0, contact.y, 1e-9)
    }

    @Test
    fun `scaled display gives the same point as an unscaled one`() {
        // Window 1280x720 drawn into a 2560x1440 framebuffer: the cursor is in window units.
        val scaled = PointerEvent(320.0, 90.0, true).toFramebuffer(Size(1280, 720), Size(2560, 1440))
        val viaFramebuffer = mapTouch(scaled, Size(2560, 1440), video)!!
        val direct = mapTouch(PointerEvent(320.0, 90.0, true), Size(1280, 720), video)!!
        assertEquals(direct.x, viaFramebuffer.x, 1e-9)
        assertEquals(direct.y, viaFramebuffer.y, 1e-9)
        assertEquals(0.25, viaFramebuffer.x, 1e-9)
    }

    @Test
    fun `scaled display with letterbox bars`() {
        val fb = Size(3200, 1440)
        val p = PointerEvent(800.0, 360.0, true).toFramebuffer(Size(1600, 720), fb)
        assertEquals(0.5, mapTouch(p, fb, video)!!.x, 1e-9)
        assertNull(mapTouch(PointerEvent(100.0, 360.0, true).toFramebuffer(Size(1600, 720), fb), fb, video))
    }

    @Test
    fun `press move release sends a drag and ignores hover`() {
        val tracker = TouchTracker()
        assertNull(tracker.handle(PointerEvent(800.0, 360.0, false), bars, video))
        assertEquals(TouchPoint(0, 0.5, 0.5, true), tracker.handle(PointerEvent(800.0, 360.0, true), bars, video))
        assertEquals(TouchPoint(0, 0.75, 0.5, true), tracker.handle(PointerEvent(1120.0, 360.0, true), bars, video))
        assertEquals(TouchPoint(0, 0.75, 0.5, false), tracker.handle(PointerEvent(1120.0, 360.0, false), bars, video))
        assertNull(tracker.handle(PointerEvent(900.0, 360.0, false), bars, video))
    }

    @Test
    fun `press on the bars sends nothing for the whole gesture`() {
        val tracker = TouchTracker()
        assertNull(tracker.handle(PointerEvent(100.0, 360.0, true), bars, video))
        assertNull(tracker.handle(PointerEvent(800.0, 360.0, true), bars, video))
        assertNull(tracker.handle(PointerEvent(800.0, 360.0, false), bars, video))
    }

    @Test
    fun `drag leaving the video clamps to the edge and release stays there`() {
        val tracker = TouchTracker()
        tracker.handle(PointerEvent(800.0, 360.0, true), bars, video)
        assertEquals(TouchPoint(0, 1.0, 0.5, true), tracker.handle(PointerEvent(1590.0, 360.0, true), bars, video))
        assertEquals(TouchPoint(0, 1.0, 0.5, false), tracker.handle(PointerEvent(1590.0, 360.0, false), bars, video))
    }

    @Test
    fun `no video yet means no touch`() {
        assertNull(mapTouch(PointerEvent(10.0, 10.0, true), Size(1280, 720), Size(0, 0)))
    }

    @Test
    fun `session ending mid-drag does not leak into the next tap`() {
        val tracker = TouchTracker()
        tracker.handle(PointerEvent(800.0, 360.0, true), bars, video)
        tracker.handle(PointerEvent(1000.0, 360.0, true), bars, video)
        tracker.reset()
        assertNull(tracker.handle(PointerEvent(1000.0, 360.0, false), bars, video))
        assertEquals(TouchPoint(0, 0.5, 0.5, true), tracker.handle(PointerEvent(800.0, 360.0, true), bars, video))
        assertEquals(TouchPoint(0, 0.5, 0.5, false), tracker.handle(PointerEvent(800.0, 360.0, false), bars, video))
    }

    @Test
    fun `press on bars then session change then tap on picture is sent`() {
        val tracker = TouchTracker()
        assertNull(tracker.handle(PointerEvent(100.0, 360.0, true), bars, video))
        tracker.reset()
        assertNull(tracker.handle(PointerEvent(100.0, 360.0, false), bars, video))
        assertEquals(TouchPoint(0, 0.5, 0.5, true), tracker.handle(PointerEvent(800.0, 360.0, true), bars, video))
    }

    @Test
    fun `button held at session start sends nothing until the next press`() {
        val tracker = TouchTracker()
        tracker.handle(PointerEvent(800.0, 360.0, true), bars, video)
        tracker.reset()
        assertNull(tracker.handle(PointerEvent(900.0, 360.0, true), bars, video))
        assertNull(tracker.handle(PointerEvent(900.0, 360.0, false), bars, video))
        assertEquals(true, tracker.handle(PointerEvent(900.0, 360.0, true), bars, video)!!.down)
    }

    @Test
    fun `queue keeps press, the latest move and release in order`() {
        val queue = TouchQueue()
        queue.offer(TouchPoint(0, 0.1, 0.1, true))
        for (i in 1..1000) queue.offer(TouchPoint(0, 0.1 + i / 10000.0, 0.1, true))
        queue.offer(TouchPoint(0, 0.2, 0.1, false))
        assertEquals(TouchPoint(0, 0.1, 0.1, true), queue.poll())
        assertEquals(0.2, queue.poll()!!.x, 1e-9)
        assertEquals(TouchPoint(0, 0.2, 0.1, false), queue.poll())
        assertNull(queue.poll())
    }

    @Test
    fun `queue never merges a press into a previous release or move`() {
        val queue = TouchQueue()
        queue.offer(TouchPoint(0, 0.1, 0.1, true))
        queue.offer(TouchPoint(0, 0.1, 0.1, false))
        queue.offer(TouchPoint(0, 0.3, 0.3, true))
        assertEquals(listOf(true, false, true), generateSequence { queue.poll() }.map { it.down }.toList())
    }
}
