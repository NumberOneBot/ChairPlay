package io.github.numberonebot.chairplay.media.apac

import com.shilapi.xcertplay.airplay.AirPlaySession
import com.shilapi.xcertplay.airplay.MediaSink
import java.io.Closeable
import java.io.IOException
import io.github.numberonebot.chairplay.platform.Platforms
import java.util.concurrent.ConcurrentHashMap

/** Typed desktop seam: APAP access units never enter MediaSink.onAudioRtp. */
interface SpatialAudioSink { val spatialAudio: SpatialAudioBridge? }

class SpatialAudioBridge internal constructor(
    private val plan: () -> SpatialPlan,
    private val volume: () -> Float,
    private val duck: (Long) -> Float,
    private val resetDuck: () -> Unit,
    private val log: (String) -> Unit,
    private val open: (SpatialPlan) -> SpatialDevice = { Platforms.current.audio.spatial.open(it.device, it.channels) },
) : Closeable {
    private val workers = ConcurrentHashMap.newKeySet<SpatialWorker>()
    internal fun source(key: ByteArray, setup: SpatialSetup): ApapStreamSource {
        val selected = plan()
        resetDuck()
        val worker = SpatialWorker({ open(selected) },
            { SpatialDecoder(selected.library, it) }, volume, duck, log, setup.soundCheckTargets)
        workers.add(worker)
        return ApapStreamSource(key, worker, log, setup.audioFormat) { workers.remove(worker) }
    }
    internal fun fadeOut(start: Long, duration: Long) { workers.forEach { it.fadeOut(start, duration) } }
    override fun close() { workers.toList().forEach { it.close() }; workers.clear() }
}

internal fun spatialStreamSource(session: AirPlaySession, sink: MediaSink, stream: Map<String, Any?>,
    key: ByteArray): ApapStreamSource? {
    if (stream["packetFormatAPAP"] != true) {
        if (stream.containsKey("packetFormatAPAP") || stream.containsKey("audioFormatIndex"))
            throw IOException("Spatial transport requires APAP")
        return null
    }
    if (!session.spatialAudioEnabled) throw IOException("Spatial audio was not negotiated")
    val setup = spatialSetup(stream)
    val bridge = (sink as? SpatialAudioSink)?.spatialAudio ?: throw IOException("Spatial audio output unavailable")
    return bridge.source(key, setup)
}
