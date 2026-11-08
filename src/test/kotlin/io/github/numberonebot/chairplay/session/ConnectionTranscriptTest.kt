package io.github.numberonebot.chairplay.session

import com.shilapi.xcertplay.airplay.AirPlaySession
import com.shilapi.xcertplay.airplay.AirPlaySessionListener
import com.shilapi.xcertplay.airplay.BplistCodec
import com.shilapi.xcertplay.airplay.MediaSink
import io.github.numberonebot.chairplay.CarPlayTheme
import io.github.numberonebot.chairplay.MainTitle
import io.github.numberonebot.chairplay.SessionListener
import io.github.numberonebot.chairplay.carplay.CarPlayLink
import io.github.numberonebot.chairplay.carplay.PhoneDataTap
import io.github.numberonebot.chairplay.carplay.carPlaySessions
import io.github.numberonebot.chairplay.carplay.pendingNight
import io.github.numberonebot.chairplay.config.DesktopConfig
import io.github.numberonebot.chairplay.config.Theme
import io.github.numberonebot.chairplay.config.writeTestSettings
import io.github.numberonebot.chairplay.media.VideoWindow
import io.github.numberonebot.chairplay.phone.LinkStatus
import io.github.numberonebot.chairplay.phone.PhoneLink
import io.github.numberonebot.chairplay.ui.UiModel
import io.github.numberonebot.chairplay.ui.UiSessions
import io.github.numberonebot.chairplay.ui.UiState
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Test
import java.nio.file.Files
import java.util.IdentityHashMap
import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicReference

/**
 * What the app makes of a connection, step by step: each bring-up status, each session that goes active or ends, the
 * car button, and the theme, as the start screen's state and the night mode the session is to send the iPhone. Pinned
 * before the connection ran through phone-neutral types, to stay the same after.
 */
class ConnectionTranscriptTest : WirelessBootstrapFixture() {
    private val events = CopyOnWriteArrayList<String>()
    private val model = UiModel(wifiSet = true, autoconnect = true, autoshow = true)
    private val ui = object : UiSessions {
        override fun sessionActive() = model.sessionActive()
        override fun sessionEnded() = model.sessionEnded()
        override fun carButton(): Boolean {
            model.carButton()
            return true
        }
    }
    private val touchSender = Executors.newSingleThreadExecutor()
    private val watch = Executors.newSingleThreadScheduledExecutor()
    private val numbers = IdentityHashMap<AirPlaySession, Int>()
    private val config = Files.createTempDirectory("chairplay-transcript").let { writeTestSettings(it); DesktopConfig(it) }
    private val window = VideoWindow(320, 240)

    init {
        model.addListener { events += "  ${describe(it)}" }
    }

    @After
    fun stop() {
        touchSender.shutdownNow()
        watch.shutdownNow()
    }

    @Test(timeout = 60_000)
    fun `a new iPhone connects, asks for the car's screen, and leaves`() {
        val active = AtomicReference<PhoneLink?>()
        val theme = CarPlayTheme(Theme.DARK, watch, touchSender, active::get)
        val links = SessionListener(active, ui, window, MainTitle(window, config), theme) {
            events += "changed, active ${(active.get() as CarPlayLink?)?.session?.let(::number) ?: "none"}"
        }
        val base = carPlaySessions(links::carButton)
        val sessionOne = AtomicReference<AirPlaySession>()
        val sessions = object : AirPlaySessionListener by base {
            override fun onSessionActive(session: AirPlaySession) {
                sessionOne.compareAndSet(null, session)
                events += "session ${number(session)} active"
                base.onSessionActive(session)
            }

            override fun onSessionEnded(session: AirPlaySession) {
                events += "session ${number(session)} ended"
                base.onSessionEnded(session)
            }

            override fun onHostUiRequested(session: AirPlaySession) {
                events += "session ${number(session)} asks for the car's screen"
                base.onHostUiRequested(session)
            }
        }
        val onLink = { link: PhoneLink? ->
            links.linked(link)
            if (link != null) night((link as CarPlayLink).session)
        }
        Harness(sessions = sessions, onStatus = ::status, onLink = onLink).use { harness ->
            val port = harness.nextPort()
            await("status joining known=false")
            val phone = RtspClient(harness.loopback, port)
            assertOk(phone.request("RECORD", "*"))
            await("night")
            assertOk(phone.request("POST", "/command", BplistCodec.encode(mapOf("type" to "requestUI", "params" to emptyMap<String, Any?>()))))
            await("asks for the car's screen")
            theme.saved("light")
            night(sessionOne.get())
            // A /info probe or a pairing retry: connects, asks, leaves without RECORD.
            RtspClient(harness.loopback, port).use { assertOk(it.request("OPTIONS", "*")) }
            await("session 2 ended")
            phone.close()
            await("status joining known=false", count = 2)
        }

        assertEquals(NEW_IPHONE, events.toList())
    }

    @Test(timeout = 30_000)
    fun `a known iPhone joins as known`() {
        Harness(knownIphone = true, onStatus = ::status).use { await("status joining known=true") }

        assertEquals(KNOWN_IPHONE, events.toList())
    }

    @Test(timeout = 30_000)
    fun `a failed bring-up retries after its wait`() {
        val dir = Files.createTempDirectory("chairplay-bootstrap")
        writeTestSettings(dir)
        WirelessBootstrap(DesktopConfig(dir), object : MediaSink {}, object : AirPlaySessionListener {}, PhoneDataTap.NONE) {
            throw IllegalArgumentException("no MFi files")
        }.use { bootstrap ->
            bootstrap.onStatus = ::status
            bootstrap.start()
            await("status failed", count = 2)
            bootstrap.stop()
        }

        assertEquals(FAILED, events.toList())
    }

    private fun status(status: LinkStatus) {
        events += "status " + when (status) {
            LinkStatus.Starting -> "starting"
            LinkStatus.CallingPhone -> "calling"
            is LinkStatus.Joining -> "joining known=${status.knownPhone}"
            is LinkStatus.Failed -> "failed: ${status.message}, retry in ${status.retryMillis} ms"
        }
        model.bringUp(status)
    }

    /** The night mode [session] holds for the iPhone, once the theme's send ran. */
    private fun night(session: AirPlaySession) {
        touchSender.submit {}.get(5, TimeUnit.SECONDS)
        events += "night for session ${number(session)}: ${pendingNight(session)}"
    }

    private fun number(session: AirPlaySession): Int = synchronized(numbers) { numbers.getOrPut(session) { numbers.size + 1 } }

    private fun await(marker: String, count: Int = 1) {
        val deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(15)
        while (events.count { marker in it } < count) {
            check(System.nanoTime() < deadline) { "no \"$marker\" ($count) in 15 s: $events" }
            Thread.sleep(10)
        }
    }

    private fun describe(state: UiState) = "phase=${state.phase.name.lowercase()} screen=${state.screen.name.lowercase()} " +
        "knownIphone=${state.knownIphone} waiting=${state.waiting} error=${state.error?.message}"

    private companion object {
        val NEW_IPHONE = listOf(
            "status starting",
            "status calling",
            "  phase=calling screen=start knownIphone=false waiting=true error=null",
            "status joining known=false",
            "  phase=joining screen=start knownIphone=false waiting=true error=null",
            "session 1 active",
            "changed, active 1",
            "  phase=active screen=carplay knownIphone=false waiting=true error=null",
            "night for session 1: true",
            "session 1 asks for the car's screen",
            "  phase=active screen=settings knownIphone=false waiting=false error=null",
            "night for session 1: false",
            "session 2 ended",
            "session 1 ended",
            "changed, active none",
            "  phase=starting screen=settings knownIphone=false waiting=false error=null",
            "status starting",
            "status calling",
            "  phase=calling screen=settings knownIphone=false waiting=false error=null",
            "status joining known=false",
            "  phase=joining screen=settings knownIphone=false waiting=false error=null",
        )
        val KNOWN_IPHONE = listOf(
            "status starting",
            "status calling",
            "  phase=calling screen=start knownIphone=false waiting=true error=null",
            "status joining known=true",
            "  phase=joining screen=start knownIphone=true waiting=true error=null",
        )
        val FAILED = listOf(
            "status starting",
            "status failed: no MFi files, retry in 5000 ms",
            "  phase=retrying screen=start knownIphone=false waiting=false error=no MFi files",
            "status starting",
            "  phase=starting screen=start knownIphone=false waiting=false error=no MFi files",
            "status failed: no MFi files, retry in 5000 ms",
            "  phase=retrying screen=start knownIphone=false waiting=false error=no MFi files",
        )
    }
}
