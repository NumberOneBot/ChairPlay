package io.github.numberonebot.chairplay.ui

import io.github.numberonebot.chairplay.ui.SettingsLock.Companion.CONTROLS
import io.github.numberonebot.chairplay.ui.SettingsLock.Companion.SHARED
import io.github.numberonebot.chairplay.ui.SettingsLock.Companion.WINDOW
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class SettingsLockTest {
    private var shows = true
    private var now = 0L
    private var changes = 0
    private val lock = SettingsLock({ shows }, { changes++ }, { now })

    init {
        lock.update()
    }

    private fun holder(): Any? = lock.view(WINDOW)["holder"]

    /** CarPlay covers the window (or it waits), so a tab may take the settings. */
    private fun cover() {
        shows = false
        lock.update()
    }

    private fun uncover() {
        shows = true
        lock.update()
    }

    @Test
    fun `the window holds the settings while its page shows`() {
        assertTrue(lock.mayWrite(WINDOW))
        assertEquals(true, lock.view(WINDOW)["mine"])
        assertEquals("window", holder())
        cover()
        assertFalse(lock.mayWrite(WINDOW))
        assertNull(holder())
    }

    // Review Focus 2: the window holds by the app's state, never by a stream of its own.
    @Test
    fun `the window holds them without any stream of its own`() {
        val stream = lock.open(WINDOW, true)
        stream.close()
        lock.update()
        assertTrue(lock.mayWrite(WINDOW))
        assertFalse(lock.tabHolds)
    }

    @Test
    fun `a tab waits while the window holds them, and takes them once CarPlay covers the window`() {
        lock.open("a", true)
        assertEquals(mapOf("mine" to false, "holder" to "window", "shared" to false, "handOver" to false), lock.view("a"))
        assertFalse(lock.mayWrite("a"))
        cover()
        assertTrue(lock.mayWrite("a"))
        assertFalse(lock.mayWrite(WINDOW))
        assertTrue(lock.tabHolds)
        assertEquals(true, lock.view("a")["mine"])
    }

    @Test
    fun `the tab keeps them when the window shows again`() {
        lock.open("a", true)
        cover()
        uncover()
        assertTrue(lock.mayWrite("a"))
        assertFalse(lock.mayWrite(WINDOW))
        assertEquals("tab", holder())
        assertFalse(lock.carButtonShowsSettings(true))
        lock.report("a", false)
        assertTrue(lock.carButtonShowsSettings(true))
        assertFalse(lock.carButtonShowsSettings(false))
    }

    @Test
    fun `a hidden tab lets go, and takes them back when shown if they are free`() {
        lock.open("a", true)
        cover()
        uncover()
        lock.report("a", false)
        assertTrue(lock.mayWrite(WINDOW))
        assertFalse(lock.mayWrite("a"))
        lock.report("a", true)
        assertFalse("the window holds them now", lock.mayWrite("a"))
        cover()
        lock.report("a", false)
        assertNull(holder())
        lock.report("a", true)
        assertTrue(lock.mayWrite("a"))
    }

    @Test
    fun `a tab not heard from for 5 seconds lets go`() {
        cover()
        lock.open("a", true)
        now = 4_000
        lock.report("a", true)
        now = 8_999
        lock.update()
        assertTrue(lock.mayWrite("a"))
        now = 9_001
        lock.update()
        assertFalse(lock.mayWrite("a"))
        assertNull(holder())
    }

    // Review Focus 1: a reload is a new page id while the old one's stream may still look open.
    @Test
    fun `a reloaded tab takes the settings back at once`() {
        cover()
        lock.open("a", true)
        lock.open("b", true)
        assertTrue(lock.mayWrite("a"))
        assertFalse(lock.mayWrite("b"))
        lock.leave("a")
        assertTrue(lock.mayWrite("b"))
        assertFalse(lock.mayWrite("a"))
    }

    // Review Focus 5: an EventSource reconnecting after sleep opens its new stream before the old one's end is seen.
    @Test
    fun `a stream that ends after its page opened another keeps the page present`() {
        cover()
        val s1 = lock.open("a", true)
        val s2 = lock.open("a", true)
        s1.close()
        s1.close()
        assertTrue(lock.mayWrite("a"))
        s2.close()
        assertFalse(lock.mayWrite("a"))
    }

    @Test
    fun `a stream closed after its page left brings nothing back`() {
        cover()
        val stream = lock.open("a", true)
        lock.leave("a")
        lock.open("a", true)
        stream.close()
        assertTrue(lock.mayWrite("a"))
    }

    @Test
    fun `the first tab shown takes them, the others wait their turn`() {
        lock.open("b", false)
        lock.open("a", true)
        lock.open("c", true)
        lock.report("b", true)
        cover()
        assertTrue(lock.mayWrite("a"))
        assertFalse(lock.mayWrite("b"))
        assertFalse(lock.mayWrite("c"))
        lock.leave("a")
        assertTrue(lock.mayWrite("c"))
        assertFalse(lock.mayWrite("b"))
    }

    @Test
    fun `Open in browser hands them to the tab it opened`() {
        lock.open("b", true)
        val key = lock.handOver()
        assertEquals(true, lock.view(WINDOW)["handOver"])
        assertFalse(lock.mayWrite(WINDOW))
        assertFalse(lock.mayWrite("b"))
        lock.open("c", true, "not the key")
        assertFalse(lock.mayWrite("c"))
        lock.open("d", true, key)
        assertTrue(lock.mayWrite("d"))
        assertEquals(false, lock.view(WINDOW)["handOver"])
        lock.leave("d")
        assertTrue("the window shows, so it holds them again", lock.mayWrite(WINDOW))
    }

    @Test
    fun `a hand-over no tab takes ends, and the window holds them again`() {
        lock.handOver()
        now = 15_000
        lock.update()
        assertNull(holder())
        now = 15_001
        lock.update()
        assertTrue(lock.mayWrite(WINDOW))
        assertEquals(false, lock.view(WINDOW)["handOver"])
        lock.handOver()
        lock.cancelHandOver()
        assertTrue(lock.mayWrite(WINDOW))
    }

    @Test
    fun `shared pages, the controls window, and requests without a page neither take nor wait`() {
        cover()
        lock.open(SHARED, true)
        lock.open(CONTROLS, true)
        lock.report(SHARED, true)
        assertNull(holder())
        lock.open("a", true)
        for (page in listOf(SHARED, CONTROLS, null)) {
            assertTrue("$page", lock.mayWrite(page))
            assertEquals("$page", mapOf("mine" to false, "holder" to "tab", "shared" to true, "handOver" to false), lock.view(page))
        }
        assertTrue(lock.mayWrite("a"))
    }

    @Test
    fun `the pages hear each change of holder, and only those`() {
        assertEquals("the window took them", 1, changes)
        lock.update()
        lock.open("a", true)
        assertEquals("a tab waiting changes no holder", 1, changes)
        shows = false
        lock.report("a", true)
        assertEquals("the tab took them", 2, changes)
        lock.report("a", true)
        lock.update()
        assertEquals(2, changes)
        uncover()
        lock.handOver()
        assertEquals("a hand-over began", 3, changes)
        lock.cancelHandOver()
        assertEquals("it ended", 4, changes)
    }

    @Test
    fun `the pages are told outside the lock's own lock`() {
        var other: Thread? = null
        lateinit var probe: SettingsLock
        probe = SettingsLock({ true }, {
            other = Thread { probe.view(WINDOW) }.also { it.start(); it.join(1_000) }
        }, { now })
        probe.update()
        assertFalse("a reader waited for the lock", other!!.isAlive)
    }

    @Test
    fun `page ids are letters, digits and hyphens`() {
        assertTrue(SettingsLock.validPage("3f2a9c4e-7b1d-4e8a-9f60-2c5d8e1b7a43"))
        assertTrue(SettingsLock.validPage("a".repeat(64)))
        for (page in listOf("", "a b", "a".repeat(65), "a/b", "é")) assertFalse(page, SettingsLock.validPage(page))
    }
}
