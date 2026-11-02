package io.github.numberonebot.chairplay.media

import com.shilapi.xcertplay.airplay.AudioFormat
import com.shilapi.xcertplay.airplay.AudioStreamId
import com.shilapi.xcertplay.media.EchoReference
import java.util.concurrent.LinkedBlockingQueue
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger

import io.github.numberonebot.chairplay.media.AudioRenderer.Companion.BUFFER_TAIL_WAIT_NS
import io.github.numberonebot.chairplay.media.AudioRenderer.Companion.SHED_ABOVE_NS
import io.github.numberonebot.chairplay.media.AudioRenderer.Companion.SHED_TO_NS
import io.github.numberonebot.chairplay.media.AudioRenderer.Companion.DIAGNOSTIC_INTERVAL_NS
import io.github.numberonebot.chairplay.media.AudioRenderer.Companion.MEDIA_AUDIO_TYPE
import io.github.numberonebot.chairplay.media.AudioRenderer.Companion.MAX_QUEUED_PACKETS
import io.github.numberonebot.chairplay.media.AudioRenderer.Companion.MEDIA_JITTER_MS
import io.github.numberonebot.chairplay.media.AudioRenderer.Companion.JITTER_MS
import io.github.numberonebot.chairplay.media.AudioRenderer.Companion.RESTART_SECONDS
import io.github.numberonebot.chairplay.media.AudioRenderer.Companion.MAX_VALID_SPAN_NS
import io.github.numberonebot.chairplay.media.AudioRenderer.Companion.LINE_HEADROOM_MS
import io.github.numberonebot.chairplay.media.AudioRenderer.Companion.EARLY_MILLIS
import io.github.numberonebot.chairplay.media.AudioRenderer.Companion.JOIN_MILLIS

/** The existing low-latency output for music, calls, Siri, and prompts. */
internal class RealtimeAudioOutput(
    private val id: AudioStreamId,
    private val format: AudioFormat,
    private val echoReference: EchoReference?,
    private val log: (String) -> Unit,
    private val lineFactory: PcmLineFactory,
    private val clock: () -> Long,
    private val pollMillis: Long,
    private val volume: () -> Float,
    private val startFade: () -> StartFade,
    private val ducking: MediaDucking,
    private val changed: () -> Unit,
) : AudioOutput {
    override val dropped = AtomicInteger()
    private val counts = HashMap<String, Int>()
    private val lastLogged = HashMap<String, Long>()
    private val thread = Thread(::run, "audio-${id.audioType}-${id.type}").apply { isDaemon = true }
    private val queue = LinkedBlockingQueue<QueuedPacket>(MAX_QUEUED_PACKETS)
    @Volatile override var active = true
        private set
    private val running get() = active
    override fun start() = thread.start()
    private var playhead: Int? = null
    private val frameBytes = 2 * format.channels
    @Volatile private var fadeStartNs = NO_FADE
    @Volatile private var fadeNs = 0L

    override fun fadeOut(startNs: Long, durationNs: Long) {
        fadeNs = durationNs
        fadeStartNs = startNs
    }
    private val media = id.audioType == MEDIA_AUDIO_TYPE
    private val jitterBytes =
        format.sampleRate * frameBytes * (if (media) MEDIA_JITTER_MS else JITTER_MS) / 1000

    /** Stream thread. Newer than or equal to the playhead is accepted; the iPhone's clock restarting is not "late". */
    override fun push(rtp: ByteArray, sample: Int) {
        val head = playhead
        val behind = if (head == null) 0 else head - sample
        if (behind in 1..format.rtpClockRate * RESTART_SECONDS) {
            dropped.incrementAndGet()
            note("late packets dropped")
            return
        }
        playhead = sample
        if (!queue.offer(QueuedPacket(rtp, sample))) {
            dropped.incrementAndGet()
            note("packets dropped, queue full")
        }
        shedExcess(sample)
    }

    /** How long [bytes] of this stream's PCM play. */
    private fun nanosOf(bytes: Int) = bytes * 1_000_000_000L / (format.sampleRate * frameBytes)

    private fun spanNanos(oldest: Int, newest: Int) =
        (newest - oldest).toLong() * 1_000_000_000L / format.rtpClockRate

    /** Stream thread. A burst after a stall would otherwise stay queued as latency for good. */
    private fun shedExcess(newest: Int) {
        val oldest = queue.peek() ?: return
        if (spanNanos(oldest.sample, newest) !in SHED_ABOVE_NS..MAX_VALID_SPAN_NS) return
        var shed = 0
        while (queue.peek()?.let { spanNanos(it.sample, newest) > SHED_TO_NS } == true && queue.poll() != null) shed++
        dropped.addAndGet(shed)
        note("queued audio shed to ${SHED_TO_NS / 1_000_000} ms", shed)
    }

    /** Counts an event of one class; the first and then one per interval reach the log. */
    @Synchronized private fun note(what: String, n: Int = 1) {
        val total = (counts[what] ?: 0) + n
        counts[what] = total
        val now = clock()
        val last = lastLogged[what]
        if (last != null && now - last < DIAGNOSTIC_INTERVAL_NS) return
        lastLogged[what] = now
        log("audio ${id.audioType}: $what (total $total)")
    }

    private fun run() {
        var decoder: PcmDecoder? = null
        var line: PcmLine? = null
        try {
            decoder = pcmDecoder(format)
            val bufferBytes = jitterBytes + format.sampleRate * frameBytes * LINE_HEADROOM_MS / 1000
            val opened = lineFactory.open(format.sampleRate, format.channels, bufferBytes)
            line = opened
            val held = java.io.ByteArrayOutputStream()
            var playing = false
            // When the audio already written has all been played, by the clock rather than the line.
            var playedUntil = 0L
            var lastPacketAt = clock()
            var writtenFrames = 0L
            // When the stream first played, and how music starts from there (silent, then rising); a prompt plays at once.
            var startedAt: Long? = null
            var start = StartFade.NONE
            fun play(pcm: ByteArray, length: Int) {
                val now = clock()
                val began = startedAt ?: now.also {
                    startedAt = it
                    if (media) start = startFade()
                }
                val gain = fadeGain(fadeStartNs, now, fadeNs) * volume()
                // A duck's ramp, and the start's, run through the block: their gains now and where it will have ended.
                val end = now + nanosOf(length)
                val startGain = (if (media) gain * ducking.gain(now) else gain) * start.gain(now - began)
                val endGain = (if (media) gain * ducking.gain(end) else gain) * start.gain(end - began)
                if (startGain < 1f || endGain < 1f) rampSamples(pcm, length, format.channels, startGain, endGain)
                opened.start()
                opened.write(pcm, length)
                writtenFrames += length / frameBytes
                // AndroidMediaSink's call renderer: the block and how much is still queued ahead of the speaker.
                echoReference?.let { reference ->
                    val pending = opened.playedFrames()?.let { writtenFrames - it }
                    reference.append(pcm, 0, length, format.channels, pending, clock())
                }
                playedUntil = maxOf(playedUntil, clock()) + nanosOf(length)
                playing = true
            }
            while (running) {
                val packet = queue.poll(pollMillis, TimeUnit.MILLISECONDS)
                val now = clock()
                if (playing && now > playedUntil) {
                    playing = false
                    note("underrun, rebuffering")
                    // Early ones each, by when they come: what a music stream's silent start has to cover.
                    startedAt?.let { (now - it) / 1_000_000 }?.takeIf { it < EARLY_MILLIS }?.let {
                        log("audio ${id.audioType}: underrun $it ms after the stream started")
                    }
                }
                if (packet == null) {
                    // A short final burst may never reach the threshold; play it after a bounded wait.
                    if (!playing && held.size() > 0 && now - lastPacketAt >= BUFFER_TAIL_WAIT_NS) {
                        play(held.toByteArray(), held.size())
                        held.reset()
                    }
                    continue
                }
                lastPacketAt = now
                val pcm = try {
                    decoder.decode(audioPayload(packet.rtp, format.codec))
                } catch (_: Exception) {
                    // One corrupt packet costs its own audio only; the next one decodes on its own.
                    note("decode errors")
                    continue
                }
                if (pcm.isEmpty()) continue
                if (playing) {
                    play(pcm, pcm.size)
                    continue
                }
                held.write(pcm)
                if (held.size() >= jitterBytes) {
                    play(held.toByteArray(), held.size())
                    held.reset()
                }
            }
        } catch (_: InterruptedException) {
            // stop() or close().
        } catch (error: Exception) {
            // A decoder that cannot be created or a device failure silences this stream only; the
            // others keep playing.
            if (running) log("audio ${id.audioType}: stopped after ${error.javaClass.simpleName}: ${error.message}")
        } finally {
            active = false
            changed()
            runCatching { line?.close() }
            runCatching { decoder?.close() }
        }
    }

    override fun close() {
        active = false
        thread.interrupt()
        thread.join(JOIN_MILLIS)
    }
}

private class QueuedPacket(val rtp: ByteArray, val sample: Int)

