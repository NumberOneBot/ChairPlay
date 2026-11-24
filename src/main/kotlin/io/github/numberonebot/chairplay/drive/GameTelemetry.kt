package io.github.numberonebot.chairplay.drive

import io.github.numberonebot.chairplay.bridge.BridgeControls
import io.github.numberonebot.chairplay.bridge.External
import io.github.numberonebot.chairplay.bridge.TelemetryInput

/**
 * A game's telemetry file while bridge.telemetry is on, whatever the GPS does (owner, 2026-11-24): looked at on the
 * status tick while no game is there ([lookForGame]), read on the window loop while one is ([pass]), and closed there
 * ([close]); the lock keeps the two apart. Each pass sends the game's controls through [controls]; what it says of the
 * car is the GPS car's to follow.
 */
internal class GameTelemetry(private val open: () -> TelemetryInput, private val wake: () -> Unit) {
    val controls = BridgeControls()
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
        controls.update(synchronized(lock) { reader?.fresh?.controls })
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

    /** bridge.telemetry off: the file closes, and the knob the game held is let go. */
    fun close() {
        synchronized(lock) {
            reader?.close()
            reader = null
            there = false
        }
        controls.update(null)
    }

    private fun poll(): External = synchronized(lock) { (reader ?: open().also { reader = it }).poll() }
}
