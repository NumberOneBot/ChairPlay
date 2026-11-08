package com.shilapi.xcertplay.airplay

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.net.InetAddress

/** The timing clock syncs fast at the start, so the first SETRATE can wait for it (gradle/timing-upstream.gradle.kts). */
class NtpClockSyncTest {
    private fun <T> withClock(peer: FakeTimingPeer, block: (NtpClock) -> T): T = peer.use {
        NtpClock().use { clock ->
            clock.listen()
            clock.start(InetAddress.getLoopbackAddress(), peer.port)
            block(clock)
        }
    }

    private fun elapsedMs(since: Long) = (System.nanoTime() - since) / 1_000_000L

    @Test fun `waiting for the sync gives up after its timeout when the iPhone does not answer`() {
        withClock(FakeTimingPeer(answering = false)) { clock ->
            val start = System.nanoTime()
            assertFalse(clock.awaitSynced(300))
            val waited = elapsedMs(start)
            assertTrue("waited $waited ms", waited in 250..1500)
        }
    }

    @Test fun `a clock that was never started does not wait`() {
        NtpClock().use { clock ->
            clock.listen()
            assertFalse(clock.started)
            val start = System.nanoTime()
            assertFalse(clock.awaitSynced(0))
            assertTrue(elapsedMs(start) < 200)
        }
    }

    @Test fun `waiting ends soon after the iPhone answers, on the iPhone's clock`() {
        withClock(FakeTimingPeer()) { clock ->
            assertTrue(clock.started)
            val start = System.nanoTime()
            assertTrue(clock.awaitSynced(3000))
            val waited = elapsedMs(start)
            assertTrue("synced after $waited ms", waited < 700)
            assertTrue(clock.awaitSynced(0))
            val seconds = clock.syncedNtp().shiftRight(32).toLong() - 2_208_988_800L
            assertTrue("clock reads $seconds", Math.abs(seconds - FakeTimingPeer.PHONE_UNIX_SECONDS) < 60)
        }
    }

    @Test fun `requests come every 100 ms before the sync and every second after it`() {
        val peer = FakeTimingPeer(answering = false)
        withClock(peer) { clock ->
            assertTrue(peer.awaitRequests(3, 2000))
            assertTrue("gap ${peer.gapMs(1)} ms", peer.gapMs(1) < 500)
            peer.answering = true
            assertTrue(clock.awaitSynced(3000))
            val synced = peer.requestTimesNs.size
            assertTrue(peer.awaitRequests(synced + 2, 4000))
            assertTrue("gap ${peer.gapMs(synced + 1)} ms", peer.gapMs(synced + 1) >= 800)
        }
    }

    @Test fun `an iPhone that answers none of the fast requests is asked every second again`() {
        val peer = FakeTimingPeer(answering = false)
        withClock(peer) {
            assertTrue(peer.awaitRequests(32, 7000))
            assertTrue("gap ${peer.gapMs(1)} ms", peer.gapMs(1) < 500)
            assertTrue("gap ${peer.gapMs(31)} ms", peer.gapMs(31) >= 800)
        }
    }
}
