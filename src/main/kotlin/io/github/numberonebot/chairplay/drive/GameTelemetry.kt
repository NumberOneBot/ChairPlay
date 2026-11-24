package io.github.numberonebot.chairplay.drive

import io.github.numberonebot.chairplay.bridge.BridgeControls
import io.github.numberonebot.chairplay.bridge.BridgePause
import io.github.numberonebot.chairplay.bridge.BridgeTouch
import io.github.numberonebot.chairplay.bridge.External
import io.github.numberonebot.chairplay.bridge.TelemetryInput
import io.github.numberonebot.chairplay.config.CarDisplay

/**
 * A game's telemetry file while bridge.telemetry is on, whatever the GPS does (owner, 2026-11-24): looked at on the
 * status tick while no game is there ([lookForGame]), read on the window loop while one is ([pass]), and closed there
 * ([close]); the lock keeps the two apart. Each pass sends the game's controls through [controls] and its look through
 * [theme]; what it says of the car is the GPS car's to follow.
 */
internal class GameTelemetry(private val open: () -> TelemetryInput, private val wake: () -> Unit) {
    val controls = BridgeControls()
    val pause = BridgePause()
    val touch = BridgeTouch()

    /** Hears the game's car display once per change, on the window loop: the app puts it in force and reconnects. */
    @Volatile var display: (CarDisplay) -> Unit = {}
    /** Hears the game's look once per change, on the window loop: dark or light, null as the game gives it back. */
    @Volatile var theme: (Boolean?) -> Unit = {}
    // The car display and the look the game sent last; the window loop's thread only.
    private var lastDisplay: CarDisplay? = null
    private var lastDark: Boolean? = null
    private val lock = Any()
    private var reader: TelemetryInput? = null
    @Volatile private var there = false

    /**
     * The status tick's look for a game, four times a second while none is there: one sending or paused wakes the window
     * loop, which reads it from its next pass until it is gone.
     */
    fun lookForGame() {
        synchronized(lock) {
            if (there || poll() == External.Absent) return
            there = true
        }
        wake()
    }

    /** A pass of the window loop: what the game says now, its controls sent; Absent while none is there. */
    fun pass(): External {
        if (!there) return External.Absent
        val said = poll()
        if (said == External.Absent) there = false
        val fresh = synchronized(lock) { reader?.fresh }
        controls.update(fresh?.controls)
        pause.update(fresh?.paused)
        // As the knob: a record without them or a game quiet for half a second lifts the finger and gives the look back.
        touch.update(fresh?.touch)
        dark(fresh?.dark)
        fresh?.display?.takeIf { it != lastDisplay }?.let {
            lastDisplay = it
            display(it)
        }
        return said
    }

    /** Whether the game's latest record gives the car's place (flag bit 8). */
    val positioned: Boolean get() = synchronized(lock) { reader?.latest?.position == true }

    /**
     * When the window loop reads the game next: every frame while it sends controls, a paused one too, as its menus are
     * where they are wanted most; else 20 times a second while it is there, to see it go; null with none.
     */
    fun due(): Double? = when {
        !there -> null
        synchronized(lock) { reader?.fresh?.controls } != null -> DriveControl.FRAME_SECONDS
        else -> DriveControl.GAMEPAD_POLL_SECONDS
    }

    /** bridge.telemetry off: the file closes, and the knob, the finger and the look the game held are let go. */
    fun close() {
        synchronized(lock) {
            reader?.close()
            reader = null
            there = false
        }
        controls.update(null)
        touch.update(null)
        dark(null)
    }

    private fun dark(dark: Boolean?) {
        if (dark == lastDark) return
        lastDark = dark
        theme(dark)
    }

    private fun poll(): External = synchronized(lock) { (reader ?: open().also { reader = it }).poll() }
}
