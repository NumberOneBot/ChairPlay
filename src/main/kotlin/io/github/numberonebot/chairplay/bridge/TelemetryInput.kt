package io.github.numberonebot.chairplay.bridge

import io.github.numberonebot.chairplay.platform.Platforms
import java.io.Closeable
import java.nio.ByteBuffer

/** Where a game's telemetry record comes from: the 256-byte file Local\ChairPlay.Telemetry.v1. */
interface TelemetrySource {
    /** Null once open, else why it is not. */
    fun open(): String?

    /** True when [into] (256 bytes) holds a consistent copy. */
    fun read(into: ByteBuffer): Boolean
    fun close()
}

/** What an outside source says about the car. */
sealed interface External {
    /** A fresh, running record: the car follows it. */
    data class Drives(val record: TelemetryRecord) : External

    /** The source is paused or went quiet: the car stops. */
    object Halted : External

    /** No source, or silent for good: gamepad and keys drive again. */
    object Absent : External
}

/**
 * Polls a [TelemetrySource] and judges by its record counter whether the game is still driving. The first counter it
 * finds is a game's last before this input opened, perhaps long ago: only a later one is news.
 */
class TelemetryInput(
    private val source: TelemetrySource = Platforms.current.bridge.telemetry,
    private val log: (String) -> Unit,
    private val nanoTime: () -> Long = System::nanoTime,
) : Closeable {
    private val buffer = ByteBuffer.allocateDirect(BridgeLayout.SIZE)
    private var opened = false
    // Whether a failed open was logged; again only after an open succeeds.
    private var openFailed = false
    private var counter: Int? = null
    // The latest record once a counter changed; null before.
    private var last: TelemetryRecord? = null
    private var lastUpdateAt = 0L
    private var lastSource: String? = null

    fun poll(): External {
        if (!opened) open()
        val now = nanoTime()
        if (opened && source.read(buffer)) {
            val record = TelemetryRecord.parse(buffer)
            if (record != null) {
                val seen = counter
                counter = record.counter
                if (seen != null && record.counter != seen) {
                    if (record.source != lastSource) {
                        lastSource = record.source
                        log("Telemetry from ${record.source}")
                    }
                    lastUpdateAt = now
                    last = record
                }
                if (last != null) last = record  // a pause flag set without a new counter still counts
            }
        }
        val record = last ?: return External.Absent
        val silent = now - lastUpdateAt
        return when {
            silent >= GONE_NANOS -> External.Absent
            silent < FRESH_NANOS && !record.paused -> External.Drives(record)
            else -> External.Halted
        }
    }

    private fun open() {
        val error = source.open()
        opened = error == null
        if (error != null && !openFailed) log("Telemetry file not opened: $error")
        openFailed = error != null
    }

    override fun close() {
        if (opened) source.close()
        opened = false
    }

    private companion object {
        const val FRESH_NANOS = 500_000_000L
        const val GONE_NANOS = 5_000_000_000L
    }
}
