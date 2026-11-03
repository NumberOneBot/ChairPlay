package io.github.numberonebot.chairplay.media.apac

import java.io.IOException
import java.nio.ByteBuffer

/** A buffered AAC cookie is an ES_Descriptor, not a bare ASC or a fixed-offset byte pair. */
internal fun isSpatialAacCookie(cookie: ByteArray): Boolean {
    if (cookie.firstOrNull()?.toInt() != 3) return false
    fun descriptor(at: Int, limit: Int): Descriptor {
        var position = at
        if (position >= limit) throw IOException("Missing AAC descriptor")
        val tag = cookie[position++].toInt() and 255
        var size = 0
        repeat(4) { count ->
            if (position >= limit) throw IOException("Truncated AAC descriptor length")
            val next = cookie[position++].toInt() and 255
            size = (size shl 7) or (next and 127)
            if (next and 128 == 0) {
                if (size > limit - position) throw IOException("Truncated AAC descriptor")
                return Descriptor(tag, position, position + size)
            }
            if (count == 3) throw IOException("AAC descriptor length overflow")
        }
        throw IOException("Invalid AAC descriptor")
    }
    val root = descriptor(0, cookie.size)
    if (root.end != cookie.size || root.end - root.start < 3) throw IOException("Invalid AAC ES descriptor")
    val flags = cookie[root.start + 2].toInt() and 255
    var at = root.start + 3
    if (flags and 128 != 0) at += 2
    if (flags and 64 != 0) {
        if (at >= root.end) throw IOException("Truncated AAC URL descriptor")
        at += 1 + (cookie[at].toInt() and 255)
    }
    if (flags and 32 != 0) at += 2
    while (at < root.end) {
        val config = descriptor(at, root.end)
        if (config.tag == 4) {
            if (config.end - config.start < 13 || cookie[config.start].toInt() != 0x40 ||
                (cookie[config.start + 1].toInt() ushr 2 and 63) != 5) throw IOException("Unsupported AAC decoder config")
            var nested = config.start + 13
            while (nested < config.end) {
                val specific = descriptor(nested, config.end)
                if (specific.tag == 5) {
                    // MPEG-4 AAC-LC, 48 kHz, stereo, 1024 frames; no core/extension flags.
                    val asc = cookie.copyOfRange(specific.start, specific.end)
                    if (!asc.contentEquals(byteArrayOf(0x11, 0x90.toByte()))) throw IOException("Unsupported buffered AAC geometry")
                    return true
                }
                nested = specific.end
            }
        }
        at = config.end
    }
    throw IOException("AAC AudioSpecificConfig missing")
}

private data class Descriptor(val tag: Int, val start: Int, val end: Int)

/** Validate atom boundaries before native code; normalize supported wrappers to one dapa atom. */
internal fun apacCookieAtom(cookie: ByteArray): ByteArray {
    if (cookie.size !in 15..ApapCookies.MAX_COOKIE_BYTES) throw IOException("Invalid APAC cookie size")
    fun scan(start: Int, limit: Int, depth: Int): ByteArray? {
        if (depth > 4) throw IOException("APAC cookie nesting limit")
        var at = start
        while (at < limit) {
            if (limit - at < 8) throw IOException("Truncated APAC atom")
            val header = ByteBuffer.wrap(cookie, at, limit - at)
            val size = header.int.toLong() and 0xffff_ffffL
            val type = header.int
            if (size < 8 || size > limit - at) throw IOException("Invalid APAC atom length")
            val end = at + size.toInt()
            when (type) {
                0x64617061 -> { // dapa: flags, sync word, GlobalConfig.
                    if (size < 15 || cookie[at + 12].toInt() != 8 || cookie[at + 13].toInt() != 0)
                        throw IOException("Invalid APAC configuration sync")
                    return cookie.copyOfRange(at, end)
                }
                0x66726d61 -> { // frma(apac), followed by the actual atom.
                    if (size != 12L || header.int != 0x61706163) throw IOException("Unexpected APAC outer codec")
                }
                0x61706163 -> {
                    if (size < 12 || header.int != 0) throw IOException("Unsupported APAC wrapper")
                    scan(at + 12, end, depth + 1)?.let { return it }
                }
            }
            at = end
        }
        return null
    }
    return scan(0, cookie.size, 0) ?: throw IOException("APAC dapa configuration missing")
}
