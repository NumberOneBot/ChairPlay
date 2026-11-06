package io.github.numberonebot.chairplay

import io.github.numberonebot.chairplay.data.Call
import io.github.numberonebot.chairplay.data.CallDirection
import io.github.numberonebot.chairplay.data.CallStatus
import io.github.numberonebot.chairplay.data.CarPlayDataBus
import io.github.numberonebot.chairplay.data.CarPlayDataEvent
import io.github.numberonebot.chairplay.media.VideoWindow
import java.util.concurrent.atomic.AtomicBoolean

/**
 * An incoming call ringing on the iPhone, told on the desktop: the CarPlay window's taskbar button [flash]es while it
 * rings, and with [front] on the window comes forward as it starts ([bringForward]). The flash stops as the call is
 * answered or ends; Windows stops it too once the window is in front.
 */
class CallAlert(
    private val flash: (Boolean) -> Unit,
    private val bringForward: () -> Unit,
    private val front: () -> Boolean,
) {
    private var ringing = false

    /** The iPhone's calls as they are now. */
    @Synchronized
    fun calls(now: List<Call>) {
        val ring = now.any { it.status == CallStatus.RINGING && it.direction != CallDirection.OUTGOING }
        if (ring == ringing) return
        ringing = ring
        flash(ring)
        if (ring && runCatching(front).getOrDefault(false)) bringForward()
    }
}

/**
 * What the app does with the iPhone's messages besides its screens: the controls window's page follows [playing]
 * (pause or play), and an incoming call flashes the CarPlay [window]'s taskbar button and, with [frontOnCall], brings
 * the window forward.
 */
internal fun watchCarPlayData(
    bus: CarPlayDataBus,
    playing: () -> Boolean,
    controlsChanged: () -> Unit,
    window: VideoWindow,
    frontOnCall: () -> Boolean,
) {
    val wasPlaying = AtomicBoolean()
    val alert = CallAlert(window.commands::flash, { window.setShown(true) }, frontOnCall)
    bus.addListener { event ->
        val now = playing()
        if (wasPlaying.getAndSet(now) != now) controlsChanged()
        // A session gone takes its calls along.
        if (event is CarPlayDataEvent.CallChanged) alert.calls(event.calls)
        if (event is CarPlayDataEvent.Cleared) alert.calls(emptyList())
    }
}
