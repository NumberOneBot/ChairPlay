package io.github.numberonebot.chairplay.carplay

import com.shilapi.xcertplay.hud.BydHudRouteState
import com.shilapi.xcertplay.iap2.wire.Iap2Frame
import com.shilapi.xcertplay.media.CarPlayPlaybackStatus
import com.shilapi.xcertplay.transport.Iap2ArtworkTransfer
import io.github.numberonebot.chairplay.data.Artwork
import io.github.numberonebot.chairplay.data.Call
import io.github.numberonebot.chairplay.data.Communications
import io.github.numberonebot.chairplay.data.LaneGuidance
import io.github.numberonebot.chairplay.data.Maneuver
import io.github.numberonebot.chairplay.data.NowPlaying
import io.github.numberonebot.chairplay.data.PhoneDataBus
import io.github.numberonebot.chairplay.data.Power
import io.github.numberonebot.chairplay.data.RouteGuidance
import io.github.numberonebot.chairplay.data.RouteGuidanceState

/**
 * The iPhone's iAP2 data frames and artwork transfers, from any thread, read into [bus]. An update carries only what
 * changed: the values so far are kept here, and the bus gets each value whole.
 *
 * Upstream's parsers decide what they already know: BydHudRouteState which maneuver is next (its rules for the
 * iPhone's empty lists and stale routes come from cars), CarPlayPlaybackStatus the song. The rest of each message
 * comes from [CarPlayDataMessages]. The bus is fed under this decoder's lock, so its values never go back to older
 * ones; its listeners run under that lock too.
 */
class Iap2DataDecoder(private val bus: PhoneDataBus, nanoTime: () -> Long = System::nanoTime) {
    private val lock = Any()

    private val nextManeuverRules = BydHudRouteState(nanoTime)
    private var routeValues: RouteGuidance? = null
    private val maneuvers = HashMap<Int, Maneuver>()
    private var activeIndex: Int? = null

    private val playback = CarPlayPlaybackStatus()
    private var nowPlayingValues = NowPlaying()

    /** The lane events the iPhone has sent ahead, by their index, oldest first. */
    private val laneEvents = LinkedHashMap<Int, LaneGuidance>()

    /** The calls that have not ended, each as last merged, by the id the bus knows it by. */
    private val calls = HashMap<String, Call>()
    private var communications: Communications? = null
    private var power: Power? = null

    // Last: the refresh reads the state above. A next maneuver whose time ran out ends at the next snapshot.
    init {
        bus.onSnapshot { synchronized(lock) { publishRoute() } }
    }

    fun accept(frame: Iap2Frame) = synchronized(lock) { acceptLocked(frame) }

    fun acceptArtwork(transfer: Iap2ArtworkTransfer) = bus.artwork(Artwork(transfer.id, transfer.bytes))

    /** The CarPlay session ended: forget everything, here and on the bus. */
    fun clear() = synchronized(lock) {
        nextManeuverRules.clear()
        routeValues = null
        maneuvers.clear()
        activeIndex = null
        playback.clearAll()
        nowPlayingValues = NowPlaying()
        laneEvents.clear()
        calls.clear()
        communications = null
        power = null
        bus.clear()
    }

    private fun acceptLocked(frame: Iap2Frame) {
        val id = frame.messageId
        if (id == ROUTE_GUIDANCE_UPDATE || id == ROUTE_GUIDANCE_MANEUVER_UPDATE) nextManeuverRules.accept(id, frame.payload)
        if (id == CarPlayPlaybackStatus.NOW_PLAYING_UPDATE) playback.acceptUpdate(frame)
        val fields = CarPlayDataMessages.decode(frame) ?: return
        when (id) {
            ROUTE_GUIDANCE_UPDATE -> {
                val state = fields.coded("routeGuidanceState", Iap2DataCodes.routeGuidanceState, null)
                if (state == RouteGuidanceState.NO_ROUTE_SET || state == RouteGuidanceState.ARRIVED) {
                    maneuvers.clear()
                    activeIndex = null
                    routeValues = RouteGuidance().merged(fields)
                } else {
                    val values = (routeValues ?: RouteGuidance()).merged(fields)
                    values.currentManeuverIndexes.firstOrNull()?.let { activeIndex = it }
                    routeValues = values
                }
                // The MHI2Q project's lane cache (hook/routeguidance/rgd_hook.c): a route reset forgets the lane events,
                // and the iPhone never needs one below the current index again.
                if (state == RouteGuidanceState.NO_ROUTE_SET) laneEvents.clear()
                routeValues?.laneGuidanceIndex?.takeIf { fields.has("laneGuidanceIndex") }?.let { current ->
                    laneEvents.keys.removeAll { it < current }
                }
                publishRoute()
                publishLanes()
            }
            ROUTE_GUIDANCE_MANEUVER_UPDATE -> {
                val index = fields.int("index", null) ?: return
                maneuvers[index] = (maneuvers[index] ?: Maneuver(index)).merged(fields)
                publishRoute()
            }
            LANE_GUIDANCE_INFO_UPDATE -> {
                val event = laneGuidance(fields)
                val index = event.index ?: return
                // Full: the oldest event goes, never the current one.
                if (index !in laneEvents && laneEvents.size >= MAX_LANE_EVENTS) {
                    val current = routeValues?.laneGuidanceIndex
                    laneEvents.keys.firstOrNull { it != current }?.let { laneEvents.remove(it) }
                }
                laneEvents[index] = event
                publishLanes()
            }
            CarPlayPlaybackStatus.NOW_PLAYING_UPDATE -> {
                nowPlayingValues = nowPlayingValues.merged(fields)
                val song = playback.nowPlaying
                bus.nowPlaying(
                    nowPlayingValues.copy(
                        title = song.title, artist = song.artist, album = song.album, durationMillis = song.durationMillis,
                        elapsedMillis = song.elapsedMillis, playing = song.playing, appName = song.sourceApp,
                        artworkTransferId = song.artworkTransferId,
                    ),
                )
            }
            CALL_STATE_UPDATE -> acceptCall(fields)
            COMMUNICATIONS_UPDATE -> bus.communications((communications ?: Communications()).merged(fields).also { communications = it })
            POWER_UPDATE -> bus.power((power ?: Power()).merged(fields).also { power = it })
        }
    }

    /** The route with its maneuvers; nothing before the first route update. */
    private fun publishRoute() {
        val values = routeValues ?: return
        val showing = nextManeuverRules.currentApple() != null
        bus.route(
            values.copy(
                nextManeuver = if (showing) activeIndex?.let { maneuvers[it] } else null,
                currentManeuvers = values.currentManeuverIndexes.mapNotNull { maneuvers[it] },
            ),
        )
    }

    /** The lane event the route makes current. */
    private fun publishLanes() = bus.lanes(routeValues?.laneGuidanceIndex?.let { laneEvents[it] })

    /** Several calls can exist at once (call waiting); each is known by its UUID, or its number without one. */
    private fun acceptCall(fields: DecodedFields) {
        val id = fields.string("callUuid", null) ?: fields.string("remoteId", null) ?: ""
        val call = (calls[id] ?: Call(id)).merged(fields)
        if (call.ended) calls.remove(id) else calls[id] = call
        bus.call(call)
    }

    private companion object {
        const val ROUTE_GUIDANCE_UPDATE = 0x5201
        const val ROUTE_GUIDANCE_MANEUVER_UPDATE = 0x5202
        const val LANE_GUIDANCE_INFO_UPDATE = 0x5204
        /** As many lane events as the MHI2Q project keeps (MANEUVER_CACHE_SIZE); the iPhone sends a few at a time. */
        const val MAX_LANE_EVENTS = 32
        const val CALL_STATE_UPDATE = 0x4155
        const val COMMUNICATIONS_UPDATE = 0x4158
        const val POWER_UPDATE = 0xae01
    }
}
