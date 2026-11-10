package io.github.numberonebot.chairplay.gps

import io.github.numberonebot.chairplay.drive.GeoPoint
import io.github.numberonebot.chairplay.platform.LocationResult
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.nio.file.Files
import java.util.concurrent.CountDownLatch
import java.util.concurrent.atomic.AtomicInteger

class GpsJournalAndLocatorTest {
    private val dir = Files.createTempDirectory("chairplay-gps")

    // V49: the record outlives the run that wrote it, and goes with the clear.
    @Test
    fun `the journal survives a restart and goes with the clear`() {
        val file = FileCleanupJournal.at(dir)
        FileCleanupJournal(file).mark("00008110-000A")

        val next = FileCleanupJournal(file)
        assertEquals("00008110-000A", next.dirty())
        next.clear()
        assertFalse(Files.exists(file))
        assertNull(FileCleanupJournal(file).dirty())
    }

    @Test
    fun `a damaged journal reads as none`() {
        val file = FileCleanupJournal.at(dir)
        Files.createDirectories(file.parent)
        Files.writeString(file, "{not json")

        assertNull(FileCleanupJournal(file).dirty())
    }

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
    fun `denied reads as denied`() {
        assertEquals(ReturnPoint.Unavailable("denied"), DeadlineLocator { LocationResult.Denied }.locate(1_000))
    }
}
