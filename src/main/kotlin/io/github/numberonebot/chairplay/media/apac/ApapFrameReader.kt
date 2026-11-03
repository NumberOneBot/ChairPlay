package io.github.numberonebot.chairplay.media.apac

import com.shilapi.xcertplay.airplay.AirPlayCrypto
import org.bouncycastle.crypto.InvalidCipherTextException
import java.io.Closeable
import java.io.DataInputStream
import java.io.IOException
import java.io.InputStream
import java.nio.ByteBuffer
import javax.crypto.AEADBadTagException

internal data class ApapMediaTime(val value: Long, val timescale: Int)

internal data class ApapPacket(
    val time: ApapMediaTime,
    val sequence: Int,
    val extension: Boolean,
    val plaintext: ByteArray,
)

internal class ApapAuthenticationException : IOException("APAP authentication failed")

/**
 * The APAP transport is separate from upstream's length-prefixed RTP AAC transport.
 * Wire reference: mib2q-carplay-rgi df73e061, docs/audio/BUFFERED_AUDIO_PATH.md.
 * Remove this adapter when upstream supplies equivalent APAP framing/authentication,
 * retaining the independent 64x64 vectors and fragmented-stream regression tests.
 */
internal class ApapFrameReader(
    input: InputStream,
    key: ByteArray,
    private val maxRecordBytes: Int = MAX_RECORD_BYTES,
) : Closeable {
    private val stream = DataInputStream(input)
    private val key = key.copyOf()
    @Volatile private var closed = false

    init {
        require(key.size == 32)
        require(maxRecordBytes in MIN_RECORD_BYTES..MAX_RECORD_BYTES)
    }

    /** Null only at a record boundary. A bad tag consumes one record; bad framing ends the stream. */
    fun read(): ApapPacket? {
        if (closed) throw IOException("APAP reader is closed")
        val first = stream.read()
        if (first < 0) return null
        val length = (first.toLong() shl 24) or (stream.readUnsignedByte().toLong() shl 16) or
            (stream.readUnsignedByte().toLong() shl 8) or stream.readUnsignedByte().toLong()
        if (length !in MIN_RECORD_BYTES.toLong()..maxRecordBytes.toLong()) {
            throw IOException("Invalid APAP record length")
        }
        val record = ByteArray(length.toInt() - 4)
        stream.readFully(record)
        val header = ByteBuffer.wrap(record, 0, HEADER_BYTES)
        val time = ApapMediaTime(header.long, header.int)
        val sequence = ((record[12].toInt() and 255) shl 16) or
            ((record[13].toInt() and 255) shl 8) or (record[14].toInt() and 255)
        val sealed = record.copyOfRange(HEADER_BYTES, record.size - NONCE_BYTES)
        // Original 64x64 ChaCha equals IETF with a zero nonce prefix below block 2^32.
        // The 1 MiB record cap keeps every message far below that boundary; independent
        // PyCryptodome eight-byte-nonce vectors verify both on-wire AAD variants.
        val nonce = ByteArray(12)
        record.copyInto(nonce, 4, record.size - NONCE_BYTES)
        for (extension in listOf(false, true)) {
            val aad = record.copyOfRange(if (extension) 0 else 4, 12)
            val plaintext = try {
                AirPlayCrypto.chachaOpen(key, nonce, sealed, aad)
            } catch (_: AEADBadTagException) {
                continue
            } catch (_: InvalidCipherTextException) {
                continue
            }
            return ApapPacket(time, sequence, extension, plaintext)
        }
        throw ApapAuthenticationException()
    }

    override fun close() {
        closed = true
        try { stream.close() } finally { key.fill(0) }
    }

    companion object {
        const val MAX_RECORD_BYTES = 1024 * 1024
        private const val HEADER_BYTES = 15
        private const val NONCE_BYTES = 8
        private const val MIN_RECORD_BYTES = 4 + HEADER_BYTES + 16 + NONCE_BYTES
    }
}
