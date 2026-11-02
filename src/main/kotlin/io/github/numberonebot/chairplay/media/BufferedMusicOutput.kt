package io.github.numberonebot.chairplay.media

import com.shilapi.xcertplay.airplay.AudioFormat
import com.shilapi.xcertplay.airplay.BufferedAudioStream
import java.util.concurrent.LinkedBlockingQueue
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger

/**
 * Upstream owns the encrypted preload, pause/seek position, and one-second delivery lead. This worker
 * decodes that lead without realtime latency shedding or startup muting. The first sample waits for
 * upstream's audible anchor; the output device then paces PCM. No APAC or platform-specific codec is used.
 */
internal class BufferedMusicOutput(
    private val format: AudioFormat,
    private val lineFactory: PcmLineFactory,
    private val clock: () -> Long,
    private val volume: () -> Float,
    private val ducking: MediaDucking,
    private val log: (String) -> Unit,
    private val changed: () -> Unit,
) : AudioOutput {
    @Volatile override var active = true
        private set
    override val dropped = AtomicInteger()
    // More than upstream's one-second lead at every supported AAC rate. Never block its control lock.
    private val queue = LinkedBlockingQueue<ByteArray>(128)
    private val beginsAt = clock() + BufferedAudioStream.START_LATENCY_MILLIS * 1_000_000
    private val lineLock = Any()
    private var line: PcmLine? = null
    private val thread = Thread(::run, "audio-media-buffered").apply { isDaemon = true }
    @Volatile private var fadeStartNs = NO_FADE
    @Volatile private var fadeNs = 0L

    override fun start() = thread.start()

    override fun push(rtp: ByteArray, sample: Int) {
        if (!active) return
        if (!queue.offer(rtp)) {
            // A stuck device must not silently skip music, grow memory, or block pause/teardown.
            log("audio media: buffered output stalled; reconnect to resume")
            close()
        }
    }

    override fun fadeOut(startNs: Long, durationNs: Long) {
        fadeNs = durationNs
        fadeStartNs = startNs
    }

    private fun run() {
        var decoder: PcmDecoder? = null
        try {
            decoder = pcmDecoder(format)
            val frameBytes = format.channels * 2
            val opened = lineFactory.open(format.sampleRate, format.channels,
                format.sampleRate * frameBytes * AudioRenderer.LINE_HEADROOM_MS / 1000)
            synchronized(lineLock) {
                if (!active) { opened.close(); return }
                line = opened
            }
            var started = false
            while (active) {
                val packet = queue.poll(100, TimeUnit.MILLISECONDS) ?: continue
                val pcm = decoder.decode(audioPayload(packet, format.codec))
                if (pcm.isEmpty()) continue
                while (active && clock() < beginsAt) Thread.sleep(5)
                if (!active) break
                val now = clock()
                val end = now + pcm.size * 1_000_000_000L / (format.sampleRate * frameBytes)
                val gain = volume()
                val from = gain * ducking.gain(now) * fadeGain(fadeStartNs, now, fadeNs)
                val to = gain * ducking.gain(end) * fadeGain(fadeStartNs, end, fadeNs)
                if (from < 1f || to < 1f) rampSamples(pcm, pcm.size, format.channels, from, to)
                synchronized(lineLock) {
                    if (!active) return
                    if (!started) { opened.start(); started = true }
                }
                opened.write(pcm, pcm.size)
            }
        } catch (_: InterruptedException) {
            // Pause, flush, or teardown: discard the old output; upstream retains unplayed frames.
        } catch (error: Exception) {
            if (active) log("audio media: buffered output stopped after ${error.javaClass.simpleName}: ${error.message}")
        } finally {
            active = false
            closeLine()
            queue.clear()
            runCatching { decoder?.close() }
            changed()
        }
    }

    private fun closeLine() {
        val opened = synchronized(lineLock) { line.also { line = null } }
        runCatching { opened?.close() }
    }

    override fun close() {
        active = false
        // Java Sound write may ignore interruption. Stop and flush it before joining the worker.
        closeLine()
        thread.interrupt()
        if (Thread.currentThread() !== thread) thread.join(AudioRenderer.JOIN_MILLIS)
        queue.clear()
        changed()
    }
}
