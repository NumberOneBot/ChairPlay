package io.github.numberonebot.chairplay

import io.github.numberonebot.chairplay.bridge.FrameOutput
import io.github.numberonebot.chairplay.carplay.CarPlayMediaSink
import io.github.numberonebot.chairplay.config.DesktopConfig
import io.github.numberonebot.chairplay.config.VolumeStore
import io.github.numberonebot.chairplay.config.WindowSize
import io.github.numberonebot.chairplay.log.LogLevel
import io.github.numberonebot.chairplay.media.AudioDevices
import io.github.numberonebot.chairplay.media.AudioRenderer
import io.github.numberonebot.chairplay.media.ScreenDecoders
import io.github.numberonebot.chairplay.media.StartFade
import io.github.numberonebot.chairplay.media.VideoWindow
import io.github.numberonebot.chairplay.media.Volume
import io.github.numberonebot.chairplay.phone.ScreenKind
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
    // The picture shared with games (display.share), its safe area in the display's pixels; the sink closes it.
    private val display = AtomicReference(config.display)
    private val frameOutput = FrameOutput(
        screen = { display.get().let { WindowSize(it.width, it.height) } }, safe = { display.get().safeRect }, log = ::log,
    )
    /** The main screen and the AltScreen, decoded into [window] and [altscreen]; the sink closes them. */
    val screens: ScreenDecoders
    val sink: CarPlayMediaSink
    private val renderer: AudioRenderer
    fun bufferedAudio(): Map<String, Any>? = sink.bufferedAudio.state()
    fun updateBufferedAudio() { if (sink.bufferedAudio.poll()) changed() }

    init {
        // A device that is gone gives way to the Windows default, with a warning that lists the devices.
        val output = AudioDevices.output(config.audioOutput) { appLog.log(LogLevel.WARNING, it) }
        val input = AudioDevices.input(config.audioInput) { appLog.log(LogLevel.WARNING, it) }
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
        screens = ScreenDecoders(mapOf(ScreenKind.MAIN to window, ScreenKind.CLUSTER to altscreen), frameOutput, ::log) {
            config.now().hardwareVideo
        }
        sink = CarPlayMediaSink(screens, renderer, ::log, input, config.echoCancellation, duckSetting::get)
        frameOutput.enabled = config.display.share
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
        if (saved.keys.any { it.startsWith("display.") }) {
            runCatching { config.now() }.onSuccess { now ->
                display.set(now.display)
                frameOutput.enabled = now.display.share
            }
        }
    }
}
