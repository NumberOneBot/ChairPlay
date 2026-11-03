package io.github.numberonebot.chairplay.media.apac

import java.io.IOException

/** One registry per type-103 session. First SETMAGICCOOKIE for an ID wins; inline cookies win per AU. */
internal class ApapCookies {
    private val values = HashMap<Long, ByteArray>()

    @Synchronized fun remember(id: Long, cookie: ByteArray, validate: (ByteArray) -> Unit = {}) {
        if (values.containsKey(id)) return
        if (values.size >= MAX_COOKIES || cookie.size !in 1..MAX_COOKIE_BYTES) {
            throw IOException("APAP cookie registry limit exceeded")
        }
        validate(cookie)
        values[id] = cookie.copyOf()
    }

    @Synchronized fun resolve(metadata: ApapMetadata): ByteArray? {
        metadata.cookie?.let { cookie ->
            if (cookie.size !in 1..MAX_COOKIE_BYTES) throw IOException("Invalid APAP cookie size")
            return cookie.copyOf()
        }
        val id = metadata.cookieId ?: return null
        return values[id]?.copyOf() ?: throw IOException("Unknown APAP cookie ID")
    }

    @Synchronized fun clear() { values.clear() }

    companion object {
        const val MAX_COOKIES = 16
        const val MAX_COOKIE_BYTES = 64 * 1024
    }
}
