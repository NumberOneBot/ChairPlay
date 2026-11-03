package io.github.numberonebot.chairplay.media.apac

import io.github.numberonebot.chairplay.media.AacPcmDecoder
import java.io.Closeable
import java.io.IOException
import java.nio.ByteBuffer
import java.nio.ByteOrder

internal enum class SpatialCodec { AAC, APAC }
internal data class SpatialDecoded(val codec: SpatialCodec, val inputChannels: Int, val samples: ShortArray, val startTrimFrames: Int = 0)

/** Cookie changes are ordered with their access units, while the output format and queued PCM stay fixed. */
internal class SpatialDecoder(private val library: ApacNativeLibrary?, private val outputChannels: Int) : SpatialPacketDecoder {
    private val owner = Thread.currentThread()
    private var cookie: ByteArray? = null
    private var aac: AacPcmDecoder? = null
    private var apac: ApacNativeDecoder? = null
    private var closed = false
    private var waitingForSync = true

    init { require(outputChannels == 2 || outputChannels == 6) }

    override fun decode(unit: ApapAccessUnit): SpatialDecoded {
        checkOwner()
        if (unit.metadata.samplesPerFrame?.let { it != 1024 } == true) throw IOException("Unsupported APAP packet geometry")
        val configuration = unit.metadata.cookie ?: throw IOException("APAP access unit has no resolved cookie")
        if (cookie?.contentEquals(configuration) != true) configure(configuration)
        val inputChannels = if (apac != null) 6 else 2
        val codec = if (apac != null) SpatialCodec.APAC else SpatialCodec.AAC
        if (unit.audio.isEmpty()) return SpatialDecoded(codec, inputChannels, ShortArray(0))
        // The verified CarPlay ASP corpus uses frame type 1. After a discontinuity,
        // accept only that proven entry point; dependent frames must not reuse stale history.
        if (apac != null && waitingForSync && (unit.audio[0].toInt() ushr 6 and 3) != 1)
            return SpatialDecoded(codec, inputChannels, ShortArray(0))
        waitingForSync = false
        val pcm = apac?.decode(unit.audio) ?: run {
            if (unit.audio.size > 8184) throw IOException("AAC access unit exceeds ADTS capacity")
            val bytes = aac!!.decode(unit.audio)
            if (bytes.size != 4096 && bytes.isNotEmpty()) throw IOException("Unexpected buffered AAC output geometry")
            val buffer = ByteBuffer.wrap(bytes).order(ByteOrder.LITTLE_ENDIAN).asShortBuffer()
            ShortArray(buffer.remaining()).also(buffer::get)
        }
        val frames = pcm.size / inputChannels
        val start = unit.metadata.trimStart?.trimFrames(48000, frames) ?: 0
        val end = unit.metadata.trimEnd?.trimFrames(48000, frames - start) ?: 0
        val trimmed = pcm.copyOfRange(start * inputChannels, (frames - end) * inputChannels)
        val output = if (inputChannels == 6) spatialPcm(trimmed, outputChannels) else {
            if (outputChannels == 2) trimmed else ShortArray(trimmed.size / 2 * 6).also { surround ->
                for (frame in 0 until trimmed.size / 2) {
                    surround[frame * 6] = trimmed[frame * 2]
                    surround[frame * 6 + 1] = trimmed[frame * 2 + 1]
                }
            }
        }
        return SpatialDecoded(codec, inputChannels, output, start)
    }

    override fun discontinuity() {
        checkOwner()
        aac?.close(); aac = null
        apac?.close(); apac = null
        cookie = null
        waitingForSync = true
    }

    private fun configure(next: ByteArray) {
        // Open/validate the replacement before retiring a usable decoder. The format hint
        // and SETUP index can remain stale across AAC/APAC track changes and are not used here.
        val nextAac = if (isSpatialAacCookie(next)) AacPcmDecoder(48000, 2) else null
        val nextApac = if (nextAac == null) ApacNativeDecoder(
            library ?: throw IOException("APAC decoder unavailable"), next) else null
        aac?.close()
        apac?.close()
        aac = nextAac
        apac = nextApac
        cookie = next.copyOf()
        waitingForSync = true
    }

    override fun close() {
        check(Thread.currentThread() === owner)
        if (closed) return
        closed = true
        aac?.close()
        apac?.close()
        cookie = null
    }

    private fun checkOwner() {
        check(Thread.currentThread() === owner) { "Spatial decoder belongs to another worker" }
        check(!closed) { "Spatial decoder is closed" }
    }
}
