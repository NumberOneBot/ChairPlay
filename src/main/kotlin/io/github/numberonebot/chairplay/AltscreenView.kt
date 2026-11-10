package io.github.numberonebot.chairplay

import io.github.numberonebot.chairplay.carplay.AREA_MOVE_SETTLE_MILLIS
import io.github.numberonebot.chairplay.carplay.AltscreenAreaMove
import io.github.numberonebot.chairplay.carplay.altscreenCommand
import io.github.numberonebot.chairplay.carplay.altscreenUrl
import io.github.numberonebot.chairplay.config.ALTSCREEN_MIN_WIDTH
import io.github.numberonebot.chairplay.config.DesktopConfig
import io.github.numberonebot.chairplay.config.WindowSize
import io.github.numberonebot.chairplay.config.altscreenTitle
import io.github.numberonebot.chairplay.config.drawnView
import io.github.numberonebot.chairplay.config.fittedTo
import io.github.numberonebot.chairplay.config.hudShowsSession
import io.github.numberonebot.chairplay.data.PhoneDataBus
import io.github.numberonebot.chairplay.drive.Driving
import io.github.numberonebot.chairplay.drive.GaugeDemo
import io.github.numberonebot.chairplay.drive.GaugeMotion
import io.github.numberonebot.chairplay.drive.GaugePainter
import io.github.numberonebot.chairplay.hud.HudDemo
import io.github.numberonebot.chairplay.hud.HudPainter
import io.github.numberonebot.chairplay.hud.HudState
import io.github.numberonebot.chairplay.hud.HudView
import io.github.numberonebot.chairplay.hud.hudState
import io.github.numberonebot.chairplay.hud.smallWellScale
import io.github.numberonebot.chairplay.input.Control
import io.github.numberonebot.chairplay.media.VideoWindow
import io.github.numberonebot.chairplay.media.saveScreenshot
import io.github.numberonebot.chairplay.phone.AltscreenCards
import io.github.numberonebot.chairplay.phone.AltscreenContent
import io.github.numberonebot.chairplay.phone.PhoneLink
import io.github.numberonebot.chairplay.session.AltscreenUi
import java.nio.file.Path
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.atomic.AtomicReference

/**
 * What the AltScreen [window] shows: the HUD or one of the iPhone's views and where its turn card goes, the part of the
 * frame each view shows in, the HUD demos, and what the iPhone is told it shows. Whatever changes the view runs on
 * [touchSender], as the session's own sends do; [active] is the running session, and [changed] tells the controls
 * window's page that what it shows changed.
 */
internal class AltscreenView(
    private val dir: Path,
    config: DesktopConfig,
    private val window: VideoWindow,
    private val touchSender: ExecutorService,
    private val active: () -> PhoneLink?,
    private val changed: () -> Unit,
) {
    /**
     * The AltScreen as the last connection had it (altscreen.*), null while altscreen.enabled is off; a connection
     * reads it again, on the touch thread like everything that changes its view.
     */
    val settings = AtomicReference(config.altscreen)

    /** Whether the AltScreen is on, as the last connection had it. */
    val on: Boolean get() = settings.get() != null

    /** What the AltScreen is set to show; null while it is off. */
    @Volatile
    var content: AltscreenContent? = config.altscreen?.content
        private set

    @Volatile private var cards = config.altscreen?.cards ?: AltscreenCards.TOP

    // The view the iPhone draws, settled (kept under the HUD: no wait on black); null for none, a new stream, or a move.
    @Volatile private var drawing: String? = null
    // The view of the iPhone's picked last, F11's other one from the HUD.
    @Volatile private var mapView = config.altscreen?.content?.takeIf { it != AltscreenContent.HUD } ?: AltscreenContent.MAP_CARD

    /**
     * The HUD demo playing (HudDemo), by its kind ("maneuvers" or "lanes"): CHAIRPLAY_HUD_DEMO starts one, the
     * Experiments page plays and stops them. The AltScreen shows the HUD, with the demo's made-up route, while one plays.
     */
    val hudDemo = AtomicReference<Pair<String, HudDemo.Running>?>(null)

    /** Set once the UI exists: the settings screen hears of the AltScreen turning on or off with a connection. */
    var onWindowsChanged: () -> Unit = {}

    // What the iPhone is told the AltScreen shows: its view while the window shows it, else nothing (stopUI).
    private val ui = AltscreenUi()

    // The AltScreen's view areas (altscreen.<view>.area and .safe.*): how many the connection declared, the settings it
    // declared them from, the one each view shows in, and the one CarPlay is in, which each connection and each new
    // AltScreen stream starts at the first of (initialViewArea). Every part is declared, so a part saved while CarPlay
    // runs is one the iPhone knows.
    private val areaCount = AtomicInteger(config.altscreenAreaCount)
    private val declared = AtomicReference(config)
    private val areaFor = AtomicReference<(AltscreenContent) -> Int>(config::altscreenAreaFor)
    private val currentArea = AtomicInteger(0)
    private val areaTimer = Executors.newSingleThreadScheduledExecutor { Thread(it, "view-area").apply { isDaemon = true } }

    init {
        settings.get()?.let { log("altscreen ${it.size.width}x${it.size.height} px, starting with ${it.content.key}") }
        window.log = { log("altscreen $it") }
        // No session yet: the HUD's waiting line, whatever the AltScreen is set to show once there is one.
        window.showView(true)
    }

    /**
     * The AltScreen's view as it is now, on the touch thread: the HUD or the iPhone's picture, the cluster URL a running
     * [session] is asked for, the title, the view remembered for the next start, and the controls window's page. A view
     * in another area than CarPlay's goes there first, and is asked for once the move is over, without which it stays
     * blurred mid-way.
     */
    fun show(session: PhoneLink?) {
        val alt = settings.get() ?: return
        val content = content ?: return
        val cards = cards
        // Without a session there is no picture to show: the HUD says where the connection stands instead.
        val hud = content == AltscreenContent.HUD || hudDemo.get() != null || session == null
        // The iPhone draws a view only while it is seen, not with the window closed, and under the HUD only if kept.
        val drawn = drawnView(content, mapView, hud, alt.streamUnderHud).takeIf { session != null }
        val seen = drawn != null && window.visible
        val url = altscreenUrl(drawn ?: content, cards)
        val area = areaFor.get()(drawn ?: content)
        if (session != null && seen && area != currentArea.get()) {
            val moved = AltscreenAreaMove(
                count = areaCount::get,
                send = { command -> altscreenCommand(session, command).also { log("altscreen view area $area of ${areaCount.get()} for ${content.key} sent=$it") } },
                showView = {
                    areaTimer.schedule({ runCatching { touchSender.execute { show(session) } } }, AREA_MOVE_SETTLE_MILLIS, TimeUnit.MILLISECONDS)
                },
            )(area)
            if (moved) {
                currentArea.set(area)
                drawing = null
                // From the HUD to a view the window goes black at once and waits there for the view the move asks for later.
                if (window.hud && !hud) window.showView(false, seen)
                return
            }
        }
        // Going to or from the HUD, or unseen, the window goes black before the iPhone is asked for the view, and stays so
        // until the view is in. Between its own views the iPhone blurs from one to the next itself: the key leaves the
        // URL out, so the window asks at once and shows the stream on.
        window.showView(hud, seen) {
            val sent = session?.cluster?.let { ui.tell(it, url, seen, key = session) }
            // A view stopped leaves no picture to come back to: the next one waits on black for the iPhone's.
            if (!seen) window.clearPicture()
            if (session != null) log("altscreen ${content.key}, cards ${cards.key}: $url ${if (seen) "showUI" else "stopUI"} sent=$sent")
            window.setTitle(altscreenTitle(alt.size, content, cards) + if (sent == false) " (not sent)" else "")
            (seen && drawing == url).also { drawing = url.takeIf { seen } }
        }
        changed()
    }

    /**
     * A view chosen or a frame shown on a page or the controls window is for looking at: the AltScreen comes back if
     * hidden or minimized, to the front, with the focus. Not while it is off.
     */
    fun bring() {
        if (on) window.setShown(true)
    }

    /** F11 switches the HUD and the view picked last, a view chosen shows, Shift+F11 moves the turn card: with [session]. */
    fun step(control: Control, session: PhoneLink) {
        val content = content ?: return
        // Shift+F11 has a turn card to move on Map + card alone.
        if (control == Control.NextAltscreenCards && !content.placesCards) return
        when (control) {
            Control.NextAltscreenContent -> this.content = content.toggled(mapView)
            is Control.ShowAltscreenContent -> this.content = control.content
            else -> cards = cards.next()
        }
        this.content?.takeIf { it != AltscreenContent.HUD }?.let { mapView = it }
        show(session)
    }

    /** A view's part saved, as the settings read [now]: the view moves there at once, among the areas declared. */
    fun areasSaved(now: DesktopConfig) {
        val connection = declared.get()
        areaFor.set { content -> connection.altscreenAreaIndex(content, now.altscreenChoice(content)) }
        touchSender.execute { show(active()) }
        bring()
    }

    /** A turn card's place saved ([value], altscreen.cards): it moves at once, as Shift+F11 moves it. */
    fun cardsSaved(value: String) {
        touchSender.execute {
            cards = AltscreenCards.entries.firstOrNull { it.key == value.trim().lowercase() } ?: AltscreenCards.TOP
            show(active())
        }
        bring()
    }

    /** Each new AltScreen stream starts on the iPhone's initial view: it is told what the window shows now. */
    fun newStream() {
        ui.newStream()
        runCatching {
            touchSender.execute {
                // It starts in the first view area again, as on the initial view; the view goes back to its own.
                currentArea.set(0)
                drawing = null
                show(active())
            }
        }
    }

    /** A closed AltScreen is not drawn on the iPhone, and asked for again as it opens; none once the app closes. */
    fun visibilityChanged() {
        runCatching { touchSender.execute { show(active()) } }
    }

    /**
     * A session gone leaves the AltScreen on the HUD's waiting line, whatever view it was showing; a new one's AltScreen
     * stream brings the view back ([newStream]). None once the app closes.
     */
    fun sessionGone() {
        runCatching { touchSender.execute { show(null) } }
    }

    /**
     * A connection's settings ([now]): the areas it declares, and the AltScreen turning on or off, or taking its new size
     * and the view it starts with.
     */
    fun connected(now: DesktopConfig) {
        areaCount.set(now.altscreenAreaCount)
        declared.set(now)
        areaFor.set(now::altscreenAreaFor)
        currentArea.set(0)
        touchSender.execute {
            val alt = now.altscreen
            val was = settings.getAndSet(alt)
            if (alt == null) {
                if (was != null) {
                    log("altscreen off from this connection on")
                    content = null
                    hudDemo.getAndSet(null)?.second?.stop()
                    window.setShown(false)
                    changed()
                    onWindowsChanged()
                }
            } else {
                if (alt.size != window.stream) window.setStream(alt.size, WindowSize(ALTSCREEN_MIN_WIDTH, 1).fittedTo(alt.size))
                if (alt.size != was?.size) log("altscreen ${alt.size.width}x${alt.size.height} px from this connection on")
                content = alt.content
                cards = alt.cards
                show(null)
                if (was == null) {
                    window.setShown(true)
                    onWindowsChanged()
                }
            }
        }
    }

    /**
     * The AltScreen's HUD shows [bus]'s route, song and call, drawn again when it changes, in the [accent] (ui.accent),
     * with the GPS car's speedometer and tachometer over whatever the AltScreen shows while [driving] drives it.
     * CHAIRPLAY_HUD_DEMO plays a made-up route in place of the iPhone's guidance, beside its song, for watching the
     * maneuvers (HudDemo).
     */
    fun paint(bus: PhoneDataBus, driving: Driving, accent: Int) {
        HudDemo.fromEnvironment()?.let { demo ->
            hudDemo.set((if (demo.lanes) "lanes" else "maneuvers") to demo.start(window::repaint) { name ->
                window.screenshot { image -> saveScreenshot(image, dir, ::log, "altscreen-demo-$name") }
            })
        }
        // The wells' coming and going, which the HUD follows to make room in step with them.
        val gaugeMotion = GaugeMotion()
        window.overlay = HudPainter({
            val demo = hudDemo.get()?.second
            val snapshot = bus.snapshot().let { live -> demo?.over(live) ?: live }
            val state = if (hudShowsSession(content, active() != null, demo != null)) hudState(snapshot) else HudState()
            HudView(demo != null || active() != null, state, snapshot.artwork, gaugeMotion.there, gaugeMotion.smallness)
        }, ::log, window::repaint, accent)
        bus.addListener { if (window.hud) window.repaint() }
        // The gauges' demo in place of drive mode's gauges while it plays.
        val gaugeDemo = GaugeDemo(driving).also { demos.gauges = it }
        window.topLayer = GaugePainter({ gaugeDemo.view() ?: driving.gauges() }, ::log, accent, gaugeMotion, ::smallWellScale)
    }

    /** The Experiments page's demos: the HUD's and the gauges'. */
    val demos = AltscreenDemos(this, window, active)
}
