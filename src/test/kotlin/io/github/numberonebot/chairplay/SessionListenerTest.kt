package io.github.numberonebot.chairplay

import io.github.numberonebot.chairplay.carplay.carPlaySessions
import io.github.numberonebot.chairplay.carplay.idleSession
import io.github.numberonebot.chairplay.config.DesktopConfig
import io.github.numberonebot.chairplay.config.Theme
import io.github.numberonebot.chairplay.config.writeTestSettings
import io.github.numberonebot.chairplay.media.VideoWindow
import io.github.numberonebot.chairplay.phone.ClusterControl
import io.github.numberonebot.chairplay.phone.KnobInput
import io.github.numberonebot.chairplay.phone.CallKey
import io.github.numberonebot.chairplay.phone.MediaKey
import io.github.numberonebot.chairplay.phone.PhoneControl
import io.github.numberonebot.chairplay.phone.PhoneLink
import io.github.numberonebot.chairplay.phone.Protocol
import io.github.numberonebot.chairplay.phone.ScreenKind
import io.github.numberonebot.chairplay.phone.TouchPoint
import io.github.numberonebot.chairplay.ui.Screen
import io.github.numberonebot.chairplay.ui.UiModel
import io.github.numberonebot.chairplay.ui.UiSessions
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Test
import java.nio.file.Files
import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicReference

/** SessionListener: what the app does as the phone's link comes and goes, whatever the phone's protocol. */
class SessionListenerTest {
    /** A phone's link that records the night modes it is asked for. */
    private class FakeLink : PhoneLink {
        val nights = CopyOnWriteArrayList<Boolean>()
        override val protocol = Protocol.ANDROID_AUTO
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
    }

    private val calls = CopyOnWriteArrayList<String>()
    private val model = UiModel(wifiSet = true, autoconnect = true, autoshow = true)
    private val ui = object : UiSessions {
        override fun sessionActive() {
            calls += "active"
            model.sessionActive()
        }

        override fun sessionEnded() {
            calls += "ended"
            model.sessionEnded()
        }

        override fun carButton(): Boolean {
            calls += "car button"
            model.carButton()
            return true
        }
    }
    private val touchSender = Executors.newSingleThreadExecutor()
    private val watch = Executors.newSingleThreadScheduledExecutor()
    private val active = AtomicReference<PhoneLink?>()
    private val theme = CarPlayTheme(Theme.DARK, watch, touchSender, active::get)
    private val window = VideoWindow(320, 240)
    private val config = Files.createTempDirectory("chairplay-sessions").let { writeTestSettings(it); DesktopConfig(it) }
    private val sessions = SessionListener(active, ui, window, MainTitle(window, config), theme) { calls += "changed" }

    @After
    fun stop() {
        touchSender.shutdownNow()
        watch.shutdownNow()
    }

    private fun drain() = touchSender.submit {}.get(5, TimeUnit.SECONDS)

    // The phone takes the theme's look as the link goes live, and again when the theme is saved while it runs.
    @Test
    fun `the theme is sent on connect`() {
        val link = FakeLink()

        sessions.linked(link)
        drain()
        theme.saved("light")
        drain()

        assertEquals(listOf(true, false), link.nights)
        assertEquals(link, active.get())
    }

    // CarPlay's car button, or Back on its home screen, shows the settings over the phone's screen.
    @Test
    fun `the car button reaches the UI`() {
        sessions.linked(FakeLink())

        carPlaySessions(sessions::carButton).onHostUiRequested(idleSession())

        assertEquals(listOf("changed", "active", "car button"), calls)
        assertEquals(Screen.SETTINGS, model.state.screen)
    }

    @Test
    fun `the link's end ends the session on the screens`() {
        sessions.linked(FakeLink())

        sessions.linked(null)

        assertEquals(null, active.get())
        assertEquals(listOf("changed", "active", "changed", "ended"), calls)
    }

    // Disconnect and Reconnect clear the link themselves; its end heard after that changes nothing more.
    @Test
    fun `a link cleared already ends unheard`() {
        sessions.linked(FakeLink())
        active.set(null)

        sessions.linked(null)

        assertEquals(listOf("changed", "active"), calls)
    }
}
