package io.github.numberonebot.chairplay.hud

import io.github.numberonebot.chairplay.data.CallDirection
import io.github.numberonebot.chairplay.data.CallStatus
import io.github.numberonebot.chairplay.data.DistanceUnits
import io.github.numberonebot.chairplay.data.LaneGuidance
import io.github.numberonebot.chairplay.data.Maneuver
import io.github.numberonebot.chairplay.data.PhoneDataSnapshot
import io.github.numberonebot.chairplay.data.RouteGuidance
import io.github.numberonebot.chairplay.data.RouteGuidanceState
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter

/** What the HUD shows, as ready text, from a snapshot of the CarPlay data bus; see desktop-docs/hud-spec.md. */
data class HudState(
    val media: HudMedia? = null,
    val maneuver: HudManeuver? = null,
    val arrival: HudArrival? = null,
    /** A ringing, starting, active or held call, which goes on top of everything else. */
    val call: String? = null,
)

data class HudMedia(
    val title: String?,
    val artist: String?,
    val album: String?,
    val source: String?,
    /** How far the song has played, 0 to 1; null without a duration. */
    val progress: Float?,
    val playing: Boolean,
    /** Which artwork the iPhone sent for this song, to tell a new cover from the last one. */
    val artworkId: Int?,
)

data class HudManeuver(
    /** Apple's RouteGuidanceManeuverType, for the arrow. */
    val typeCode: Int?,
    val distance: String?,
    val road: String?,
    val description: String?,
    /** The maneuver as the iPhone sent it, for the maneuver renderer. */
    val next: Maneuver? = null,
    /** The metres to it, for the arrow's progress. */
    val distanceMeters: Long? = null,
    /** The lanes to show under it, while the iPhone shows lanes. */
    val lanes: LaneGuidance? = null,
)

data class HudArrival(val time: String?, val remaining: String?, val destination: String?)

/** Text from the iPhone cut to [MAX_TEXT] characters before the HUD measures and draws it. */
private fun String?.clip(): String? = this?.ifBlank { null }?.take(MAX_TEXT)

/**
 * The HUD for [snapshot], times in [zone]: the song alone without guidance, the route and the song side by side
 * while the iPhone guides (RouteGuidanceUpdate's state), and a call on top. Which app is on the CarPlay screen the
 * iPhone does not say; visibleInApp stayed true with the music app in front.
 */
fun hudState(snapshot: PhoneDataSnapshot, zone: ZoneId = ZoneId.systemDefault()): HudState {
    val route = snapshot.route?.takeIf { it.state in GUIDING }
    val maneuver = route?.let {
        val next = it.nextManeuver
        HudManeuver(
            typeCode = next?.typeCode,
            distance = distance(it.distanceToManeuverText, it.distanceToManeuverUnits, it.distanceToManeuverMeters),
            road = next?.afterRoad.clip() ?: it.currentRoad.clip(),
            description = next?.description.clip(),
            next = next,
            distanceMeters = it.distanceToManeuverMeters,
            lanes = snapshot.lanes.takeIf { _ -> lanesVisible(it) },
        )
    }
    val arrival = route?.let {
        HudArrival(
            time = it.arrivalEpochSeconds?.let { seconds -> TIME.format(Instant.ofEpochSecond(seconds).atZone(zone)) },
            remaining = listOfNotNull(
                it.remainingSeconds?.let(::duration),
                distance(it.remainingText, it.remainingUnits, it.remainingMeters),
            ).joinToString(" · ").ifEmpty { null },
            destination = it.destination.clip(),
        )
    }
    val media = snapshot.nowPlaying?.takeIf { !it.title.isNullOrBlank() }?.let { song ->
        HudMedia(
            title = song.title.clip(),
            artist = song.artist.clip(),
            album = song.album.clip(),
            source = song.appName.clip(),
            progress = song.durationMillis?.takeIf { it > 0 }?.let { total ->
                ((song.elapsedMillis ?: 0).toFloat() / total).coerceIn(0f, 1f)
            },
            playing = song.playing,
            artworkId = song.artworkTransferId,
        )
    }
    val call = snapshot.calls.firstNotNullOfOrNull { call ->
        val what = when (call.status) {
            CallStatus.RINGING -> if (call.direction == CallDirection.OUTGOING) "Calling" else "Incoming call"
            CallStatus.SENDING, CallStatus.CONNECTING -> "Calling"
            CallStatus.ACTIVE -> "On a call"
            CallStatus.HELD -> "On hold"
            else -> null
        }
        what?.let { listOfNotNull(it, call.name.clip()).joinToString(" · ") }
    }
    return HudState(media, maneuver, arrival, call)
}

/**
 * The MHI2Q bridge's gate for lanes (BAPBridge.laneGuidanceVisible): the iPhone's laneGuidanceShowing, unless no route
 * is set. No distance or maneuver type counts: lanes may come long before a junction, to get into lane early.
 */
private fun lanesVisible(route: RouteGuidance) =
    route.laneGuidanceShowing == true && !(route.maneuverCount == 0 && route.state == RouteGuidanceState.NO_ROUTE_SET)

/** The iPhone's own distance text with a short unit, or the metres counted out when it sent no text. */
private fun distance(text: String?, units: DistanceUnits?, meters: Long?): String? {
    if (!text.isNullOrBlank()) return listOfNotNull(text, units?.let(::unit)).joinToString(" ")
    meters ?: return null
    return if (meters >= 1_000) "%.1f km".format(meters / 1_000.0) else "$meters m"
}

private fun unit(units: DistanceUnits) = when (units) {
    DistanceUnits.KILOMETERS -> "km"
    DistanceUnits.METERS -> "m"
    DistanceUnits.MILES -> "mi"
    DistanceUnits.YARDS -> "yd"
    DistanceUnits.FEET -> "ft"
}

private fun duration(seconds: Long): String {
    val minutes = (seconds + 30) / 60
    return if (minutes < 60) "$minutes min" else "${minutes / 60} h %02d min".format(minutes % 60)
}

private val TIME = DateTimeFormatter.ofPattern("HH:mm")

/**
 * The states in which the iPhone guides: Apple Maps starts a route away from it with PROCEED_TO_ROUTE where Google
 * Maps sends ROUTE_SET, and LOCATING comes mid-route while the position is found again.
 */
private val GUIDING = setOf(
    RouteGuidanceState.ROUTE_SET, RouteGuidanceState.REROUTING, RouteGuidanceState.PROCEED_TO_ROUTE, RouteGuidanceState.LOCATING,
)
private const val MAX_TEXT = 256
