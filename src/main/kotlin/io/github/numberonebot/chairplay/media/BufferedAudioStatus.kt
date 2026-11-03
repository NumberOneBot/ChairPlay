package io.github.numberonebot.chairplay.media

import java.util.concurrent.atomic.AtomicReference

/** Desktop telemetry seam. Remove the generated-copy hooks when upstream exposes equivalent buffer status. */
interface BufferedAudioStatusSink {
    fun bufferedAudioOpened(owner: Any)
    fun bufferedAudioUpdated(owner: Any, status: BufferedAudioStatus)
    fun bufferedAudioClosed(owner: Any)
}

/** Available audio, including the unplayed portion already handed to the renderer; not buffer capacity. */
data class BufferedAudioStatus(
    val phase: String, val samples: Long, val sampleRate: Int, val bytes: Long, val multichannelOutput: String? = null,
) {
    fun toPage(): Map<String, Any> = mapOf(
        "state" to phase, "seconds" to (Math.round(samples * 10.0 / sampleRate) / 10.0),
        "bytes" to bytes,
    ) + (multichannelOutput?.let { mapOf("multichannelOutput" to it) } ?: emptyMap())

    companion object {
        fun snapshot(
            rate: Int, hasAnchor: Boolean, audible: Boolean, queuedFrames: Int,
            fedTimestamps: List<Long>, position: Long?, sampleRate: Int, bytes: Long,
            multichannelOutput: String? = null,
        ): BufferedAudioStatus {
            // RTP timestamps wrap at 32 bits. Count only samples not yet passed by upstream's audible clock.
            val samples = queuedFrames * 1024L + fedTimestamps.sumOf { timestamp ->
                val elapsed = position?.let { (it - timestamp) and 0xffff_ffffL } ?: 0L
                if (elapsed >= 0x8000_0000L) 1024L else (1024L - elapsed).coerceAtLeast(0)
            }
            val phase = when {
                rate == 0 && hasAnchor -> "paused"
                samples == 0L -> "empty"
                rate == 0 || !audible -> "loading"
                else -> "playing"
            }
            return BufferedAudioStatus(phase, samples, sampleRate, bytes, multichannelOutput)
        }
    }
}

/** Audio threads only replace immutable snapshots. UI notification is polled on the app's 250 ms ticker. */
class BufferedAudioMonitor : BufferedAudioStatusSink {
    private data class Entry(val owner: Any, val status: BufferedAudioStatus)
    private val current = AtomicReference<Entry?>()
    private val shown = AtomicReference<Map<String, Any>?>()

    override fun bufferedAudioOpened(owner: Any) {
        current.set(Entry(owner, BufferedAudioStatus("empty", 0, 48_000, 0)))
    }

    override fun bufferedAudioUpdated(owner: Any, status: BufferedAudioStatus) {
        current.updateAndGet { if (it?.owner === owner) Entry(owner, status) else it }
    }

    override fun bufferedAudioClosed(owner: Any) {
        current.updateAndGet { if (it?.owner === owner) null else it }
    }

    fun state(): Map<String, Any>? = current.get()?.status?.toPage()
    fun poll(): Boolean = state().let { shown.getAndSet(it) != it }
}
