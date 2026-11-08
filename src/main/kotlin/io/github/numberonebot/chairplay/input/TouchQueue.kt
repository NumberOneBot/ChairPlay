package io.github.numberonebot.chairplay.input

import io.github.numberonebot.chairplay.phone.TouchPoint

/**
 * Contacts waiting for the socket. A mouse reports far more often than a slow socket drains, so a move that
 * finds another move at the tail replaces it: only the latest position matters. A press is never merged
 * (the first down of a gesture) and a release is never dropped, so their order around the moves is kept.
 */
class TouchQueue {
    private class Entry(val contact: TouchPoint, val move: Boolean)

    private val entries = ArrayDeque<Entry>()
    private var inGesture = false

    @Synchronized
    fun offer(contact: TouchPoint) {
        val move = contact.down && inGesture
        inGesture = contact.down
        if (move && entries.lastOrNull()?.move == true) entries.removeLast()
        entries.addLast(Entry(contact, move))
    }

    @Synchronized
    fun poll(): TouchPoint? = entries.removeFirstOrNull()?.contact

    /** Drops what has not been sent; the next gesture starts clean. */
    @Synchronized
    fun clear() {
        entries.clear()
        inGesture = false
    }
}
