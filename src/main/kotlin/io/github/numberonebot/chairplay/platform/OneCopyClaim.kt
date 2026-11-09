package io.github.numberonebot.chairplay.platform

import java.io.Closeable

/** The system-wide claim on a [name] that keeps a second copy of the app from running on the same PC. */
internal fun interface OneCopyClaim {
    /**
     * Claims [name], waiting up to [timeoutMillis] for a copy that is going; the claim is held until it is closed or
     * the process ends. Null while another copy still holds it.
     */
    fun claim(name: String, timeoutMillis: Long): Closeable?
}
