package io.github.numberonebot.chairplay.media.apac

import java.io.IOException
import java.nio.ByteBuffer

internal data class ApapMetadata(
    val formatIndex: Int? = null,
    val samplesPerFrame: Int? = null,
    val cookieId: Long? = null,
    val cookie: ByteArray? = null,
    val trimStart: ApapMediaTime? = null,
    val trimEnd: ApapMediaTime? = null,
    val loudness: List<Float>? = null,
    val mediaKind: Int? = null,
    val transitionId: Long? = null,
    val sourceFormatIndex: Int? = null,
    val unknownFields: Int = 0,
)

internal data class ApapAccessUnit(
    val time: ApapMediaTime,
    val sequence: Int,
    val metadata: ApapMetadata,
    val audio: ByteArray,
)

/** Parse only authenticated extension packets. TLV 2 is a hint; cookies determine the decoder. */
internal fun parseApap(packet: ApapPacket): ApapAccessUnit {
    if (!packet.extension) return ApapAccessUnit(packet.time, packet.sequence, ApapMetadata(), packet.plaintext)
    val cursor = ApapCursor(packet.plaintext)
    var metadata = ApapMetadata()
    while (cursor.remaining > 0) {
        val id = cursor.uleb128()
        if (id == 0uL) return ApapAccessUnit(packet.time, packet.sequence, metadata, cursor.rest())
        val length = cursor.uleb128()
        if (length > cursor.remaining.toULong()) throw IOException("Truncated APAP extension")
        val size = length.toInt()
        metadata = when (id) {
            2uL -> metadata.copy(formatIndex = cursor.integer(size))
            4uL -> metadata.copy(trimStart = cursor.time(size))
            5uL -> metadata.copy(trimEnd = cursor.time(size))
            7uL -> { // Legacy SoundCheck is a list of five-word records, not an RMS gain.
                if (size % 20 != 0) throw IOException("Invalid APAP SoundCheck size")
                cursor.skip(size)
                metadata
            }
            8uL -> metadata.copy(samplesPerFrame = cursor.integer(size))
            10uL -> metadata.copy(transitionId = cursor.unsigned(size).toLong())
            11uL -> metadata.copy(loudness = cursor.loudness(size))
            12uL -> {
                if (size != 1) throw IOException("Invalid APAP media kind size")
                metadata.copy(mediaKind = cursor.integer(size))
            }
            17uL -> metadata.copy(cookieId = cursor.unsigned(size).toLong())
            18uL -> {
                if (size !in 1..ApapCookies.MAX_COOKIE_BYTES) throw IOException("Invalid APAP cookie size")
                metadata.copy(cookie = cursor.bytes(size))
            }
            20uL -> metadata.copy(sourceFormatIndex = cursor.integer(size))
            else -> {
                cursor.skip(size)
                metadata.copy(unknownFields = metadata.unknownFields + 1)
            }
        }
    }
    throw IOException("Missing APAP extension terminator")
}

/** Per-AU floor and clamp, with no overflowing value * sampleRate intermediate. */
internal fun ApapMediaTime.trimFrames(sampleRate: Int, limit: Int): Int {
    require(sampleRate > 0 && limit >= 0)
    if (value <= 0 || timescale <= 0 || limit == 0) return 0
    val whole = value / timescale
    if (whole > limit / sampleRate) return limit
    val frames = whole * sampleRate
    val extra = ((value % timescale) * sampleRate) / timescale
    return minOf(limit.toLong(), frames + extra).toInt()
}

private class ApapCursor(private val data: ByteArray) {
    private var at = 0
    val remaining get() = data.size - at

    fun uleb128(): ULong {
        var value = 0uL
        for (i in 0..9) {
            if (remaining == 0) throw IOException("Truncated APAP ULEB128")
            val next = data[at++].toInt() and 255
            if (i == 9 && next > 1) throw IOException("APAP ULEB128 overflow")
            value = value or ((next and 127).toULong() shl (7 * i))
            if (next and 128 == 0) return value
        }
        throw IOException("APAP ULEB128 overflow")
    }

    fun unsigned(size: Int): ULong {
        if (size !in 1..8) throw IOException("Invalid APAP integer size")
        var value = 0uL
        repeat(size) { value = (value shl 8) or (data[at++].toInt() and 255).toULong() }
        return value
    }

    fun integer(size: Int): Int {
        val value = unsigned(size)
        if (value > Int.MAX_VALUE.toULong()) throw IOException("APAP integer overflow")
        return value.toInt()
    }

    fun time(size: Int): ApapMediaTime {
        if (size != 12) throw IOException("Invalid APAP media time size")
        val buffer = ByteBuffer.wrap(data, at, size)
        at += size
        return ApapMediaTime(buffer.long, buffer.int)
    }

    fun loudness(size: Int): List<Float> {
        if (size != 12) throw IOException("Invalid APAP loudness size")
        val buffer = ByteBuffer.wrap(data, at, size)
        at += size
        return List(3) { buffer.float }.also { values ->
            if (values.any { !it.isFinite() }) throw IOException("Nonfinite APAP loudness")
        }
    }

    fun bytes(size: Int) = data.copyOfRange(at, at + size).also { at += size }
    fun skip(size: Int) { at += size }
    fun rest() = bytes(remaining)
}
