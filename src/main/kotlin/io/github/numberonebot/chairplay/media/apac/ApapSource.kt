package io.github.numberonebot.chairplay.media.apac

import java.io.Closeable
import java.io.IOException
import java.math.BigInteger

/** Resolves authenticated metadata on receipt; queued access units never consult mutable cookie state. */
internal class ApapSource : Closeable {
    private val cookies = ApapCookies()
    private var current: ByteArray? = null
    private var loudness: List<Float>? = null
    private var kind: Int? = null
    private var closed = false

    /**
     * SETMAGICCOOKIE: [id]'s cookie, and the one a packet naming none takes from now on, until a packet names another.
     * The iPhone gives YouTube's stream its cookie this way only, its packets naming none.
     */
    @Synchronized fun remember(id: Long, cookie: ByteArray) {
        if (closed) return
        if (cookies.remember(id, cookie, ::validateSpatialCookie)) current = cookie.copyOf()
    }

    @Synchronized fun resolve(unit: ApapAccessUnit): ApapAccessUnit {
        if (closed) throw IOException("APAP source is closed")
        unit.time.rtpTime() // Validate time before committing any metadata.
        if (unit.metadata.samplesPerFrame?.let { it != 1024 } == true) {
            throw IOException("Unsupported APAP packet geometry")
        }
        val resolved = cookies.resolve(unit.metadata) ?: current ?: throw IOException("APAP cookie missing")
        validateSpatialCookie(resolved)
        current = resolved
        loudness = unit.metadata.loudness ?: loudness
        kind = unit.metadata.mediaKind ?: kind
        return unit.copy(metadata = unit.metadata.copy(cookie = resolved.copyOf(), loudness = loudness, mediaKind = kind))
    }

    @Synchronized override fun close() {
        closed = true
        current = null
        loudness = null
        kind = null
        cookies.clear()
    }
}

internal fun validateSpatialCookie(cookie: ByteArray) {
    if (!isSpatialAacCookie(cookie)) apacCookieAtom(cookie)
}

/** Media time is signed, RTP is its floor at 48 kHz modulo 2^32; never multiply in a Long. */
internal fun ApapMediaTime.rtpTime(): Long {
    if (timescale <= 0) throw IOException("Invalid APAP timescale")
    val scaled = BigInteger.valueOf(value).multiply(BigInteger.valueOf(48000))
    val parts = scaled.divideAndRemainder(BigInteger.valueOf(timescale.toLong()))
    val floor = if (parts[1].signum() < 0) parts[0].subtract(BigInteger.ONE) else parts[0]
    return floor.and(BigInteger.valueOf(0xffffffffL)).toLong()
}

internal fun apapControlTime(body: Map<String, Any?>, prefix: String = ""): Long? {
    val rtpKey = if (prefix.isEmpty()) "rtpTime" else prefix + "TS"
    body[rtpKey]?.let { return apapInteger(it) and 0xffffffffL }
    val valueKey = if (prefix.isEmpty()) "mediaTimeValue" else prefix + "MediaTimeValue"
    val scaleKey = if (prefix.isEmpty()) "mediaTimeScale" else prefix + "MediaTimeScale"
    if (body[valueKey] == null && body[scaleKey] == null) return null
    val value = apapInteger(body[valueKey])
    val scale = apapInteger(body[scaleKey])
    if (scale !in 1..Int.MAX_VALUE.toLong()) throw IOException("Invalid APAP control timescale")
    return ApapMediaTime(value, scale.toInt()).rtpTime()
}

internal fun apapInteger(value: Any?): Long = when (value) {
    is Byte, is Short, is Int, is Long -> (value as Number).toLong()
    is BigInteger -> try { value.longValueExact() } catch (_: ArithmeticException) {
        throw IOException("APAP integer overflow")
    }
    else -> throw IOException("Missing or invalid APAP integer")
}
