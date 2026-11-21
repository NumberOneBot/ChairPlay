package io.github.numberonebot.chairplay.config

import java.nio.file.Path

/**
 * The coffee ask's state in window.properties: the CarPlay time counted, whether the person supported (Buy me a coffee
 * or I already did, in the card or the toast), and when Later was last pressed. [now] is the time in epoch seconds.
 */
class SupportStore(private val dir: Path, private val now: () -> Long = { System.currentTimeMillis() / 1000 }) {
    /** The CarPlay seconds counted; null before this build counted any. */
    fun carPlaySeconds(): Long? = text(SECONDS)?.toLongOrNull()

    fun done(): Boolean = text(DONE)?.toBooleanStrictOrNull() ?: false

    /** The card asks: two hours of CarPlay, not supported, and Later not pressed in the last three days (owner, 2026-11-21). */
    fun due(): Boolean {
        val later = text(LATER)?.toLongOrNull()
        return !done() && (carPlaySeconds() ?: 0) >= ASK_AFTER_SECONDS && (later == null || now() - later >= LATER_SECONDS)
    }

    fun addCarPlay(seconds: Long) {
        if (seconds > 0) writeWindowState(dir, listOf(SECONDS to ((carPlaySeconds() ?: 0) + seconds).toString()))
    }

    fun supported() = writeWindowState(dir, listOf(DONE to "true"))

    fun later() = writeWindowState(dir, listOf(LATER to now().toString()))

    private fun text(key: String) = readWindowState(dir)?.getProperty(key)?.trim()

    private companion object {
        const val SECONDS = "usage.carplay.seconds"
        const val DONE = "support.done"
        const val LATER = "support.later.at"
        const val ASK_AFTER_SECONDS = 2 * 3600L
        const val LATER_SECONDS = 3 * 24 * 3600L
    }
}

/**
 * Counts the time a CarPlay session is live into [store]: [live] as one starts or ends, [save] now and then while it
 * runs, so a crash loses little. [clock] is in nanoseconds; a part of a second waits for the next save.
 */
class CarPlayTime(private val store: SupportStore, private val clock: () -> Long = System::nanoTime) {
    private var since: Long? = null

    /** The session is live ([on]) or not; told again as it was, it changes nothing. */
    @Synchronized
    fun live(on: Boolean) {
        if (on == (since != null)) return
        save()
        since = if (on) clock() else null
    }

    @Synchronized
    fun save() {
        val from = since ?: return
        val seconds = (clock() - from) / 1_000_000_000
        store.addCarPlay(seconds)
        since = from + seconds * 1_000_000_000
    }
}
