package io.github.numberonebot.chairplay.gps.usb

import io.github.numberonebot.chairplay.drive.GeoPoint
import io.github.numberonebot.chairplay.gps.UsbErrorCode
import io.github.numberonebot.chairplay.gps.UsbResult
import io.github.numberonebot.chairplay.gps.UsbSession
import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ProcessUsbSessionTest {
    private val udid = "00008030-001A2D3E0C38802E"
    private val launcher = FakeLauncher()
    private val timeouts = HelperTimeouts(helloMillis = 2_000, setMillis = 600, priorityMillis = 1_000, closeMillis = 300,
        exitGraceMillis = 200, heartbeatMillis = 150)
    private val helper = ProcessUsbHelper(launcher, timeouts = timeouts)
    private val held = CopyOnWriteArrayList<Map<String, Any?>>()
    private val lost = CopyOnWriteArrayList<Pair<UsbErrorCode, String>>()

    @After
    fun close() = helper.close()

    private fun open(): Pair<UsbSession, FakeHelper> {
        val session = (helper.openSession(udid) as UsbResult.Ok).value
        session.onLost = { lost += it to Thread.currentThread().name }
        return session to launcher.of(HelperRole.SESSION).last()
    }

    /** Sets are held unanswered until the test replies; the rest as usual. */
    private fun holdSets() {
        launcher.session = { if (it["op"] == "set") held += it else sessionStandard(it) }
    }

    private fun FakeHelper.sets() = requests.filter { it["op"] == "set" }.map { it["latitude"] }

    @Test
    fun `offers never wait, and only the newest goes out after a slow set`() {
        holdSets()
        val (session, fake) = open()
        session.offer(GeoPoint(1.0, 0.0))
        eventually { held.size == 1 }
        val started = System.nanoTime()
        for (index in 2..50) session.offer(GeoPoint(index.toDouble(), 0.0))
        assertTrue(System.nanoTime() - started < TimeUnit.MILLISECONDS.toNanos(100))
        Thread.sleep(50)
        assertEquals(1, held.size)
        fake.reply(held[0], mapOf("applied" to true, "acknowledged" to 1))
        eventually { held.size == 2 }
        assertEquals(listOf(1.0, 50.0), fake.sets())
        fake.reply(held[1], mapOf("applied" to true, "acknowledged" to 2))
        eventually { session.acknowledged == 2L }
    }

    @Test
    fun `every request names the session and its generation`() {
        val (session, fake) = open()
        session.offer(GeoPoint(1.0, 2.0))
        eventually { fake.sets().isNotEmpty() }
        assertEquals(UsbResult.Ok(Unit), session.clear())
        val open = fake.requests.first { it["op"] == "open" }
        fake.requests.filter { it["op"] in setOf("set", "clear") }.forEach {
            assertEquals(listOf(session.id, open["generation"]), listOf(it["session"], it["generation"]))
        }
    }

    @Test
    fun `return goes after the set in flight, and later offers stay unsent`() {
        holdSets()
        val (session, fake) = open()
        session.offer(GeoPoint(1.0, 0.0))
        eventually { held.size == 1 }
        session.offer(GeoPoint(2.0, 0.0))
        var result: UsbResult<Unit>? = null
        val returning = Thread { result = session.returnTo(GeoPoint(50.4, 30.2)) }.apply { start() }
        eventually { fake.ops().contains("return") }
        fake.reply(held[0], mapOf("applied" to true, "acknowledged" to 1))
        returning.join(2_000)
        assertEquals(UsbResult.Ok(Unit), result)
        session.offer(GeoPoint(3.0, 0.0))
        Thread.sleep(100)
        assertEquals(listOf(1.0), fake.sets())
        assertEquals(listOf("hello", "open", "set", "return"), fake.ops().filter { it != "ping" })
    }

    @Test
    fun `clear drops the point that waited`() {
        holdSets()
        val (session, fake) = open()
        session.offer(GeoPoint(1.0, 0.0))
        eventually { held.size == 1 }
        session.offer(GeoPoint(2.0, 0.0))
        val clearing = Thread { session.clear() }.apply { start() }
        eventually { fake.ops().contains("clear") }
        fake.reply(held[0], mapOf("applied" to true, "acknowledged" to 1))
        clearing.join(2_000)
        Thread.sleep(100)
        assertEquals(listOf(1.0), fake.sets())
    }

    @Test
    fun `a lost phone is told once, off the reader thread, and the helper is ended`() {
        val (session, fake) = open()
        val generation = fake.requests.first { it["op"] == "open" }["generation"]
        fake.onInputEnd = {}
        repeat(2) { fake.event("lost", mapOf("session" to session.id, "generation" to generation, "code" to "link-lost")) }
        eventually { lost.isNotEmpty() }
        eventually { !fake.alive() }
        assertEquals(listOf(UsbErrorCode.LINK_LOST), lost.map { it.first })
        assertTrue(!lost[0].second.endsWith("-reader"))
        assertEquals(UsbErrorCode.LINK_LOST, (session.clear() as UsbResult.Failed).code)
    }

    @Test
    fun `a lost event of another session is ignored`() {
        val (session, fake) = open()
        fake.event("lost", mapOf("session" to "old", "generation" to 0, "code" to "link-lost"))
        Thread.sleep(100)
        assertTrue(lost.isEmpty())
        session.close()
    }

    @Test
    fun `a helper that dies is a lost session, but not after close`() {
        val (_, fake) = open()
        fake.exit()
        eventually { lost.size == 1 }
        assertEquals(UsbErrorCode.HELPER_FAILED, lost[0].first)
        val (other, otherFake) = open()
        other.close()
        otherFake.exit()
        Thread.sleep(100)
        assertEquals(1, lost.size)
    }

    @Test
    fun `a set with no reply means a hung helper`() {
        holdSets()
        val (session, fake) = open()
        session.offer(GeoPoint(1.0, 0.0))
        eventually { lost.isNotEmpty() }
        assertEquals(UsbErrorCode.TIMEOUT, lost[0].first)
        eventually { !fake.alive() }
    }

    @Test
    fun `close asks the helper to clean up, ends it, and is bounded when it stays silent`() {
        val (session, fake) = open()
        session.close()
        assertTrue(fake.ops().contains("close"))
        assertTrue(!fake.alive())
        launcher.session = { if (it["op"] != "close") sessionStandard(it) }
        val (silent, silentFake) = open()
        silentFake.onInputEnd = {}
        val started = System.nanoTime()
        silent.close()
        assertTrue(System.nanoTime() - started < TimeUnit.SECONDS.toNanos(2))
        assertTrue(silentFake.killed)
        silent.close()
    }

    @Test
    fun `an idle session pings so the helper's watchdog stays quiet`() {
        val (session, fake) = open()
        eventually { fake.ops().count { it == "ping" } >= 2 }
        session.close()
    }

    @Test
    fun `invalid points never leave the app`() {
        val (session, fake) = open()
        session.offer(GeoPoint(Double.NaN, 0.0))
        session.offer(GeoPoint(91.0, 0.0))
        Thread.sleep(100)
        assertTrue(fake.sets().isEmpty())
        assertEquals(UsbErrorCode.INVALID_COORDINATES, (session.returnTo(GeoPoint(0.0, 181.0)) as UsbResult.Failed).code)
        val counter = AtomicInteger()
        session.onLost = { counter.incrementAndGet() }
        session.close()
        assertEquals(0, counter.get())
    }
}
