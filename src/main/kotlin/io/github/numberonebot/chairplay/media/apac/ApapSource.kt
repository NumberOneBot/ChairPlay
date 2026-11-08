package io.github.numberonebot.chairplay.media.apac

import java.io.Closeable
import java.io.IOException
import java.math.BigInteger
import java.util.concurrent.TimeUnit
import java.util.concurrent.locks.ReentrantLock
import kotlin.concurrent.withLock

/**
 * Resolves authenticated metadata on receipt; queued access units never consult mutable cookie state.
 * [fallback] is the cookie a stream that never gets one plays with; any real cookie replaces it.
 */
internal class ApapSource(
    private val fallback: ByteArray? = null,
    private val log: (String) -> Unit = {},
    private val cookieWaitMillis: Long = 2_000,
) : Closeable {
    private val cookies = ApapCookies()
    private var current: ByteArray? = null
    private var loudness: List<Float>? = null
    private var kind: Int? = null
    private var closed = false
    private val lock = ReentrantLock()
    private val remembered = lock.newCondition()

    /**
     * SETMAGICCOOKIE: [id]'s cookie, and the one a packet naming none takes from now on, until a packet names another.
     */
    fun remember(id: Long, cookie: ByteArray) {
        lock.withLock {
            if (closed) return
            if (cookies.remember(id, cookie, ::validateSpatialCookie)) current = cookie.copyOf()
            remembered.signalAll()
        }
    }

    fun resolve(unit: ApapAccessUnit): ApapAccessUnit = lock.withLock { resolveLocked(unit) }

    private fun resolveLocked(unit: ApapAccessUnit): ApapAccessUnit {
        if (closed) throw IOException("APAP source is closed")
        unit.time.rtpTime() // Validate time before committing any metadata.
        if (unit.metadata.samplesPerFrame?.let { it != 1024 } == true) {
            throw IOException("Unsupported APAP packet geometry")
        }
        if (unit.metadata.cookie == null) unit.metadata.cookieId?.let(::awaitCookie)
        val resolved = cookies.resolve(unit.metadata) ?: current ?: fallbackCookie()
        validateSpatialCookie(resolved)
        current = resolved
        loudness = unit.metadata.loudness ?: loudness
        kind = unit.metadata.mediaKind ?: kind
        return unit.copy(metadata = unit.metadata.copy(cookie = resolved.copyOf(), loudness = loudness, mediaKind = kind))
    }

    // A stream's first packets can come a moment before the SETMAGICCOOKIE naming their cookie (2 ms seen on a
    // reconnect): wait for it, the lock released, rather than end the stream. One that never comes still fails.
    private fun awaitCookie(id: Long) {
        var left = TimeUnit.MILLISECONDS.toNanos(cookieWaitMillis)
        while (!closed && !cookies.knows(id) && left > 0) left = remembered.awaitNanos(left)
        if (closed) throw IOException("APAP source is closed")
    }

    // Once taken, the fallback is [current], so this runs, and logs, once per stream.
    private fun fallbackCookie(): ByteArray {
        val cookie = fallback ?: throw IOException("APAP cookie missing")
        log("Buffered audio: no cookie from the iPhone; using AAC-LC 48 kHz stereo")
        return cookie.copyOf()
    }

    override fun close() {
        lock.withLock {
            closed = true
            current = null
            loudness = null
            kind = null
            cookies.clear()
            remembered.signalAll()
        }
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
