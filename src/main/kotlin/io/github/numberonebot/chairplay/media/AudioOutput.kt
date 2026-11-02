package io.github.numberonebot.chairplay.media

import java.io.Closeable
import java.util.concurrent.atomic.AtomicInteger

/** One stream's worker; buffered music and realtime audio have different queue policies. */
internal interface AudioOutput : Closeable {
    val active: Boolean
    val dropped: AtomicInteger
    fun start()
    fun push(rtp: ByteArray, sample: Int)
    fun fadeOut(startNs: Long, durationNs: Long)
}
