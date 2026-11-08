package io.github.numberonebot.chairplay.media.apac

import com.shilapi.xcertplay.airplay.BufferedAudioStream
import java.io.Closeable
import java.io.DataInputStream
import java.io.EOFException

/** APAP transport/renderer seam; the upstream bounded queue remains the sole stream owner. */
internal class ApapStreamSource(
    key: ByteArray,
    private val worker: SpatialWorker,
    private val log: (String) -> Unit = {},
    audioFormat: Long = 0,
    private val onClosed: () -> Unit = {},
) : Closeable {
    private val key = key.copyOf()
    private val metadata = ApapSource(apapFallbackCookie(audioFormat), log)
    @Volatile private var reader: ApapFrameReader? = null
    private var from: Long? = null
    private var pendingPosition: Long? = null
    private var authFailures = 0
    @Volatile var flushRange: ApapFlushRange? = null
    val cleanupComplete get() = worker.cleanupComplete
    val position get() = pendingPosition ?: worker.position
    val leadMillis get() = worker.leadMillis
    val multichannelOutput get() = worker.multichannelOutput
    val spatial get() = worker.spatial

    fun read(input: DataInputStream): BufferedAudioStream.Frame {
        val inputReader = reader ?: ApapFrameReader(input, key).also { reader = it }
        while (true) {
            val packet = try { inputReader.read() ?: throw EOFException() } catch (_: ApapAuthenticationException) {
                if (authFailures++ < 3) log("Spatial audio: authentication failed; packet discarded")
                continue
            }
            val unit = metadata.resolve(parseApap(packet))
            // State-only extensions affect the next AU, and are not 1024 frames of music.
            if (unit.audio.isEmpty()) continue
            if (flushRange?.contains(unit.sequence, unit.time.rtpTime()) == true) continue
            return BufferedAudioStream.Frame(unit.sequence, unit.time.rtpTime(), byteArrayOf(), unit, packet.plaintext.size + 43)
        }
    }

    fun remember(body: Map<String, Any?>) {
        val cookie = body["magicCookie"] as? ByteArray ?: throw java.io.IOException("APAP cookie missing")
        // Cookie IDs are opaque uint64 bit patterns; Bplist may decode the unsigned form as BigInteger.
        val id = (body["magicCookieID"] as? java.math.BigInteger)?.let {
            if (it.signum() < 0 || it.bitLength() > 64) throw java.io.IOException("Invalid APAP cookie ID")
            it.toLong()
        } ?: apapInteger(body["magicCookieID"])
        metadata.remember(id, cookie)
    }

    fun retainFrom(timestamp: Long) { from = timestamp; pendingPosition = timestamp }
    fun begin() { worker.begin(from); pendingPosition = null }
    fun push(unit: ApapAccessUnit) = worker.push(unit)
    fun pause() = worker.pause()
    override fun close() {
        try {
            worker.close()
            runCatching { reader?.close() }
            metadata.close()
            key.fill(0)
        } finally { onClosed() }
    }
}
