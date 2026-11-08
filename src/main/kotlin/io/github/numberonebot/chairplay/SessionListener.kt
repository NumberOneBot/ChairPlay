package io.github.numberonebot.chairplay

import io.github.numberonebot.chairplay.media.VideoWindow
import io.github.numberonebot.chairplay.phone.PhoneLink
import io.github.numberonebot.chairplay.ui.UiSessions
import java.util.concurrent.atomic.AtomicReference

/**
 * What the app does as the phone's link comes and goes: the [active] one, the settings screen [ui], the main [window]
 * and its [title], the phone's [theme], and [sessionChanged] for the touch, the HUD and the AltScreen.
 */
internal class SessionListener(
    private val active: AtomicReference<PhoneLink?>,
    private val ui: UiSessions,
    private val window: VideoWindow,
    private val title: MainTitle,
    private val theme: CarPlayTheme,
    private val sessionChanged: () -> Unit,
) {
    /**
     * The connection's link went live ([link]) or ended (null). One retired by Reconnect or Disconnect ends unheard, and
     * Main clears it then; ending after that, it changes nothing again.
     */
    fun linked(link: PhoneLink?) {
        if (link != null) {
            active.set(link)
            sessionChanged()
            ui.sessionActive()
            title.reconnecting.set(false)
            title.session()
            theme.send(link)
        } else if (active.getAndSet(null) != null) {
            sessionChanged()
            ui.sessionEnded()
            window.clearPicture()
            title.connecting()
        }
    }

    /**
     * The phone asked for the car's own screen (the car button): the settings screen covers the phone's. With the page
     * in a browser instead, the window gives the screen back to Windows, as before.
     */
    fun carButton() {
        if (ui.carButton()) {
            log("CarPlay asked for ChairPlay's screen (the car button, or Back on its home screen): showing the settings")
        } else {
            log("CarPlay asked for the Windows screen; minimizing the window")
            window.minimize()
        }
    }
}
