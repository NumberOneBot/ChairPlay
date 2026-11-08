package io.github.numberonebot.chairplay

import com.shilapi.xcertplay.airplay.AirPlaySession
import com.shilapi.xcertplay.airplay.AirPlaySessionListener
import io.github.numberonebot.chairplay.media.VideoWindow
import io.github.numberonebot.chairplay.ui.UiSessions
import java.util.concurrent.atomic.AtomicReference

/**
 * What the app does as CarPlay sessions come and go: the [active] one, the settings screen [ui], the main [window] and
 * its [title], CarPlay's [theme], and [sessionChanged] for the touch, the HUD and the AltScreen.
 */
internal fun sessionListener(
    active: AtomicReference<AirPlaySession?>,
    ui: UiSessions,
    window: VideoWindow,
    title: MainTitle,
    theme: CarPlayTheme,
    sessionChanged: () -> Unit,
) = object : AirPlaySessionListener {
    override val wantsTrace: Boolean get() = appLog.level == io.github.numberonebot.chairplay.log.LogLevel.TRACE

    override fun onSessionActive(session: AirPlaySession) {
        active.set(session)
        sessionChanged()
        log("AirPlay session active")
        ui.sessionActive()
        title.reconnecting.set(false)
        title.session()
        theme.send(session)
    }

    override fun onSessionEnded(session: AirPlaySession) {
        // /info probes and pairing retries end too; resetting for them would cut a drag on the live session
        // short, and its release would never reach the iPhone.
        if (active.compareAndSet(session, null)) {
            sessionChanged()
            ui.sessionEnded()
            window.clearPicture()
            title.connecting()
        }
        log("AirPlay session ended")
    }

    // CarPlay asked for the car's own screen: the driver tapped the car button on its home screen, or pressed Back
    // with nothing left to go back to there, as a car's Back leaves CarPlay for the car's menu. The settings screen
    // covers CarPlay. With the page in a browser instead, the window gives the screen back to Windows, as before.
    override fun onHostUiRequested(session: AirPlaySession) {
        if (ui.carButton()) {
            log("CarPlay asked for ChairPlay's screen (the car button, or Back on its home screen): showing the settings")
        } else {
            log("CarPlay asked for the Windows screen; minimizing the window")
            window.minimize()
        }
    }

    override fun onTransportError(message: String) = appLog.error("AirPlay transport error: $message")

    override fun onDebugLog(message: String) = appLog.fromDiPlay(message)
}
