package io.github.numberonebot.chairplay.media.apac

import java.io.Closeable
import java.io.IOException
import java.util.ArrayDeque
import java.util.concurrent.LinkedBlockingQueue
import java.util.concurrent.TimeUnit

internal interface SpatialDevice : Closeable {
    val channels: Int
    val description: String get() = "$channels channels"
    fun write(samples: ShortArray)
    fun playedFrames(): Long
    fun cancel()
}

internal interface SpatialPacketDecoder : Closeable {
    fun decode(unit: ApapAccessUnit): SpatialDecoded
    fun discontinuity()
}

/** One decoder owner for the session. A pause replays cached PCM, never re-decodes prefetched AUs. */
internal class SpatialWorker(
    private val open: () -> SpatialDevice,
    private val decoderFactory: (Int) -> SpatialPacketDecoder,
    private val volume: () -> Float,
    private val duck: (Long) -> Float,
    private val log: (String) -> Unit,
    private val targets: Map<Int, Float>,
    private val clock: () -> Long = System::nanoTime,
    private val startDelayNs: Long = 400_000_000L,
) : Closeable {
    private data class Delivery(val generation: Long, val unit: ApapAccessUnit)
    private data class Audible(val start: Long, val frames: Int, val time: Long, val multichannelOutput: String?)
    private val lock = Object()
    private val queue = LinkedBlockingQueue<Delivery>(128)
    @Volatile private var generation = 0L
    @Volatile private var acknowledged = 0L
    @Volatile private var active = true
    @Volatile private var failure: Exception? = null
    @Volatile private var beginsAt = 0L
    @Volatile private var cutoff: Long? = null
    @Volatile private var device: SpatialDevice? = null
    @Volatile var position: Long? = null
        private set
    @Volatile var multichannelOutput: String? = null
        private set
    @Volatile private var devicePending = 0L
    @Volatile var cleanupComplete = false
        private set
    @Volatile private var fadeAt = Long.MAX_VALUE
    @Volatile private var fadeDuration = 0L
    val leadMillis get() = (queue.size * 1024L + devicePending) * 1000 / 48000
    private val thread = Thread(::run, "audio-media-spatial").apply { isDaemon = true; start() }

    fun begin(from: Long?) {
        checkUsable()
        cutoff = from
        beginsAt = clock() + startDelayNs
        position = from
    }

    fun push(unit: ApapAccessUnit) {
        checkUsable()
        if (!queue.offer(Delivery(generation, unit))) throw IOException("Spatial output stalled")
    }

    fun fadeOut(start: Long, duration: Long) { fadeDuration = duration; fadeAt = start }

    fun pause() {
        synchronized(lock) {
            generation++
            queue.clear()
            device?.cancel()
            val deadline = System.nanoTime() + 2_000_000_000L
            while (active && acknowledged != generation && System.nanoTime() < deadline) lock.wait(20)
            if (active && acknowledged != generation) throw IOException("Spatial output did not stop")
        }
    }

    private fun checkUsable() {
        failure?.let { throw IOException("Spatial playback stopped", it) }
        if (!active) throw IOException("Spatial playback is closed")
    }

    private fun run() {
        var decoder: SpatialPacketDecoder? = null
        var opened: SpatialDevice? = null
        var epoch = 0L
        var written = 0L
        var lastDecoded: Long? = null
        var lastCodec: SpatialCodec? = null
        var lastGain: Float? = null
        var rendered = 0L
        var nextReport = 480_000L
        val audible = ArrayDeque<Audible>()
        val cache = object : LinkedHashMap<ApapAccessUnit, SpatialDecoded>(128, .75f, true) {
            override fun removeEldestEntry(eldest: MutableMap.MutableEntry<ApapAccessUnit, SpatialDecoded>?) = size > 128
        }
        fun refreshPosition() {
            val played = opened?.playedFrames()?.coerceIn(0, written) ?: return
            while (audible.isNotEmpty()) {
                val block = audible.first
                if (played <= block.start) break
                val used = minOf(block.frames.toLong(), played - block.start)
                multichannelOutput = block.multichannelOutput
                position = (block.time + used) and 0xffffffffL
                if (used < block.frames) break
                audible.removeFirst()
            }
            devicePending = written - played
        }
        try {
            while (active) {
                if (epoch != generation) {
                    synchronized(lock) { device = null }
                    opened?.close()
                    opened = null
                    audible.clear()
                    written = 0
                    devicePending = 0
                    multichannelOutput = null
                    epoch = generation
                    synchronized(lock) { acknowledged = epoch; lock.notifyAll() }
                }
                refreshPosition()
                val next = queue.poll(10, TimeUnit.MILLISECONDS) ?: continue
                if (next.generation != epoch || generation != epoch) continue
                if (opened == null) {
                    val candidate = open()
                    synchronized(lock) { device = candidate }
                    opened = candidate
                    if (decoder == null) decoder = decoderFactory(candidate.channels)
                }
                val output = opened
                val unit = next.unit
                val time = unit.time.rtpTime()
                val decoded = cache[unit] ?: run {
                    if (lastDecoded?.let { (it + 1024) and 0xffffffffL != time } == true) decoder!!.discontinuity()
                    decoder!!.decode(unit).also { cache[unit] = it; lastDecoded = time }
                }
                var skip = 0
                cutoff?.let { at ->
                    val delta = (at - time) and 0xffffffffL
                    if (delta < 0x80000000L) skip = (delta - decoded.startTrimFrames).coerceIn(0, 1024).toInt()
                }
                val start = minOf(decoded.samples.size, skip * output.channels)
                val pcm = decoded.samples.copyOfRange(start, decoded.samples.size)
                if (pcm.isEmpty()) continue
                if (lastCodec != decoded.codec) {
                    lastCodec = decoded.codec
                    log("Spatial audio: input=${decoded.codec} 48000 Hz ${decoded.inputChannels} channels; " +
                        "output=WASAPI ${output.description}${if (output.channels == 2 && decoded.inputChannels == 6) " stereo downmix" else ""}")
                }
                while (active && epoch == generation && clock() < beginsAt) Thread.sleep(5)
                if (!active || epoch != generation) continue
                val frames = pcm.size / output.channels
                val now = clock()
                val end = now + frames * 1_000_000_000L / 48000
                val metadataGain = spatialSoundCheck(targets[unit.metadata.mediaKind], unit.metadata.loudness).toFloat()
                val fromGain = lastGain ?: metadataGain
                lastGain = metadataGain
                fun gain(at: Long) = volume() * duck(at) * if (at <= fadeAt) 1f else
                    (1.0 - (at - fadeAt).toDouble() / maxOf(1L, fadeDuration)).coerceIn(0.0, 1.0).toFloat()
                spatialGain(pcm, output.channels, fromGain * gain(now), metadataGain * gain(end))
                try { output.write(pcm) } catch (error: IOException) {
                    if (epoch != generation || !active) continue else throw error
                }
                val multichannel = if (decoded.codec != SpatialCodec.APAC) null else
                    if (output.channels == 6) "surround" else "downmix"
                audible.addLast(Audible(written, frames, (time + decoded.startTrimFrames + skip) and 0xffffffffL, multichannel))
                written += frames
                rendered += frames
                if (rendered >= nextReport) {
                    log("Spatial audio: rendered=$rendered frames input=$lastCodec output=${output.description}")
                    nextReport = rendered + 480_000
                }
                refreshPosition()
            }
        } catch (_: InterruptedException) {
        } catch (error: Exception) {
            failure = error
            if (active) log("Spatial audio stopped: ${error.message}")
        } finally {
            active = false
            multichannelOutput = null
            synchronized(lock) { device = null }
            runCatching { opened?.close() }
            runCatching { decoder?.close() }
            queue.clear()
            cache.clear()
            cleanupComplete = true
            synchronized(lock) { lock.notifyAll() }
        }
    }

    override fun close() {
        active = false
        synchronized(lock) { device?.cancel() }
        thread.interrupt()
        if (Thread.currentThread() !== thread) thread.join(2000)
    }
}
