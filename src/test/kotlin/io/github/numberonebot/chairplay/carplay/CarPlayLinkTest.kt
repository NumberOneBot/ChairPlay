package io.github.numberonebot.chairplay.carplay

import com.shilapi.xcertplay.airplay.AirPlayMediaHandler
import com.shilapi.xcertplay.airplay.AirPlaySession
import io.github.numberonebot.chairplay.media.AudioRenderer
import io.github.numberonebot.chairplay.media.ScreenDecoders
import io.github.numberonebot.chairplay.media.VideoWindow
import io.github.numberonebot.chairplay.phone.PhoneLink
import io.github.numberonebot.chairplay.phone.Protocol
import io.github.numberonebot.chairplay.phone.ScreenKind
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotSame
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.concurrent.atomic.AtomicInteger

/** CarPlayLink and CarPlayLinks: a CarPlay session as the app's live link, and which session that is. */
class CarPlayLinkTest {
    private val reported = mutableListOf<PhoneLink?>()
    private val links = CarPlayLinks({}) { reported += it }

    // A keyframe asked for through the link goes the way a decoder's own does: through the screens' request, which
    // asks the iPhone at most once a second.
    @Test
    fun `a keyframe takes the screens' rate-limited request`() {
        val sink = CarPlayMediaSink(ScreenDecoders(mapOf(ScreenKind.MAIN to VideoWindow(320, 240))), AudioRenderer())
        val asked = AtomicInteger()
        sink.setVideoRecoveryHandler(110) { asked.incrementAndGet() }
        val link = CarPlayLink(idleSession(), sink::keyframe)

        link.keyframe(ScreenKind.MAIN)
        link.keyframe(ScreenKind.MAIN)
        val deadline = System.nanoTime() + 5_000_000_000
        while (asked.get() == 0 && System.nanoTime() < deadline) Thread.sleep(10)
        Thread.sleep(200)

        assertEquals(1, asked.get())
        sink.close()
    }

    @Test
    fun `the link is CarPlay's, with the session's night mode`() {
        val session = idleSession()
        val link = CarPlayLink(session)

        link.nightMode(true)

        assertEquals(Protocol.CARPLAY, link.protocol)
        assertEquals(true, pendingNight(session))
    }

    // The session that went active is the link until it ends.
    @Test
    fun `a session is the link from going active to its end`() {
        val session = idleSession()

        links.active(session)
        links.ended(session)

        assertSame(session, (reported[0] as CarPlayLink).session)
        assertNull(reported[1])
        assertEquals(2, reported.size)
    }

    // DiPlay reports a session active on every RECORD: the same session stays the same link, so what is keyed by the
    // link (knob owners, held keys, the AltScreen's stop) does not start over; another session is another link.
    @Test
    fun `a session reported active again stays the same link`() {
        val session = idleSession()

        links.active(session)
        links.active(session)
        links.active(idleSession())

        assertEquals(3, reported.size)
        assertSame(reported[0], reported[1])
        assertNotSame(reported[1], reported[2])
    }

    // /info probes and pairing retries end without going active; the live session stays the link.
    @Test
    fun `a session that never went active leaves the link`() {
        links.active(idleSession())

        links.ended(idleSession())

        assertEquals(1, reported.size)
    }

    // Two sessions active at once: the first one's end leaves the second as the link.
    @Test
    fun `the end of a session replaced since leaves the link`() {
        val first = idleSession()
        val second = idleSession()
        links.active(first)
        links.active(second)

        links.ended(first)

        assertEquals(2, reported.size)
        assertTrue(reported.all { it != null })
        assertSame(second, (reported.last() as CarPlayLink).session)
    }

    // The iPhone's feedback, every two seconds while it is there, is the live link's sign of life; another session's
    // is not.
    @Test
    fun `the live session's feedback marks its link heard`() {
        var now = 1_000L
        val links = CarPlayLinks({}, { now }) { reported += it }
        val session = idleSession()
        links.active(session)

        now = 9_000L
        links.heard(idleSession())
        assertEquals(1_000L, reported.single()!!.heardNanos)
        links.heard(session)
        assertEquals(9_000L, reported.single()!!.heardNanos)
    }

    // The media handler DiPlay asks at each feedback still answers it, and the link hears it first.
    @Test
    fun `feedback reaches the link and DiPlay's engine alike`() {
        val heard = mutableListOf<AirPlaySession>()
        val answer = mapOf<String, Any?>("streams" to emptyList<Any>())
        val engine = object : AirPlayMediaHandler {
            override fun onFeedback(session: AirPlaySession) = answer
        }
        val session = idleSession()

        val media = engine.reportingFeedback { heard += it }

        assertSame(answer, media.onFeedback(session))
        assertEquals(listOf(session), heard)
    }
}
