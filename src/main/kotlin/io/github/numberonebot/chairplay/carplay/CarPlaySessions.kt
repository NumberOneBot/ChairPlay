package io.github.numberonebot.chairplay.carplay

import com.shilapi.xcertplay.airplay.AirPlaySession
import com.shilapi.xcertplay.airplay.AirPlaySessionListener
import io.github.numberonebot.chairplay.appLog
import io.github.numberonebot.chairplay.log
import io.github.numberonebot.chairplay.log.LogLevel

/**
 * What the app hears of CarPlay's sessions besides the link (WirelessBootstrap.onLink): their coming and going in the
 * log, DiPlay's own log and transport errors, and [carButton], CarPlay asking for the car's own screen: the driver
 * tapped the car button on its home screen, or pressed Back with nothing left to go back to there, as a car's Back
 * leaves CarPlay for the car's menu.
 */
internal fun carPlaySessions(carButton: () -> Unit) = object : AirPlaySessionListener {
    override val wantsTrace: Boolean get() = appLog.level == LogLevel.TRACE

    override fun onSessionActive(session: AirPlaySession) = log("AirPlay session active")

    override fun onSessionEnded(session: AirPlaySession) = log("AirPlay session ended")

    override fun onHostUiRequested(session: AirPlaySession) = carButton()

    override fun onTransportError(message: String) = appLog.error("AirPlay transport error: $message")

    override fun onDebugLog(message: String) = appLog.fromDiPlay(message)
}
