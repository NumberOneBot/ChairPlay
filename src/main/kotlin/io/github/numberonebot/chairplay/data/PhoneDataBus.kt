package io.github.numberonebot.chairplay.data

import java.util.concurrent.CopyOnWriteArrayList

/**
 * What the phone tells the car besides the picture, as typed events and a snapshot: route guidance, lanes, now
 * playing and its artwork, calls, the phone's network and its battery. A protocol's decoder sets each value as a
 * whole (carplay.Iap2DataDecoder for CarPlay), from any thread; a value equal to the one held raises nothing.
 * Listeners hear each change on the thread that made it, after the snapshot holds it.
 */
class PhoneDataBus {
    private val lock = Any()
    private val listeners = CopyOnWriteArrayList<(PhoneDataEvent) -> Unit>()
    private val refreshers = CopyOnWriteArrayList<() -> Unit>()

    private var route: RouteGuidance? = null
    private var lanes: LaneGuidance? = null
    private var nowPlaying: NowPlaying? = null
    private var artwork: Artwork? = null
    private val calls = LinkedHashMap<String, Call>()
    private var communications: Communications? = null
    private var power: Power? = null

    /** [listener] hears every change from now on, until the returned handle is closed. */
    fun addListener(listener: (PhoneDataEvent) -> Unit): AutoCloseable {
        listeners += listener
        return AutoCloseable { listeners -= listener }
    }

    /**
     * [refresh] runs before every snapshot, until the returned handle is closed: a decoder whose values age (the next
     * maneuver) sets them again there, and listeners hear what changed.
     */
    fun onSnapshot(refresh: () -> Unit): AutoCloseable {
        refreshers += refresh
        return AutoCloseable { refreshers -= refresh }
    }

    /** The latest state, after every [onSnapshot] refresh. */
    fun snapshot(): PhoneDataSnapshot {
        for (refresh in refreshers) refresh()
        return synchronized(lock) { snapshotLocked() }
    }

    fun route(next: RouteGuidance?) = publish {
        if (next == route) null else PhoneDataEvent.RouteChanged(next).also { route = next }
    }

    /** The current lane event, the one [RouteGuidance.laneGuidanceIndex] names; null for none. */
    fun lanes(next: LaneGuidance?) = publish {
        if (next == lanes) null else PhoneDataEvent.LanesChanged(next).also { lanes = next }
    }

    fun nowPlaying(next: NowPlaying?) = publish {
        if (next == nowPlaying) null else PhoneDataEvent.NowPlayingChanged(next).also { nowPlaying = next }
    }

    /** Empty bytes clear the picture; the artwork stays, matched to the song by its id. */
    fun artwork(next: Artwork) = publish {
        if (next == artwork) null else PhoneDataEvent.ArtworkChanged(next).also { artwork = next }
    }

    /**
     * One call as it is now, known by its [Call.id]: several can exist at once (call waiting). An [ended][Call.ended]
     * call leaves the calls; one the bus does not hold raises nothing.
     */
    fun call(call: Call) = publish {
        val previous = calls[call.id]
        when {
            call.ended -> calls.remove(call.id)?.let { PhoneDataEvent.CallChanged(call, calls.values.toList()) }
            call == previous -> null
            else -> {
                calls[call.id] = call
                PhoneDataEvent.CallChanged(call, calls.values.toList())
            }
        }
    }

    fun communications(next: Communications?) = publish {
        if (next == communications) null else PhoneDataEvent.CommunicationsChanged(next).also { communications = next }
    }

    fun power(next: Power?) = publish {
        if (next == power) null else PhoneDataEvent.PowerChanged(next).also { power = next }
    }

    /** The phone's session ended: forget everything. */
    fun clear() {
        val wasEmpty = synchronized(lock) {
            val empty = snapshotLocked() == PhoneDataSnapshot()
            route = null
            lanes = null
            nowPlaying = null
            artwork = null
            calls.clear()
            communications = null
            power = null
            empty
        }
        if (!wasEmpty) dispatch(PhoneDataEvent.Cleared)
    }

    private inline fun publish(change: () -> PhoneDataEvent?) {
        val event = synchronized(lock) { change() } ?: return
        dispatch(event)
    }

    private fun snapshotLocked() = PhoneDataSnapshot(
        route = route,
        lanes = lanes,
        nowPlaying = nowPlaying,
        artwork = artwork,
        calls = calls.values.toList(),
        communications = communications,
        power = power,
    )

    /** A listener's failure must not reach the protocol's thread that delivered the data. */
    private fun dispatch(event: PhoneDataEvent) {
        for (listener in listeners) runCatching { listener(event) }
    }
}
