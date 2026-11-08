package io.github.numberonebot.chairplay.carplay

import com.shilapi.xcertplay.airplay.AirPlayMediaHandler
import com.shilapi.xcertplay.airplay.AirPlaySession
import io.github.numberonebot.chairplay.phone.ClusterControl
import io.github.numberonebot.chairplay.phone.PhoneLink
import io.github.numberonebot.chairplay.phone.Protocol
import io.github.numberonebot.chairplay.phone.ScreenKind

/**
 * A live CarPlay [session]: its keys and touches (CarPlayControl), its AltScreen commands, and its night mode; a
 * keyframe goes through [keyframes], the screens' rate-limited request (CarPlayMediaSink.keyframe).
 */
class CarPlayLink(
    val session: AirPlaySession,
    private val keyframes: (ScreenKind) -> Unit = {},
    private val nanoTime: () -> Long = System::nanoTime,
) : PhoneLink {
    override val protocol = Protocol.CARPLAY
    override val control = CarPlayControl(session)

    override val cluster: ClusterControl = object : ClusterControl {
        override fun setClusterUrl(url: String, send: Boolean) = session.setClusterUrl(url, send)
        override fun setClusterUiShown(shown: Boolean) = session.setClusterUiShown(shown)
    }

    override fun nightMode(on: Boolean) = session.setNightMode(on)

    override fun keyframe(screen: ScreenKind) = keyframes(screen)

    @Volatile
    override var heardNanos: Long = nanoTime()
        private set

    /** The iPhone spoke on this session: its feedback comes every two seconds while it is there. */
    fun heard() {
        heardNanos = nanoTime()
    }
}

/**
 * The live link of a CarPlay connection, told to [report]: each session as it goes active, and null as that one ends.
 * A session that ends after another went active, or a /info probe or pairing retry, which never does, leaves the link
 * as it is: resetting for them would cut a drag on the live session short, and its release would never reach the iPhone.
 * DiPlay reports a session active on every RECORD: the live one is reported again as the same link.
 */
internal class CarPlayLinks(
    private val keyframes: (ScreenKind) -> Unit,
    private val nanoTime: () -> Long = System::nanoTime,
    private val report: (PhoneLink?) -> Unit,
) {
    private var live: CarPlayLink? = null

    @Synchronized
    fun active(session: AirPlaySession) {
        val link = live?.takeIf { it.session === session } ?: CarPlayLink(session, keyframes, nanoTime)
        live = link
        report(link)
    }

    @Synchronized
    fun ended(session: AirPlaySession) {
        if (live?.session !== session) return
        live = null
        report(null)
    }

    /** [session]'s feedback: the live link's sign of life, another session's none. */
    @Synchronized
    fun heard(session: AirPlaySession) {
        live?.takeIf { it.session === session }?.heard()
    }
}

/** This media handler, telling [heard] first of each feedback the iPhone sends, the session's sign of life. */
internal fun AirPlayMediaHandler.reportingFeedback(heard: (AirPlaySession) -> Unit): AirPlayMediaHandler {
    val engine = this
    return object : AirPlayMediaHandler by engine {
        override fun onFeedback(session: AirPlaySession): Map<String, Any?>? {
            heard(session)
            return engine.onFeedback(session)
        }
    }
}
