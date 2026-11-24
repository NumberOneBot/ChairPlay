package io.github.numberonebot.chairplay.bridge

import io.github.numberonebot.chairplay.phone.TouchPoint

/** A game's touch (telemetry flag bit 13): [x] and [y] in 0..1 of CarPlay's picture from its top-left, [down] while on it. */
data class GameTouch(val x: Double, val y: Double, val down: Boolean)

/**
 * A game's finger on CarPlay's picture, held as its buttons are (owner, 2026-11-24): [send] takes each change, a press,
 * a move while down (a drag) and the release, as the window's clicks go, in fractions of the picture. Called on the
 * window loop only.
 */
class BridgeTouch {
    @Volatile var send: (TouchPoint) -> Unit = {}

    // The finger while down, where it last was.
    private var held: TouchPoint? = null

    /** The game's touch in its latest fresh record; null (none given, a game quiet or gone, the switch off) lifts it. */
    fun update(touch: GameTouch?) {
        val held = held
        if (touch == null || !touch.down) {
            if (held == null) return
            this.held = null
            send(held.copy(down = false))
            return
        }
        val point = TouchPoint(ID, touch.x, touch.y, true)
        if (point == held) return
        this.held = point
        send(point)
    }

    companion object {
        /** The game's contact: the window's clicks are contact 0. */
        const val ID = 1
    }
}
