package io.github.numberonebot.chairplay

import io.github.numberonebot.chairplay.phone.PhoneLink

/**
 * Tells [onChange] when the live [link] has said nothing for five seconds, long before the connection gives up on it
 * (the picture stands still and buffered music plays on meanwhile), and again when it speaks or ends. [check] runs
 * from one thread, a few times a second.
 */
internal class LinkSilence(
    private val link: () -> PhoneLink?,
    private val nanoTime: () -> Long = System::nanoTime,
    private val onChange: (silent: Boolean) -> Unit,
) {
    private var silent = false

    fun check() {
        val now = link()?.let { nanoTime() - it.heardNanos >= SILENT_NANOS } ?: false
        if (now == silent) return
        silent = now
        onChange(now)
    }

    private companion object {
        // The iPhone's feedback comes every two seconds; its longest gap seen on a live session was 2.3 s.
        const val SILENT_NANOS = 5_000_000_000L
    }
}
