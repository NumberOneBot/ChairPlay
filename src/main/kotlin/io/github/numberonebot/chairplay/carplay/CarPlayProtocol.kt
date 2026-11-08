package io.github.numberonebot.chairplay.carplay

import io.github.numberonebot.chairplay.AppAudio
import io.github.numberonebot.chairplay.config.DesktopConfig
import io.github.numberonebot.chairplay.data.PhoneDataBus
import io.github.numberonebot.chairplay.drive.Driving
import io.github.numberonebot.chairplay.input.CarPlayTextInput
import io.github.numberonebot.chairplay.log
import io.github.numberonebot.chairplay.phone.PhoneConnection
import io.github.numberonebot.chairplay.session.WirelessBootstrap
import java.io.Closeable
import java.nio.file.Path

/**
 * CarPlay as one module beside the shared app: the sink that plays the iPhone's streams through [audio] and shows its
 * screens, the iPhone's route, song, call and battery messages ([bus]; with log.carplay.data also a file in [dir]'s
 * logs), the GPS car [driving] as the iPhone's GPS, and the wireless [connection] that brings each session up. The
 * session's text input goes through [textInput]; CarPlay asking for the car's own screen calls [carButton].
 */
internal class CarPlayProtocol(
    dir: Path,
    config: DesktopConfig,
    audio: AppAudio,
    textInput: CarPlayTextInput,
    driving: Driving,
    carButton: () -> Unit,
) : Closeable {
    private val sink = CarPlayMediaSink(audio.screens, audio.renderer, ::log, audio.microphone, config.echoCancellation,
        audio.duckSetting, audio.bufferedStatus)
    private val data = CarPlayDataFeed(if (config.logCarPlayData) CarPlayDataLog(dir.resolve("logs"), log = ::log) else null)
    private val bootstrap = WirelessBootstrap(config, sink, textInput.listener(carPlaySessions(carButton)), data).apply {
        // The iPhone's GPS for each connection that identifies with the GPS on, as the settings have it then: the car.
        newLocation = { gps -> CarPlayLocation(driving, gps.satellites, ::log) }
    }

    /** The iPhone's data for the HUD, the AltScreen and the controls window, while a session runs. */
    val bus: PhoneDataBus get() = data.bus

    val connection: PhoneConnection get() = bootstrap

    /** Hears the settings each bring-up offers the iPhone its screens by, read from the file then, on its thread. */
    var onConnection: (DesktopConfig) -> Unit by bootstrap::onConnection

    /** Ends the session, then the data log, then the screens, the microphones and the sound. */
    override fun close() {
        bootstrap.close()
        data.close()
        sink.close()
    }
}
