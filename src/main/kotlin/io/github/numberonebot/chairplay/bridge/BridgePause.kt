package io.github.numberonebot.chairplay.bridge

import io.github.numberonebot.chairplay.input.Control
import io.github.numberonebot.chairplay.phone.MediaKey

/**
 * A game's pause (telemetry flag bit 2: its menus, car choice, replays) as a car switched off (owner, 2026-11-24): the
 * iPhone's music, if [playing], pauses as the game pauses, and plays again as the game goes on, if this paused it. Music
 * the player started meanwhile is left as it is; calls and prompts are not touched. [send] takes the play/pause key, as
 * the keys' does. Called on the window loop only.
 */
class BridgePause {
    @Volatile var send: (Control) -> Unit = {}
    @Volatile var playing: () -> Boolean = { false }

    private var paused = false
    // Whether the music stands paused by this.
    private var ours = false

    /** The game's pause in its latest fresh record; null with none (a game quiet or gone), which changes nothing. */
    fun update(paused: Boolean?) {
        if (paused == null || paused == this.paused) return
        this.paused = paused
        if (paused) {
            if (playing()) {
                send(TOGGLE)
                ours = true
            }
        } else if (ours) {
            ours = false
            if (!playing()) send(TOGGLE)
        }
    }

    private companion object {
        val TOGGLE = Control.Media(MediaKey.PLAY_PAUSE)
    }
}
