package io.github.numberonebot.chairplay.media

import com.shilapi.xcertplay.airplay.AudioFormat
import com.shilapi.xcertplay.airplay.AudioStreamId
import com.shilapi.xcertplay.airplay.MediaSink
import com.shilapi.xcertplay.airplay.MicrophoneConfig
import com.shilapi.xcertplay.airplay.VideoCodec
import com.shilapi.xcertplay.media.EchoReference
import io.github.numberonebot.chairplay.bridge.FrameTap
import java.io.Closeable
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicBoolean
import javax.sound.sampled.Mixer

/**
 * Shows the main CarPlay screen in a [VideoWindow], and the altscreen (the car's instrument cluster) in
 * [altscreen] when there is one. Each screen stream's thread decodes and hands each picture to its window,
 * which draws it on the GL thread.
 *
 * Audio goes to [audio], which decodes and plays each stream on its own thread. A microphone stream
 * gets one [MicrophoneSender] capturing [microphone] until the iPhone stops it. With [echoCancellation], a call's
 * audio stream records what it plays and the call's microphone stream cancels it, as AndroidMediaSink does. The music
 * ducks under navigation prompts when the iPhone asks ([onAudioDuck]).
 */
class DesktopMediaSink(
    private val window: VideoWindow,
    private val audio: AudioRenderer,
    private val log: (String) -> Unit = {},
    /** The microphone (audio.input), or the Windows default recording device when it is null. */
    private val microphone: Mixer.Info? = null,
    /** audio.echo.cancellation: run SpeexDSP's echo canceller on calls. */
    private val echoCancellation: Boolean = false,
    /** altscreen.enabled: the window for the cluster stream; its frames are ignored without one. */
    private val altscreen: VideoWindow? = null,
    /** audio.duck.*: how low a navigation prompt puts the music, read at each prompt. */
    private val duckSetting: () -> DuckSetting = { DuckSetting() },
    private val hardwareDecoding: () -> Boolean = { true },
    /** display.share: sees each picture before its window takes it, to publish the main screen for games. */
    private val frameTap: FrameTap = FrameTap.NONE,
) : MediaSink, AudioDuckSink, BufferedAudioStatusSink, io.github.numberonebot.chairplay.media.apac.SpatialAudioSink, Closeable {
    override val spatialAudio get() = audio.spatialAudio
    val bufferedAudio = BufferedAudioMonitor()
    override fun bufferedAudioOpened(owner: Any) = bufferedAudio.bufferedAudioOpened(owner)
    override fun bufferedAudioUpdated(owner: Any, status: BufferedAudioStatus) = bufferedAudio.bufferedAudioUpdated(owner, status)
    override fun bufferedAudioClosed(owner: Any) = bufferedAudio.bufferedAudioClosed(owner)
    private val diagnosticHandlers = ConcurrentHashMap<Int, (String) -> Unit>()
    private val recoveryHandlers = ConcurrentHashMap<Int, () -> Unit>()
    // The handler sends a command, and a failed send closes the session, which stops this stream and
    // closes the decoder. Run inside decode() that would free the decoder under its own feet, so the
    // request is posted away from the decoder's call path (as AndroidMediaSink does).
    private val recoveryExecutor = Executors.newSingleThreadExecutor { Thread(it, "video-recovery").apply { isDaemon = true } }
    private val microphones = ConcurrentHashMap<AudioStreamId, MicrophoneSender>()
    private val callEchoReferences = ConcurrentHashMap<AudioStreamId, EchoReference>()
    /** Runs on the AltScreen window's thread when the first picture of an AltScreen stream newly set up is on screen. */
    @Volatile
    var onAltscreenStream: () -> Unit = {}
    private val altscreenSetUp = AtomicBoolean()

    private val screens: Map<Int, Screen> = buildMap {
        put(MAIN_SCREEN, Screen(MAIN_SCREEN, window))
        if (altscreen != null) put(ALT_SCREEN, Screen(ALT_SCREEN, altscreen))
    }

    /** One screen stream's decoder and window. */
    private inner class Screen(private val type: Int, private val window: VideoWindow) {
        private val lock = Any()
        private var decoder: VideoDecoder? = null
        private var codec = VideoCodec.H264
        /** The running decoder's configuration; a new codec or a stop closes the decoder, so it counts only with one. */
        private var configured: ByteArray? = null
        private var lastRecoveryNanos = 0L
        private val firstFramePending = AtomicBoolean()

        init {
            window.onFrameShown = {
                if (firstFramePending.compareAndSet(true, false)) {
                    diagnosticHandlers[type]?.invoke(FIRST_FRAME)
                }
                // Once per stream set up, not per decoder configuration: the iPhone may configure one again as asked
                // for a view, and the view asked for again would loop.
                if (type == ALT_SCREEN && altscreenSetUp.compareAndSet(true, false)) {
                    onAltscreenStream()
                }
            }
        }

        fun selectCodec(value: VideoCodec) = synchronized(lock) {
            window.titles.codec(value)
            if (codec != value) {
                decoder?.close()
                decoder = null
                codec = value
            }
        }

        /**
         * A configuration starts a stream, so it starts a fresh decoder and awaits that stream's first picture. The same
         * one again keeps the decoder: at 1920x1080 the iPhone can repeat it right after the keyframe and send no new
         * keyframe, which a fresh decoder would wait for (AndroidMediaSink keeps its decoder too).
         */
        fun configure(codecData: ByteArray) = synchronized(lock) {
            if (decoder != null && configured.contentEquals(codecData)) {
                log("video screen $type: configuration unchanged; keeping the decoder")
                return
            }
            configured = codecData
            decoder?.close()
            // The window releases the picture once drawn, so the tap reads it first.
            decoder = VideoDecoder({ frame -> frameTap.frame(type, frame); window.show(frame) }, ::recover, codec, { log("video screen $type: $it") }, hardwareDecoding())
                .also { it.configure(codecData) }
            firstFramePending.set(true)
        }

        fun decode(nalus: ByteArray) = synchronized(lock) { decoder?.decode(nalus) }

        fun stop() = synchronized(lock) {
            decoder?.close()
            decoder = null
            window.titles.codec(null)
        }

        /** The decoder rejected a packet: ask the iPhone for a keyframe of this screen, at most once a second. */
        private fun recover(reason: String) {
            log("video decoder ($type): $reason")
            requestKeyframe()
        }

        /** Asks the iPhone for a keyframe of this screen through the recovery handler, at most once a second. */
        fun requestKeyframe() {
            if (recoveryExecutor.isShutdown) return
            val now = System.nanoTime()
            if (now - lastRecoveryNanos < RECOVERY_INTERVAL_NANOS && lastRecoveryNanos != 0L) return
            lastRecoveryNanos = now
            recoveryExecutor.execute { recoveryHandlers[type]?.invoke() }
        }
    }

    init {
        frameTap.attach { screens[MAIN_SCREEN]?.requestKeyframe() }
    }

    override fun onVideoCodec(type: Int, codec: VideoCodec) {
        val screen = screens[type] ?: return
        screen.selectCodec(codec)
        log("video codec $codec of screen $type")
    }

    override fun onVideoConfig(type: Int, codecData: ByteArray) {
        val screen = screens[type] ?: return
        screen.configure(codecData)
    }

    override fun onVideoFrame(type: Int, naluBytes: ByteArray, senderNanos: Long, arrivalNanos: Long) {
        val screen = screens[type] ?: return
        screen.decode(naluBytes)
    }

    override fun setVideoDiagnosticHandler(type: Int, handler: (String) -> Unit) {
        diagnosticHandlers[type] = handler
    }

    override fun setVideoRecoveryHandler(type: Int, handler: () -> Unit) {
        recoveryHandlers[type] = handler
    }

    override fun onScreenStreamActive(type: Int, active: Boolean) {
        if (active && type == ALT_SCREEN) altscreenSetUp.set(true)
        if (active) return frameTap.active(type, true)
        val screen = screens[type] ?: return
        diagnosticHandlers.remove(type)
        recoveryHandlers.remove(type)
        screen.stop()
        // After the decoder closed, so no picture of this stream follows the stop.
        frameTap.active(type, false)
    }

    override fun onAudioStarted(id: AudioStreamId, format: AudioFormat, firstSample: Int) {
        // A restarted stream gets a fresh reference, as AndroidMediaSink gives each renderer its own ring.
        val echoReference = if (echoCancellation && format.audioType == TELEPHONY_AUDIO_TYPE && format.sampleRate > 0) {
            EchoReference(format.sampleRate).also { callEchoReferences[id] = it }
        } else {
            callEchoReferences.remove(id)
            null
        }
        audio.start(id, format, echoReference)
    }

    override fun onAudioRtp(id: AudioStreamId, format: AudioFormat, rtp: ByteArray, sample: Int) =
        audio.push(id, rtp, sample)

    override fun onAudioStopped(id: AudioStreamId) {
        audio.stop(id)
        callEchoReferences.remove(id)
    }

    // The engine calls this on the downlink thread: a missing device only costs this stream its uplink.
    override fun onMicrophoneStarted(id: AudioStreamId, config: MicrophoneConfig) {
        microphones.remove(id)?.close()
        val echoReference = if (config.audioType == TELEPHONY_AUDIO_TYPE) callEchoReferences[id] else null
        val sender = openMicrophoneSender(config, log, microphone, echoReference) ?: return
        microphones[id] = sender
        sender.start()
    }

    override fun onMicrophoneStopped(id: AudioStreamId) {
        microphones.remove(id)?.close()
    }

    /** duckAudio and unduckAudio: the music makes way for a navigation prompt, as long as the prompt lasts. */
    override fun onAudioDuck(ducked: Boolean, params: Map<String, Any?>) {
        val request = duckRequest(params, duckSetting())
        if (ducked) audio.duck(request.level, request.rampMillis) else audio.unduck(request.rampMillis)
    }

    /**
     * Fades the sound out as long as the music fades in, for a reconnect or a close: the graceful end of the session
     * waits seconds for the iPhone. The next session's streams play at full volume.
     */
    fun fadeOut() = audio.fadeOut()

    override fun close() {
        screens.values.forEach { it.stop() }
        (frameTap as? Closeable)?.close()
        recoveryExecutor.shutdownNow()
        microphones.keys.toList().forEach(::onMicrophoneStopped)
        callEchoReferences.clear()
        audio.close()
        window.close()
        altscreen?.close()
    }

    internal companion object {
        // The screen stream types CarPlayMediaEngine uses for the main display and the altscreen (cluster).
        const val MAIN_SCREEN = 110
        const val ALT_SCREEN = 111
        private const val FIRST_FRAME = "first frame rendered"
        private const val RECOVERY_INTERVAL_NANOS = 1_000_000_000L
        private const val TELEPHONY_AUDIO_TYPE = "telephony"
    }
}
