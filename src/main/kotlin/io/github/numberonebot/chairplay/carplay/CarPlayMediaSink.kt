package io.github.numberonebot.chairplay.carplay

import com.shilapi.xcertplay.airplay.AudioFormat
import com.shilapi.xcertplay.airplay.AudioStreamId
import com.shilapi.xcertplay.airplay.MediaSink
import com.shilapi.xcertplay.airplay.MicrophoneConfig
import com.shilapi.xcertplay.airplay.VideoCodec
import com.shilapi.xcertplay.media.EchoReference
import com.shilapi.xcertplay.media.MediaCodecSupport
import io.github.numberonebot.chairplay.media.AudioDuckSink
import io.github.numberonebot.chairplay.media.AudioRenderer
import io.github.numberonebot.chairplay.media.BufferedAudioMonitor
import io.github.numberonebot.chairplay.media.BufferedAudioStatus
import io.github.numberonebot.chairplay.media.BufferedAudioStatusSink
import io.github.numberonebot.chairplay.media.DuckSetting
import io.github.numberonebot.chairplay.media.MicrophoneSender
import io.github.numberonebot.chairplay.media.ScreenDecoders
import io.github.numberonebot.chairplay.media.apac.SpatialAudioSink
import io.github.numberonebot.chairplay.media.duckRequest
import io.github.numberonebot.chairplay.media.openMicrophoneSender
import io.github.numberonebot.chairplay.phone.ScreenKind
import io.github.numberonebot.chairplay.phone.VideoCodecKind
import java.io.Closeable
import java.util.concurrent.ConcurrentHashMap
import javax.sound.sampled.Mixer

/**
 * What CarPlay's media engine hands the car: its two screen streams go to [screens] (stream type 110 the main screen, 111
 * the AltScreen, the car's instrument cluster), each configuration made Annex B parameter sets first.
 *
 * Audio goes to [audio], which decodes and plays each stream on its own thread. A microphone stream
 * gets one [MicrophoneSender] capturing [microphone] until the iPhone stops it. With [echoCancellation], a call's
 * audio stream records what it plays and the call's microphone stream cancels it, as AndroidMediaSink does. The music
 * ducks under navigation prompts when the iPhone asks ([onAudioDuck]).
 */
class CarPlayMediaSink(
    private val screens: ScreenDecoders,
    private val audio: AudioRenderer,
    private val log: (String) -> Unit = {},
    /** The microphone (audio.input), or the Windows default recording device when it is null. */
    private val microphone: Mixer.Info? = null,
    /** audio.echo.cancellation: run SpeexDSP's echo canceller on calls. */
    private val echoCancellation: Boolean = false,
    /** audio.duck.*: how low a navigation prompt puts the music, read at each prompt. */
    private val duckSetting: () -> DuckSetting = { DuckSetting() },
) : MediaSink, AudioDuckSink, BufferedAudioStatusSink, SpatialAudioSink, Closeable {
    override val spatialAudio get() = audio.spatialAudio
    val bufferedAudio = BufferedAudioMonitor()
    override fun bufferedAudioOpened(owner: Any) = bufferedAudio.bufferedAudioOpened(owner)
    override fun bufferedAudioUpdated(owner: Any, status: BufferedAudioStatus) = bufferedAudio.bufferedAudioUpdated(owner, status)
    override fun bufferedAudioClosed(owner: Any) = bufferedAudio.bufferedAudioClosed(owner)
    private val microphones = ConcurrentHashMap<AudioStreamId, MicrophoneSender>()
    private val callEchoReferences = ConcurrentHashMap<AudioStreamId, EchoReference>()
    /** Each screen's codec, which tells how to read its configuration (avcC or hvcC). */
    private val codecs = ConcurrentHashMap<ScreenKind, VideoCodec>()

    override fun onVideoCodec(type: Int, codec: VideoCodec) {
        val screen = screen(type) ?: return
        codecs[screen] = codec
        screens.selectCodec(screen, if (codec == VideoCodec.H265) VideoCodecKind.H265 else VideoCodecKind.H264)
    }

    override fun onVideoConfig(type: Int, codecData: ByteArray) {
        val screen = screen(type) ?: return
        screens.configure(screen, annexBParameterSets(codecData, codecs[screen] ?: VideoCodec.H264))
    }

    // CarPlayMediaEngine hands the frames over as Annex B already.
    override fun onVideoFrame(type: Int, naluBytes: ByteArray, senderNanos: Long, arrivalNanos: Long) {
        screens.decode(screen(type) ?: return, naluBytes)
    }

    override fun setVideoDiagnosticHandler(type: Int, handler: (String) -> Unit) {
        screens.setDiagnosticHandler(screen(type) ?: return, handler)
    }

    override fun setVideoRecoveryHandler(type: Int, handler: () -> Unit) {
        screens.setRecoveryHandler(screen(type) ?: return, handler)
    }

    override fun onScreenStreamActive(type: Int, active: Boolean) {
        screens.active(screen(type) ?: return, active)
    }

    /** Asks the iPhone for a keyframe of [screen], as a decoder that rejected a packet does: at most once a second. */
    fun keyframe(screen: ScreenKind) = screens.requestKeyframe(screen)

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

    /** Stops the screens (and closes their windows), then the microphones and the sound. */
    override fun close() {
        screens.close()
        microphones.keys.toList().forEach(::onMicrophoneStopped)
        callEchoReferences.clear()
        audio.close()
    }

    private companion object {
        // The screen stream types CarPlayMediaEngine uses for the main display and the altscreen (cluster).
        const val MAIN_SCREEN = 110
        const val ALT_SCREEN = 111
        const val TELEPHONY_AUDIO_TYPE = "telephony"

        fun screen(type: Int): ScreenKind? = when (type) {
            MAIN_SCREEN -> ScreenKind.MAIN
            ALT_SCREEN -> ScreenKind.CLUSTER
            else -> null
        }
    }
}

private val START_CODE = byteArrayOf(0, 0, 0, 1)

/**
 * A CarPlay screen configuration ([codecData]: an AVCDecoderConfigurationRecord, avcC, or an HEVC one, hvcC) as the
 * Annex B parameter sets a decoder takes: H.264's first SPS and PPS, HEVC's VPS, SPS and PPS. Empty when it holds none.
 */
internal fun annexBParameterSets(codecData: ByteArray, codec: VideoCodec): ByteArray = when (codec) {
    VideoCodec.H264 -> {
        val (sps, pps) = MediaCodecSupport.avcParameterSets(codecData)
        if (sps.isEmpty() || pps.isEmpty()) ByteArray(0) else START_CODE + sps + START_CODE + pps
    }
    VideoCodec.H265 -> MediaCodecSupport.hevcCodecSpecificData(codecData)
}
