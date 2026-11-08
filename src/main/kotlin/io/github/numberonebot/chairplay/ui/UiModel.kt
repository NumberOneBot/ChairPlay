package io.github.numberonebot.chairplay.ui

import io.github.numberonebot.chairplay.phone.LinkStatus
import java.time.LocalTime
import java.util.concurrent.CopyOnWriteArrayList

/** Where the connection to the iPhone stands, as the start screen tells it. */
enum class Phase {
    /** No Wi-Fi network is set; nothing connects until one is. */
    SETUP,

    /** Not connecting: ui.autoconnect is off and Connect was not pressed. */
    IDLE,

    /** Opening the network, the AirPlay listener and Bonjour. */
    STARTING,

    /** Calling the iPhone over Bluetooth. */
    CALLING,

    /** The iPhone answered and is to join the Wi-Fi (a new one: after the driver accepts CarPlay). */
    JOINING,

    /** CarPlay is running. */
    ACTIVE,

    /** The last bring-up failed; the next one starts shortly. */
    RETRYING,
}

/** What the main window shows. */
enum class Screen { START, SETTINGS, CARPLAY }

data class UiError(val message: String, val at: LocalTime)

data class UiState(
    val phase: Phase,
    val screen: Screen,
    val error: UiError? = null,
    val knownIphone: Boolean = false,
    val autoshow: Boolean,
    /** Saved settings that apply with the next connection, which has not started yet. */
    val reconnectPending: Set<String> = emptySet(),
    /** Saved settings that differ from what the app started with and apply at the next start. */
    val restartPending: Set<String> = emptySet(),
    /**
     * The start screen waits, black, for a connection the app makes on its own: as it starts with ui.autoconnect, and
     * when a session shown as CarPlay ends. A failed attempt, the settings asked for, or Disconnect end the wait, and
     * the settings show; CarPlay covers it as the session starts, the wait kept until then so the settings never show
     * between the two.
     */
    val waiting: Boolean = false,
) {
    /** The web UI covers the window; otherwise CarPlay's picture shows and takes the keys. */
    val showsUi: Boolean get() = screen != Screen.CARPLAY

    val canShowCarPlay: Boolean get() = phase == Phase.ACTIVE
}

/**
 * The start and settings screens' state machine. CarPlay shows only with a session: with [autoshow] it covers the
 * start screen as the session starts, otherwise it waits for Show CarPlay; the settings screen, also opened by the
 * car button on the CarPlay home screen, stays open until left. A session's end brings the start screen back.
 * Safe from any thread; listeners hear each change on the thread that made it.
 */
class UiModel(
    wifiSet: Boolean,
    autoconnect: Boolean,
    autoshow: Boolean,
    /** Get started is done; until then it shows, not the waiting screen. */
    setupDone: Boolean = true,
    private val clock: () -> LocalTime = LocalTime::now,
) {
    @Volatile
    var state = UiState(
        phase = when {
            !wifiSet -> Phase.SETUP
            autoconnect -> Phase.STARTING
            else -> Phase.IDLE
        },
        screen = Screen.START,
        autoshow = autoshow,
        waiting = wifiSet && autoconnect && setupDone,
    )
        private set

    private val listeners = CopyOnWriteArrayList<(UiState) -> Unit>()

    fun addListener(listener: (UiState) -> Unit) {
        listeners += listener
    }

    private fun update(change: (UiState) -> UiState) {
        val next: UiState
        synchronized(this) {
            val previous = state
            next = change(previous)
            if (next == previous) return
            state = next
        }
        listeners.forEach { it(next) }
    }

    fun bringUp(status: LinkStatus) = update { state ->
        when (status) {
            LinkStatus.Starting -> state.copy(phase = Phase.STARTING, reconnectPending = emptySet())
            LinkStatus.CallingPhone -> state.copy(phase = Phase.CALLING)
            is LinkStatus.Joining -> state.copy(phase = Phase.JOINING, knownIphone = status.knownPhone)
            is LinkStatus.Failed -> state.copy(phase = Phase.RETRYING, error = UiError(status.message, clock()), waiting = false)
        }
    }

    /** Connect was pressed, or the app connects on its own as it starts. */
    fun connecting() = update { if (it.phase == Phase.IDLE) it.copy(phase = Phase.STARTING) else it }

    fun sessionActive() = update { state ->
        val screen = if (state.autoshow && state.screen == Screen.START) Screen.CARPLAY else state.screen
        state.copy(phase = Phase.ACTIVE, error = null, screen = screen, waiting = state.waiting && screen == Screen.CARPLAY)
    }

    /** The bring-up starts over after a session, so the start screen says so at once; from CarPlay it waits, black. */
    fun sessionEnded() = update { state ->
        if (state.phase != Phase.ACTIVE) return@update state
        val fromCarPlay = state.screen == Screen.CARPLAY
        state.copy(phase = Phase.STARTING, screen = if (fromCarPlay) Screen.START else state.screen, waiting = fromCarPlay)
    }

    /** Disconnect: no session and no bring-up until Connect; CarPlay's picture gives way to the start screen. */
    fun disconnected() = update { state ->
        state.copy(phase = Phase.IDLE, screen = if (state.screen == Screen.CARPLAY) Screen.START else state.screen, waiting = false)
    }

    /** The car button on the CarPlay home screen. */
    fun carButton() = update { it.copy(screen = Screen.SETTINGS, waiting = false) }

    /** Shows [screen]; CarPlay only with a session. Returns whether it is shown. */
    fun show(screen: Screen): Boolean {
        var shown = false
        update { state ->
            shown = screen != Screen.CARPLAY || state.phase == Phase.ACTIVE
            // The settings asked for end the wait; CarPlay keeps it, for the session's end to wait again.
            if (shown) state.copy(screen = screen, waiting = state.waiting && screen == Screen.CARPLAY) else state
        }
        return shown
    }

    /** A Wi-Fi network was saved; returns true when that ends the setup. */
    fun wifiSaved(): Boolean {
        var ended = false
        update { state ->
            ended = state.phase == Phase.SETUP
            if (ended) state.copy(phase = Phase.IDLE) else state
        }
        return ended
    }

    fun setAutoshow(autoshow: Boolean) = update { it.copy(autoshow = autoshow) }

    fun pending(reconnect: Set<String>, restart: Set<String>) =
        update { it.copy(reconnectPending = it.reconnectPending + reconnect, restartPending = restart) }
}
