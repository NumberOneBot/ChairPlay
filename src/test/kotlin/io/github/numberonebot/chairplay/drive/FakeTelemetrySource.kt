package io.github.numberonebot.chairplay.drive

import io.github.numberonebot.chairplay.bridge.TelemetryRecord
import io.github.numberonebot.chairplay.bridge.TelemetrySource
import java.nio.ByteBuffer

/** A game's telemetry file in memory: it holds [record], or nothing while that is null. */
class FakeTelemetrySource : TelemetrySource {
    var record: TelemetryRecord? = null
    var opens = 0
    var reads = 0
    var closes = 0

    override fun open(): String? { opens++; return null }

    override fun read(into: ByteBuffer): Boolean {
        reads++
        TelemetryRecord.write(record ?: return false, into)
        return true
    }

    override fun close() { closes++ }
}
