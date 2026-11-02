package io.github.numberonebot.chairplay.media

import com.shilapi.xcertplay.airplay.AudioFormat
import com.shilapi.xcertplay.airplay.AudioStreamId
import com.shilapi.xcertplay.airplay.BufferedAudioStream
import com.shilapi.xcertplay.media.EchoReference
import java.io.Closeable
import java.util.concurrent.ConcurrentHashMap
import javax.sound.sampled.Mixer

/**
 * Plays CarPlay audio streams through Java Sound, one line per [AudioStreamId]; Windows mixes the lines.
 *
 * [RealtimeAudioOutput] keeps latency low for ordinary music, calls, Siri, and navigation prompts.
 * Type 103 uses [BufferedMusicOutput], which preserves prefetched music and follows upstream's start anchor.
 * Each output owns a decoder and a device writer; both share the app's volume and navigation ducking.
 */
class AudioRenderer internal constructor(
    private val log: (String) -> Unit = {},
    private val lineFactory: PcmLineFactory = javaSoundLines(null),
    private val clock: () -> Long = System::nanoTime,
    private val pollMillis: Long = POLL_MILLIS,
    /** The app's volume ([Volume.gain]), read for every block written. */
    private val volume: () -> Float = { 1f },
    /** How a music stream starts, read as each one starts playing. */
    private val startFade: () -> StartFade = { StartFade.NONE },
    private val changed: () -> Unit = {},
) : Closeable {
    /** Plays on [output], or on the Windows default output when it is null (audio.output), at [volume]. */
    constructor(
        log: (String) -> Unit,
        output: Mixer.Info? = null,
        volume: () -> Float = { 1f },
        startFade: () -> StartFade = { StartFade.NONE },
        changed: () -> Unit = {},
    ) : this(log, javaSoundLines(output), volume = volume, startFade = startFade, changed = changed)

    private val streams = ConcurrentHashMap<AudioStreamId, AudioOutput>()
    private val ducking = MediaDucking(log)

    /** [echoReference], for a call, receives what this stream plays, for the microphone's echo canceller. */
    internal fun start(id: AudioStreamId, format: AudioFormat, echoReference: EchoReference? = null) {
        streams.remove(id)?.close()
        endDuck(id, "started")
        val supported = format.channels in 1..2 && format.sampleRate > 0
        if (!supported) {
            log("audio ${id.audioType}: ${format.codec} with ${format.channels} channels at ${format.sampleRate} Hz is not supported; dropped")
            return
        }
        val output = if (id.type == BufferedAudioStream.STREAM_TYPE) {
            BufferedMusicOutput(format, lineFactory, clock, volume, ducking, log, changed)
        } else RealtimeAudioOutput(id, format, echoReference, log, lineFactory, clock, pollMillis, volume, startFade, ducking, changed)
        streams[id] = output
        output.start()
        changed()
    }

    fun push(id: AudioStreamId, rtp: ByteArray, sample: Int) {
        streams[id]?.push(rtp, sample)
    }

    /**
     * Fades every playing stream out over [durationMillis], for a reconnect or a close that ends the session
     * gracefully, which takes seconds. Streams started afterwards, by the next session, play at full volume. By default
     * the sound fades out as long as the music fades in (audio.start.fade.ms).
     */
    fun fadeOut(durationMillis: Long = startFade().fadeMillis) {
        val now = clock()
        streams.values.forEach { it.fadeOut(now, durationMillis * 1_000_000) }
    }

    /**
     * duckAudio: lowers the media streams to [level] over [rampMillis] while a navigation prompt plays; the prompt, calls
     * and Siri keep their volume. A media stream that starts or stops meanwhile ends the duck, so no music starts ducked.
     */
    fun duck(level: Float, rampMillis: Long) {
        ducking.duck(level, rampMillis * 1_000_000, clock())
        log("audio media: ducked to $level over $rampMillis ms")
    }

    /** unduckAudio: the media streams back to full volume over [rampMillis]. */
    fun unduck(rampMillis: Long) {
        ducking.unduck(rampMillis * 1_000_000, clock())
        log("audio media: unducked over $rampMillis ms")
    }

    internal val ducked get() = ducking.ducked

    private fun endDuck(id: AudioStreamId, what: String) {
        if (id.audioType == MEDIA_AUDIO_TYPE && ducking.reset()) log("audio media: stream $what while ducked; back to full volume")
    }

    fun stop(id: AudioStreamId) {
        streams.remove(id)?.close()
        endDuck(id, "stopped")
        changed()
    }

    /** Actual music output, independent of the mode requested for the next connection. */
    fun musicStream(): String {
        val ids = streams.entries.filter { it.key.audioType == MEDIA_AUDIO_TYPE && it.value.active }.map { it.key }
        return when {
            ids.any { it.type == BufferedAudioStream.STREAM_TYPE } -> "buffered"
            ids.isNotEmpty() -> "standard"
            else -> "none"
        }
    }

    /** Packets [id] refused as older than its playhead or because its queue was full. */
    internal fun droppedPackets(id: AudioStreamId) = streams[id]?.dropped?.get() ?: 0

    override fun close() {
        streams.keys.toList().forEach(::stop)
    }

    internal companion object {
        // The audioType of CarPlay's music streams, the only ones a duck lowers.
        const val MEDIA_AUDIO_TYPE = "media"
        const val BUFFER_TAIL_WAIT_NS = 500_000_000L
        const val SHED_ABOVE_NS = 1_500_000_000L
        // MediaPacketLagGuard.MAX_VALID_SPAN_SECONDS: a bigger jump is the iPhone restarting its clock.
        const val MAX_VALID_SPAN_NS = 16_000_000_000L
        const val SHED_TO_NS = 500_000_000L
        const val DIAGNOSTIC_INTERVAL_NS = 10_000_000_000L
        // A stream's first seconds, whose underruns are each logged with their time.
        const val EARLY_MILLIS = 10_000L
        const val MEDIA_JITTER_MS = 60
        const val JITTER_MS = 40
        const val LINE_HEADROOM_MS = 140
        const val MAX_QUEUED_PACKETS = 256
        const val POLL_MILLIS = 100L
        const val JOIN_MILLIS = 2000L
        // A timestamp further behind than this is the iPhone restarting its clock at a track boundary.
        const val RESTART_SECONDS = 2
    }
}
