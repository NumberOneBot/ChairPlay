package io.github.numberonebot.chairplay

import io.github.numberonebot.chairplay.config.DesktopConfig
import io.github.numberonebot.chairplay.config.VolumeStore
import io.github.numberonebot.chairplay.media.AudioDevices
import io.github.numberonebot.chairplay.media.AudioRenderer
import io.github.numberonebot.chairplay.media.DesktopMediaSink
import io.github.numberonebot.chairplay.media.StartFade
import io.github.numberonebot.chairplay.media.VideoWindow
import io.github.numberonebot.chairplay.media.Volume
import java.nio.file.Path
import java.util.concurrent.atomic.AtomicReference

/**
 * The app's sound: the devices it plays through and listens on, its own volume, how the music starts and how low a
 * prompt puts it, and the sink that plays the iPhone's streams and shows its pictures in [window] and [altscreen].
 * [changed] tells the controls window's page that the volume changed.
 */
internal class AppAudio(dir: Path, config: DesktopConfig, window: VideoWindow, altscreen: VideoWindow, private val changed: () -> Unit) {
    /** The app's own volume (Ctrl+Up, Ctrl+Down, Ctrl+M), kept from the last run. */
    val volume: Volume

    // How the music starts, silent and then rising (and how long the sound fades out over), and how low a prompt puts it;
    // the Audio page changes both at once.
    private val musicStart = AtomicReference(StartFade(config.musicStartSilenceMillis, config.musicStartFadeMillis))
    private val duckSetting = AtomicReference(config.duckSetting)
    val sink: DesktopMediaSink
    private val renderer: AudioRenderer
    fun bufferedAudio(): Map<String, Any>? = sink.bufferedAudio.state()
    fun updateBufferedAudio() { if (sink.bufferedAudio.poll()) changed() }

    init {
        // A device name matching nothing stops here, before any connection, with the devices listed.
        val output = AudioDevices.output(config.audioOutput)
        val input = AudioDevices.input(config.audioInput)
        log("audio output: ${output?.name ?: "Windows default"}; microphone: ${input?.name ?: "Windows default"}")
        val volumeStore = VolumeStore(dir)
        volume = runCatching { volumeStore.load() }.getOrDefault(Volume.MAX to false).let { (level, muted) -> Volume(level, muted) }
        log("volume ${volume.level}%${if (volume.muted) ", muted" else ""}")
        volume.onChange = {
            log("volume ${volume.level}%${if (volume.muted) ", muted" else ""}")
            runCatching { volumeStore.save(volume.level, volume.muted) }.onFailure { log("volume not saved: $it") }
            changed()
        }
        renderer = AudioRenderer(::log, output, volume::gain, musicStart::get, changed)
        renderer.configureSpatial {
            io.github.numberonebot.chairplay.media.apac.SpatialRuntime.requirePlan(dir, config.audioOutput)
        }
        sink = DesktopMediaSink(window, renderer, ::log, input, config.echoCancellation, altscreen, duckSetting::get,
            hardwareDecoding = { config.now().hardwareVideo })
    }

    /** The Audio page saved [saved]: the music's start and the prompts' ducking take what the file reads now. */
    fun saved(saved: Map<String, String>, config: DesktopConfig) {
        if ("audio.start.silence.ms" in saved || "audio.start.fade.ms" in saved) {
            runCatching { config.now() }.onSuccess { now ->
                musicStart.set(StartFade(now.musicStartSilenceMillis, now.musicStartFadeMillis))
                log("music starts silent for ${now.musicStartSilenceMillis} ms, rising over ${now.musicStartFadeMillis} ms")
            }
        }
        if ("audio.duck.db" in saved || "audio.duck.iphone" in saved) {
            runCatching { config.now() }.onSuccess { now ->
                duckSetting.set(now.duckSetting)
                log("prompts duck the music to ${now.duckSetting.level}" + if (now.duckSetting.iPhoneFirst) ", or the iPhone's level" else "")
            }
        }
    }
}
