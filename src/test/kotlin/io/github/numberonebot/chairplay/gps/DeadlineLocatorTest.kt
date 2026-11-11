package io.github.numberonebot.chairplay.gps

import io.github.numberonebot.chairplay.drive.GeoPoint
import io.github.numberonebot.chairplay.platform.LocationResult
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.concurrent.CountDownLatch
import java.util.concurrent.atomic.AtomicInteger

class DeadlineLocatorTest {
    // V47: a slow Windows read gives way at the deadline; asks meanwhile share the one read; the next asks anew.
    @Test
    fun `a slow Windows read times out, one read at a time, and the next asks anew`() {
        val reads = AtomicInteger()
        val answer = CountDownLatch(1)
        val locator = DeadlineLocator {
            reads.incrementAndGet()
            answer.await()
            LocationResult.Found(GeoPoint(49.84, 24.03))
        }

        assertEquals(ReturnPoint.Unavailable("timeout"), locator.locate(50))
        assertEquals(ReturnPoint.Unavailable("timeout"), locator.locate(50))
        assertEquals(1, reads.get())
        answer.countDown()
        Thread.sleep(100)
        assertTrue(locator.locate(1_000) is ReturnPoint.Found)
        assertEquals(2, reads.get())
    }

    @Test
    fun `denied reads as denied, and the log says why there is no place`() {
        val log = mutableListOf<String>()
        assertEquals(ReturnPoint.Unavailable("denied"), DeadlineLocator(log::add) { LocationResult.Denied }.locate(1_000))
        assertTrue(log.single().contains("denied"))
    }
}
