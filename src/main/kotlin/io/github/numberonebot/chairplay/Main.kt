package io.github.numberonebot.chairplay

import io.github.numberonebot.chairplay.config.DesktopConfig
import io.github.numberonebot.chairplay.config.AreaGroup
import io.github.numberonebot.chairplay.config.areaKey
import io.github.numberonebot.chairplay.carplay.CarPlayConfig
import io.github.numberonebot.chairplay.config.WindowSize
import io.github.numberonebot.chairplay.carplay.CarPlayProtocol
import io.github.numberonebot.chairplay.input.Control
import io.github.numberonebot.chairplay.input.ControlsConfig
import io.github.numberonebot.chairplay.input.Size
import io.github.numberonebot.chairplay.input.TouchQueue
import io.github.numberonebot.chairplay.input.TouchTracker
import io.github.numberonebot.chairplay.log.AppLog
import io.github.numberonebot.chairplay.log.LogLevel
import io.github.numberonebot.chairplay.log.routeAllOutput
import io.github.numberonebot.chairplay.log.routeFfmpegLog
import io.github.numberonebot.chairplay.media.runWindows
import io.github.numberonebot.chairplay.drive.Driving
import io.github.numberonebot.chairplay.drive.AlmanacCache
import io.github.numberonebot.chairplay.media.saveScreenshot
import io.github.numberonebot.chairplay.media.titleNotice
import io.github.numberonebot.chairplay.phone.PhoneLink
import io.github.numberonebot.chairplay.platform.Platforms
import io.github.numberonebot.chairplay.ui.BuildInfo
import io.github.numberonebot.chairplay.ui.ChairPlayUi
import io.github.numberonebot.chairplay.ui.startLine
import io.github.numberonebot.chairplay.ui.UiActions
import io.github.numberonebot.chairplay.ui.UiSafeAreaFrame
import java.nio.file.Path
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicReference
import kotlin.system.exitProcess

fun main(args: Array<String>) {
    // The platform first: nothing native loads before it is chosen, and a system with none says so and stops.
    Platforms.refusal()?.let {
        System.err.println(it)
        exitProcess(1)
    }
    if (args.firstOrNull() == "run") guardStart(appLog, start = ::runCarPlay) else exitProcess(runCommand(args))
}

/** The console and, once the app folder is known, logs\chairplay-<time>.log, at log.level. */
internal val appLog = AppLog()

internal fun log(message: String) = appLog.info(message)

private const val PREVIOUS_RUN_WAIT_MILLIS = 15_000L

/** Held for the life of the process; the system lets go of it when the process ends. */
private val instanceLock = AtomicReference<InstanceLock?>()
private const val STATUS_PAGE_MILLIS = 250L

/** The app folder, where the settings, keys and pairings live. */
internal fun dataDirectory(): Path = Platforms.current.shell.dataDirectory().also { log("settings and keys: $it") }

/**
 * Wireless CarPlay with the screen shown in a window and its sound played through Java Sound. Runs until the window
 * is closed or the process is stopped; every session end or failure restarts the bring-up. [windowsOpen] is called
 * once every window is open.
 */
private fun runCarPlay(windowsOpen: () -> Unit) {
    routeAllOutput(appLog)
    val dir = dataDirectory()
    appLog.open(dir.resolve("logs"))
    appLog.log(LogLevel.WARNING, BuildInfo.load().startLine())
    routeFfmpegLog(appLog)
    val config = DesktopConfig(dir)
    appLog.level = config.logLevel
    val controlsConfig = ControlsConfig.load(dir)
    log(controlsConfig.summary())
    log(config.displaySummary())
    log(CarPlayConfig.summary())
    val windows = AppWindows(dir, config, MainTitle.opening(config))
    val window = windows.main
    val title = MainTitle(window, config)
    val controlsWindow = windows.controls
    val altscreen = windows.altscreen
    // Set once the UI exists: the controls window's page hears of what it shows changing.
    val controlsChanged = AtomicReference<() -> Unit> {}
    val audio = AppAudio(dir, config, window, altscreen) { controlsChanged.get()() }
    val active = AtomicReference<PhoneLink?>()
    // sendTouch writes to the iPhone's socket and can block; the GL thread only maps and enqueues.
    // One thread keeps the press, moves and release in order.
    val touchSender = Executors.newSingleThreadExecutor { Thread(it, "touch-sender").apply { isDaemon = true } }
    val altView = AltscreenView(dir, config, altscreen, touchSender, active::get) { controlsChanged.get()() }
    // F12 saves both screens, each at the size the iPhone draws it.
    fun screenshots() {
        window.screenshot { image -> saveScreenshot(image, dir, ::log) }
        if (altView.on) altscreen.screenshot { image -> saveScreenshot(image, dir, ::log, "altscreen") }
    }
    val tracker = TouchTracker()
    val pending = TouchQueue()
    // Every sample goes through the tracker so it never misses a button change; only the send needs a session.
    window.setInputHandler { event ->
        val contact = tracker.handle(event, window.framebufferSize, window.pictureSize)?.takeIf { active.get() != null } ?: return@setInputHandler
        pending.offer(contact)
        touchSender.execute { pending.poll()?.let { point -> active.get()?.control?.touch(listOf(point)) } }
    }
    // The key bindings in force, which the Keyboard page's saves replace.
    val bindings = AtomicReference(controlsConfig)
    // The spoofed GPS's car (gps.*): driven on the window loop by a gamepad, or by the arrows in drive mode (F10); each
    // connection with gps.enabled sends it to the iPhone.
    val almanac = AlmanacCache(dir.resolve("gps-almanac.yuma"), ::log)
    val driving = Driving(config.gps, ::log, satelliteSentences = almanac::sentences)
    // Ctrl+R: set once the bootstrap exists; closing a session talks to the network, so not on the GL thread.
    val restartBootstrap = AtomicReference<() -> Unit> {}
    // Reconnect retires the session unheard: set below, this ends it on the screens as a lost one would, CarPlay giving
    // way to the waiting screen instead of its last picture standing still.
    val sessionDropped = AtomicReference<() -> Unit> {}
    fun reconnect() = Thread({ restartBootstrap.get()() }, "reconnect").apply { isDaemon = true }.start()
    val appControls = AppControls(bindings, audio.volume, driving, altView, touchSender, active::get, ::screenshots, reconnect = {
        log("reconnecting at the user's request")
        title.reconnect()
        audio.fadeOut()
        sessionDropped.get()()
        reconnect()
    }) { controlsChanged.get()() }.apply { fullscreen = window::toggleFullscreen }
    val lockReady = CountDownLatch(1)
    val startBootstrap = AtomicReference<() -> Unit> {}
    val stopBootstrap = AtomicReference<() -> Unit> {}
    // Looks at the Windows theme for CarPlay's look, at the GPS car for its page, and for a game's telemetry.
    val themeWatch = Executors.newSingleThreadScheduledExecutor { Thread(it, "theme-watch").apply { isDaemon = true } }
    val theme = CarPlayTheme(config.theme, themeWatch, touchSender, active::get)
    val ui = ChairPlayUi(dir, config, { Platforms.current.firewall.check(CarPlayConfig.PORT, config.uiServer.firewallPort, appLog) }, object : UiActions {
        override fun connect() = Thread({
            lockReady.await()
            title.connecting()
            startBootstrap.get()()
        }, "connect").apply { isDaemon = true }.start()
        override fun reconnect() = appControls.send(Control.Reconnect)
        override fun disconnect() {
            log("disconnecting at the user's request")
            title.reconnecting.set(false)
            audio.fadeOut()
            Thread({ stopBootstrap.get()() }, "disconnect").apply { isDaemon = true }.start()
        }
    }, window::repaint, ::log, buildMap {
        put("altscreen", altscreen.asUiWindow { altView.on })
        put("controls", controlsWindow.asUiWindow())
    }, appControls.page, window.asUiMainWindow(), onBindings = { saved ->
        bindings.set(saved)
        window.repaint()
        log(saved.summary())
    }, readFirewall = { Platforms.current.firewall.status(config.uiServer.firewallPort) }, altscreenWindow = altscreen.asUiMainWindow { altView.on },
        altscreenFrame = UiSafeAreaFrame { top, bottom, left, right, width, height ->
            altView.bring()
            altscreen.showFrame(top, bottom, left, right, of = Size(width, height))
        },
        onSaved = { saved ->
            saved["display.theme"]?.let(theme::saved)
            audio.saved(saved, config)
            windows.saved(saved)
            saved["log.level"]?.let { LogLevel.of(it) }?.let { appLog.level = it }
            // A view's part saved: the view moves there at once, among the areas this connection declared.
            if (saved.keys.any { key -> AreaGroup.entries.any { areaKey(it) == key } }) {
                runCatching { config.now() }.onSuccess(altView::areasSaved)
            }
            // A start or heading saved puts the GPS car there; a power or mass saved remakes it where it is.
            if (saved.keys.any { it.startsWith("gps.") }) runCatching { config.now() }.onSuccess { now -> driving.saved(now.gps, saved.keys) }
            // A saved turn card moves at once, as Shift+F11 moves it.
            saved["altscreen.cards"]?.let(altView::cardsSaved)
        },
        demo = altView.demos, gps = driving::state, driving = uiDriving(driving, window, ::screenshots, ::log),
        bufferedAudio = audio::bufferedAudio)
    altView.onWindowsChanged = { ui.windowsChanged() }
    // The CarPlay page offers Actual size while the window's size differs from the picture's.
    window.onResize = { ui.windowsChanged() }
    altscreen.onResize = { ui.windowsChanged() }
    controlsChanged.set { ui.controlsChanged() }
    appControls.controlsPass = { controlsWindow.repaint() }
    appControls.gaugeSize = { ui.saveSettings(mapOf("gps.gauges" to driving.settings.gauges.other().key)) }
    altscreen.onVisibility = {
        ui.windowsChanged()
        altView.visibilityChanged()
    }
    audio.screens.onClusterStream = altView::newStream
    // taskkill without /F asks one of the app's windows to close: the AltScreen's or the controls window's ends the app
    // as the main window's does, where its close button only hides it.
    altscreen.onOutsideClose = { window.close() }
    controlsWindow.onOutsideClose = { window.close() }
    controlsWindow.onVisibility = { shown ->
        runCatching { windows.controlsShown.save(shown) }.onFailure { log("controls window state not saved: $it") }
        ui.windowsChanged()
    }
    window.child = ui.view
    controlsWindow.child = ui.controlsView
    appControls.bindWindows(window, altscreen, controlsWindow, title::volumeFeedback) { ui.coversWindow }
    screenshotAfterPicture({ window.pictureSize.width != 0 }, ::screenshots)
    window.scrollHandler = { dy -> if (!ui.coversWindow) appControls.send(appControls.keys.scroll(dy)) }
    altscreen.scrollHandler = { dy -> appControls.send(appControls.keys.altscreenScroll(dy)) }
    fun sessionChanged() {
        appControls.global.reset()
        pending.clear()
        tracker.reset()
        // The HUD's waiting line and the session's parts rise and fade as the session comes and goes.
        if (altscreen.hud) altscreen.repaint()
        if (active.get() == null) altView.sessionGone()
    }
    // The GPS page follows the car four times a second, on the status tick below, told only when what it shows changed;
    // the car's place goes into gps.start and gps.heading as it stops.
    driving.saveTo { place -> ui.saveSettings(place) }
    // A session the iPhone went quiet on says so in both windows' titles until it speaks or ends.
    val silence = LinkSilence(active::get) { silent ->
        listOf(window, altscreen).forEach { it.titleNotice(if (silent) "iPhone not responding" else null) }
    }
    themeWatch.scheduleWithFixedDelay({
        driving.showPage(ui::gpsChanged)
        driving.lookForGame()
        audio.updateBufferedAudio()
        silence.check()
    }, STATUS_PAGE_MILLIS, STATUS_PAGE_MILLIS, TimeUnit.MILLISECONDS)
    // CarPlay: the iPhone's sound and screens, its route, song, call and battery messages, its GPS, and the connection.
    val sessions = SessionListener(active, ui, window, title, theme, ::sessionChanged)
    val carPlay = CarPlayProtocol(dir, config, audio, appControls.textInput, driving, sessions::carButton)
    // The iPhone's now playing, while a session runs: the controls window's page shows pause or play by it. An incoming
    // call flashes the CarPlay window's taskbar button, and brings the window forward with window.front.on.call.
    appControls.playing = { active.get() != null && carPlay.bus.snapshot().nowPlaying?.playing == true }
    watchPhoneData(carPlay.bus, appControls.playing, { controlsChanged.get()() }, window) { config.now().windowOnTop.frontOnCall }
    altView.paint(carPlay.bus, driving, config.accent)
    val connection = carPlay.connection
    restartBootstrap.set(connection::restart)
    sessionDropped.set {
        if (active.getAndSet(null) != null) {
            sessionChanged()
            ui.sessionEnded()
        }
        window.clearPicture()
    }
    startBootstrap.set(connection::start)
    // A retired stack's session ends unheard (onSessionEnded above stays quiet), so Disconnect clears it here.
    stopBootstrap.set {
        connection.stop()
        if (active.getAndSet(null) != null) sessionChanged()
        window.clearPicture()
        title.notConnected()
    }
    connection.onStatus = ui::bringUp
    connection.onLink = sessions::linked
    // The screens saved since come with the next connection: the main window takes the new picture's shape and size,
    // and the AltScreen turns on or off, or takes its new size and the view it starts with.
    carPlay.onConnection = { now ->
        ui.connected(now)
        val size = WindowSize(now.displayWidth, now.displayHeight)
        if (size != window.stream) {
            log("display ${size.width}x${size.height} px from this connection on")
            window.setStream(size)
            title.connecting(size)
        }
        now.altscreenWarnings.forEach { appLog.log(LogLevel.WARNING, it) }
        altView.connected(now)
    }
    Runtime.getRuntime().addShutdownHook(Thread {
        // The window is gone at once; the sound fades over the seconds the session takes to end.
        audio.fadeOut()
        appControls.close()
        driving.close()
        almanac.close()
        ui.close()
        themeWatch.shutdownNow()
        carPlay.close()
        touchSender.shutdownNow()
    })
    // A previous run that is still ending its session holds the iPhone's Bluetooth channel: wait for it first.
    Thread({
        instanceLock.set(InstanceLock.acquire(dir, PREVIOUS_RUN_WAIT_MILLIS) {
            log("the previous run is still closing; waiting for it")
            title.closingPrevious()
        })
        // Then a copy in another folder, the installed one or a portable one: only one of them runs.
        claimOneCopy(OneCopy.GOING_WAIT_MILLIS)
        lockReady.countDown()
        if (ui.connectsAtStart) {
            title.connecting()
            connection.start()
        } else {
            log(if (config.wifi == null) "no Wi-Fi network set; enter it on the start screen" else "waiting for Connect on the start screen")
            title.notConnected()
        }
    }, "instance-lock").apply { isDaemon = true }.start()
    // GLFW needs its windows and event loop on this, the main, thread; the bring-up runs on its own.
    runWindows(window, *listOfNotNull(altscreen, controlsWindow).toTypedArray(), pass = windowPass(driving, appControls), opened = { windowsOpen(); appControls.global.pass(); ui.checkUpdatesAtStart() })
    exitProcess(0)
}
