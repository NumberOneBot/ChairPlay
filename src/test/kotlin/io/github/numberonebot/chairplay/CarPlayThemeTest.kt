package io.github.numberonebot.chairplay

import io.github.numberonebot.chairplay.config.Theme
import io.github.numberonebot.chairplay.phone.CallKey
import io.github.numberonebot.chairplay.phone.ClusterControl
import io.github.numberonebot.chairplay.phone.KnobInput
import io.github.numberonebot.chairplay.phone.MediaKey
import io.github.numberonebot.chairplay.phone.PhoneControl
import io.github.numberonebot.chairplay.phone.PhoneLink
import io.github.numberonebot.chairplay.phone.Protocol
import io.github.numberonebot.chairplay.phone.ScreenKind
import io.github.numberonebot.chairplay.phone.TouchPoint
import io.github.numberonebot.chairplay.platform.FakeShell
import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicReference
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Test

class CarPlayThemeTest {
    /** A phone's link that records the night modes it is asked for. */
    private class FakeLink : PhoneLink {
        val nights = CopyOnWriteArrayList<Boolean>()
        override val protocol = Protocol.CARPLAY
        override val control = object : PhoneControl {
            override fun knob(input: KnobInput, momentary: Boolean) {}
            override fun media(key: MediaKey) {}
            override fun call(key: CallKey) {}
            override fun voiceAssistant() {}
            override fun touch(points: List<TouchPoint>) {}
            override fun mapZoom(steps: Int) = false
        }
        override val cluster: ClusterControl? = null
        override fun nightMode(on: Boolean) = nights.add(on)
        override fun keyframe(screen: ScreenKind) {}
        override val heardNanos = 0L
    }

    private val touchSender = Executors.newSingleThreadExecutor()
    private val watch = Executors.newSingleThreadScheduledExecutor()

    @After
    fun stop() {
        touchSender.shutdownNow()
        watch.shutdownNow()
    }

    // The system theme is asked of the platform; one that cannot be read counts as light.
    @Test
    fun `a dark system turns CarPlay dark`() {
        val shell = FakeShell(dark = true)
        val link = FakeLink()
        val theme = CarPlayTheme(Theme.SYSTEM, watch, touchSender, AtomicReference<PhoneLink?>(link)::get, shell::appsDark)

        theme.send(link)
        shell.dark = null
        theme.send(link)
        touchSender.submit {}.get(5, TimeUnit.SECONDS)

        assertEquals(listOf(true, false), link.nights)
    }

    // A game's look (telemetry bits 11 and 12) over display.theme while it gives one, sent at once and once per change;
    // given back, the setting's look is in force again, the system's too.
    @Test
    fun `a game's theme holds over the setting until given back`() {
        val shell = FakeShell(dark = false)
        val link = FakeLink()
        val theme = CarPlayTheme(Theme.SYSTEM, watch, touchSender, AtomicReference<PhoneLink?>(link)::get, shell::appsDark)

        theme.send(link)
        theme.game(true)
        theme.game(true)
        theme.game(null)
        theme.saved("dark")
        theme.game(false)
        theme.game(null)
        touchSender.submit {}.get(5, TimeUnit.SECONDS)

        assertEquals(listOf(false, true, false, true, false, true), link.nights)
    }

    @Test
    fun `a session that starts while a game holds the theme gets the game's`() {
        val active = AtomicReference<PhoneLink?>()
        val theme = CarPlayTheme(Theme.PHONE, watch, touchSender, active::get, FakeShell(dark = false)::appsDark)
        theme.game(true)
        val link = FakeLink()
        active.set(link)

        theme.send(link)
        touchSender.submit {}.get(5, TimeUnit.SECONDS)
        assertEquals(listOf(true), link.nights)
    }
}
