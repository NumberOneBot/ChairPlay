package io.github.numberonebot.chairplay

import io.github.numberonebot.chairplay.phone.ClusterControl
import io.github.numberonebot.chairplay.phone.PhoneControl
import io.github.numberonebot.chairplay.phone.PhoneLink
import io.github.numberonebot.chairplay.phone.Protocol
import io.github.numberonebot.chairplay.phone.ScreenKind
import org.junit.Assert.assertEquals
import org.junit.Test

/** LinkSilence: a live link the phone stopped speaking on, told before the connection gives up on it. */
class LinkSilenceTest {
    private class HeardLink(override var heardNanos: Long) : PhoneLink {
        override val protocol = Protocol.CARPLAY
        override val control: PhoneControl get() = error("not used")
        override val cluster: ClusterControl? = null
        override fun nightMode(on: Boolean) = false
        override fun keyframe(screen: ScreenKind) {}
    }

    private var now = 0L
    private var link: PhoneLink? = null
    private val changes = mutableListOf<Boolean>()
    private val silence = LinkSilence({ link }, { now }) { changes += it }

    private fun seconds(value: Double) = (value * 1_000_000_000).toLong()

    @Test
    fun `a link heard within five seconds is not silent`() {
        link = HeardLink(0)
        now = seconds(4.9)

        silence.check()

        assertEquals(emptyList<Boolean>(), changes)
    }

    @Test
    fun `five seconds without a word make the link silent, told once`() {
        link = HeardLink(0)
        now = seconds(5.0)

        silence.check()
        now = seconds(6.0)
        silence.check()

        assertEquals(listOf(true), changes)
    }

    @Test
    fun `a word after the silence ends it`() {
        val heard = HeardLink(0)
        link = heard
        now = seconds(5.0)
        silence.check()

        heard.heardNanos = now
        silence.check()

        assertEquals(listOf(true, false), changes)
    }

    // The connection gave up on it: the waiting screen and its title take over.
    @Test
    fun `the link's end ends the silence`() {
        link = HeardLink(0)
        now = seconds(5.0)
        silence.check()

        link = null
        silence.check()

        assertEquals(listOf(true, false), changes)
    }

    @Test
    fun `no link is never silent`() {
        now = seconds(60.0)

        silence.check()

        assertEquals(emptyList<Boolean>(), changes)
    }
}
