package io.github.numberonebot.chairplay.config

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.nio.file.Files

class SupportStoreTest {
    private val dir = Files.createTempDirectory("chairplay-support")
    private var now = 1_000_000L
    private val store = SupportStore(dir) { now }

    @Test
    fun `the card is due from two hours of CarPlay, not before`() {
        assertNull(store.carPlaySeconds())
        assertFalse(store.due())
        store.addCarPlay(7199)
        assertFalse(store.due())
        store.addCarPlay(1)
        assertTrue(store.due())
        assertEquals(7200L, SupportStore(dir).carPlaySeconds())
    }

    @Test
    fun `once supported it is never due again`() {
        store.addCarPlay(9000)
        store.supported()
        assertFalse(store.due())
        assertTrue(SupportStore(dir).done())
    }

    @Test
    fun `Later holds the card for three days, across restarts`() {
        store.addCarPlay(9000)
        store.later()
        assertFalse(store.due())
        now += 3 * 24 * 3600 - 1
        assertFalse(SupportStore(dir) { now }.due())
        now += 1
        assertTrue(SupportStore(dir) { now }.due())
    }

    @Test
    fun `CarPlay time sums the live sessions, saved as it runs and at each end`() {
        var clock = 0L
        val time = CarPlayTime(store) { clock }
        time.live(true)
        clock += 90_000_000_000
        time.save()
        assertEquals(90L, store.carPlaySeconds())
        clock += 30_000_000_000
        time.live(false)
        clock += 500_000_000_000
        time.live(false)
        time.save()
        assertEquals("the time between sessions does not count", 120L, store.carPlaySeconds())
        time.live(true)
        repeat(2) { clock += 600_000_000; time.save(); time.live(true) }
        assertEquals("a part of a second is carried to the next save", 121L, store.carPlaySeconds())
    }
}
