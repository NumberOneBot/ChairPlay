package io.github.numberonebot.chairplay

import io.github.numberonebot.chairplay.drive.GaugeDemo
import io.github.numberonebot.chairplay.hud.HudDemo
import io.github.numberonebot.chairplay.media.VideoWindow
import io.github.numberonebot.chairplay.phone.AltscreenContent
import io.github.numberonebot.chairplay.phone.PhoneLink
import io.github.numberonebot.chairplay.ui.UiDemo

/**
 * The Experiments page's demos on the AltScreen, one at a time, none while it is off: the HUD's ("maneuvers",
 * "lanes"), which the AltScreen shows on its HUD as they play and goes back to its view after, and the gauges'
 * ([GaugeDemo]), drawn large over whatever it shows.
 */
internal class AltscreenDemos(
    private val view: AltscreenView,
    private val window: VideoWindow,
    private val active: () -> PhoneLink?,
) : UiDemo {
    /** The gauges' demo, there once the AltScreen draws the GPS car's gauges. */
    @Volatile var gauges: GaugeDemo? = null

    override val playing get() = view.hudDemo.get()?.first ?: GAUGES.takeIf { gauges?.playing == true }
    override val available get() = view.on

    override fun play(kind: String?): Boolean {
        if (kind != null && !view.on) return false
        view.hudDemo.getAndSet(null)?.second?.stop()
        gauges?.stop()
        if (kind != null && kind != GAUGES) {
            view.hudDemo.set(kind to HudDemo(withMedia = false, lanes = kind == "lanes").start(window::repaint) {})
            window.showView(true)
            window.setShown(true)
        } else {
            // Back to the AltScreen's view, which waits on black for the iPhone's picture as any change does.
            window.showView(view.content == AltscreenContent.HUD)
            view.show(active())
        }
        if (kind == GAUGES) {
            val gauges = gauges ?: return false
            gauges.start(window::repaint)
            window.setShown(true)
        }
        window.repaint()
        return true
    }

    companion object {
        const val GAUGES = "gauges"
    }
}
