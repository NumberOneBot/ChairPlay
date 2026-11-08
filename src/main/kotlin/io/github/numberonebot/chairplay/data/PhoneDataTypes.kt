package io.github.numberonebot.chairplay.data

// What a phone tells the car besides the picture, whatever its protocol. CarPlay's wire codes for these enums are in
// carplay/Iap2DataCodes.kt; only the maneuver and lane codes are the app's own too.

/** RouteGuidanceState (0x5201 parameter 1). */
enum class RouteGuidanceState { NO_ROUTE_SET, ROUTE_SET, ARRIVED, LOADING, LOCATING, REROUTING, PROCEED_TO_ROUTE }

/** ManeuverState (0x5201 parameter 2): how close the next maneuver is. */
enum class ManeuverState { CONTINUE, INITIAL, PREPARE, EXECUTE }

/** The units of the iPhone's own distance text. */
enum class DistanceUnits { KILOMETERS, MILES, METERS, YARDS, FEET }

enum class DrivingSide { RIGHT, LEFT }

enum class JunctionType { INTERSECTION, ROUNDABOUT }

/**
 * RouteGuidanceManeuverType (0x5202 parameter 3), the same codes upstream's BydManeuverCodes and
 * FceManeuverCodes map to cluster glyphs. 28 to 46 are the exits of a roundabout, first to nineteenth.
 * Apple's [code]s are the app's maneuver codes, whatever the phone: the HUD and the MHI2Q renderer draw by them.
 */
enum class ManeuverType(val code: Int) {
    NO_TURN(0), LEFT_TURN(1), RIGHT_TURN(2), STRAIGHT_AHEAD(3), U_TURN(4), FOLLOW_ROAD(5), ENTER_ROUNDABOUT(6),
    EXIT_ROUNDABOUT(7), OFF_RAMP(8), ON_RAMP(9), ARRIVE_END_OF_NAVIGATION(10), START_ROUTE(11), ARRIVE_AT_DESTINATION(12),
    KEEP_LEFT(13), KEEP_RIGHT(14), ENTER_FERRY(15), EXIT_FERRY(16), CHANGE_FERRY(17), START_ROUTE_WITH_U_TURN(18),
    U_TURN_AT_ROUNDABOUT(19), LEFT_TURN_AT_END(20), RIGHT_TURN_AT_END(21), HIGHWAY_OFF_RAMP_LEFT(22),
    HIGHWAY_OFF_RAMP_RIGHT(23), ARRIVE_AT_DESTINATION_LEFT(24), ARRIVE_AT_DESTINATION_RIGHT(25), U_TURN_WHEN_POSSIBLE(26),
    ARRIVE_END_OF_DIRECTIONS(27),
    ROUNDABOUT_EXIT_1(28), ROUNDABOUT_EXIT_2(29), ROUNDABOUT_EXIT_3(30), ROUNDABOUT_EXIT_4(31), ROUNDABOUT_EXIT_5(32),
    ROUNDABOUT_EXIT_6(33), ROUNDABOUT_EXIT_7(34), ROUNDABOUT_EXIT_8(35), ROUNDABOUT_EXIT_9(36), ROUNDABOUT_EXIT_10(37),
    ROUNDABOUT_EXIT_11(38), ROUNDABOUT_EXIT_12(39), ROUNDABOUT_EXIT_13(40), ROUNDABOUT_EXIT_14(41), ROUNDABOUT_EXIT_15(42),
    ROUNDABOUT_EXIT_16(43), ROUNDABOUT_EXIT_17(44), ROUNDABOUT_EXIT_18(45), ROUNDABOUT_EXIT_19(46),
    SHARP_LEFT_TURN(47), SHARP_RIGHT_TURN(48), SLIGHT_LEFT_TURN(49), SLIGHT_RIGHT_TURN(50), CHANGE_HIGHWAY(51),
    CHANGE_HIGHWAY_LEFT(52), CHANGE_HIGHWAY_RIGHT(53),
    ;

    /** The exit a roundabout maneuver takes, 1 to 19; null for other maneuvers. */
    val roundaboutExit: Int? get() = if (code in 28..46) code - 27 else null
}

/** A lane's state in LaneGuidanceInfoUpdate (0x5204); Apple's [code]s are what the MHI2Q renderer draws by. */
enum class LaneStatus(val code: Int) { NOT_GOOD(0), GOOD(1), PREFERRED(2) }

enum class PlaybackStatus { STOPPED, PLAYING, PAUSED, SEEK_FORWARD, SEEK_BACKWARD }

enum class ShuffleMode { OFF, SONGS, ALBUMS }

enum class RepeatMode { OFF, ONE, ALL }

enum class CallStatus { DISCONNECTED, SENDING, RINGING, CONNECTING, ACTIVE, HELD, DISCONNECTING }

enum class CallDirection { UNKNOWN, INCOMING, OUTGOING }

enum class CallService { UNKNOWN, TELEPHONY, FACETIME_AUDIO, FACETIME_VIDEO }

enum class DisconnectReason { ENDED, DECLINED, FAILED }

enum class RegistrationStatus {
    UNKNOWN, NOT_REGISTERED, SEARCHING, DENIED, REGISTERED_HOME, REGISTERED_ROAMING, EMERGENCY_CALLS_ONLY,
}

enum class BatteryChargingState { DISABLED, CHARGING, CHARGED }

/** One maneuver of the route, from RouteGuidanceManeuverUpdate (0x5202); the iPhone sends each once per route. */
data class Maneuver(
    val index: Int,
    /** Apple's maneuver type as sent, also when [type] does not know it yet. */
    val typeCode: Int? = null,
    val description: String? = null,
    /** The road the maneuver turns onto. */
    val afterRoad: String? = null,
    val distanceBetweenMeters: Long? = null,
    val distanceBetweenText: String? = null,
    val distanceBetweenUnits: DistanceUnits? = null,
    val drivingSide: DrivingSide? = null,
    val junctionType: JunctionType? = null,
    /** The angles of the junction's roads, in degrees, as the iPhone draws the junction. */
    val junctionAngles: List<Int> = emptyList(),
    /** The angle of the road the maneuver leaves the junction by. */
    val exitAngle: Int? = null,
) {
    val type: ManeuverType? get() = ManeuverType.entries.firstOrNull { it.code == typeCode }
}

/** The route from RouteGuidanceUpdate (0x5201), each value as last sent, with its maneuvers. */
data class RouteGuidance(
    val state: RouteGuidanceState? = null,
    val maneuverState: ManeuverState? = null,
    val currentRoad: String? = null,
    val destination: String? = null,
    val arrivalEpochSeconds: Long? = null,
    val remainingSeconds: Long? = null,
    val remainingMeters: Long? = null,
    /** The iPhone's own text for [remainingMeters], in [remainingUnits]. */
    val remainingText: String? = null,
    val remainingUnits: DistanceUnits? = null,
    val distanceToManeuverMeters: Long? = null,
    val distanceToManeuverText: String? = null,
    val distanceToManeuverUnits: DistanceUnits? = null,
    /** The indexes of the maneuvers the iPhone shows now, the next first; briefly empty while it reroutes. */
    val currentManeuverIndexes: List<Int> = emptyList(),
    val maneuverCount: Int? = null,
    /** Whether the navigation app shows the guidance on the CarPlay screen now (0x5201 parameter 15). */
    val visibleInApp: Boolean? = null,
    val laneGuidanceIndex: Int? = null,
    val laneGuidanceTotal: Int? = null,
    val laneGuidanceShowing: Boolean? = null,
    /** The navigation app. */
    val sourceName: String? = null,
    /**
     * The maneuver to show, by upstream's BydHudRouteState rules: the first current one, kept through the
     * iPhone's brief empty lists, gone after 3 s of an empty list, 30 s without a route update, or at the
     * end of the route. The rules depend on time: [PhoneDataBus.snapshot] brings them up to date.
     */
    val nextManeuver: Maneuver? = null,
    /** The current maneuvers whose details have arrived, in the iPhone's order. */
    val currentManeuvers: List<Maneuver> = emptyList(),
)

/** One lane of LaneGuidanceInfoUpdate (0x5204), its angles in degrees, negative to the left. */
data class Lane(
    /** Where the lane is across the road, as sent. */
    val position: Int? = null,
    /** Apple's lane status as sent, also when [status] does not know it. */
    val statusCode: Int? = null,
    /** Every way the lane leads. */
    val angles: List<Int> = emptyList(),
    /** The way the route takes from this lane, when the iPhone marks one. */
    val highlightAngle: Int? = null,
) {
    val status: LaneStatus? get() = LaneStatus.entries.firstOrNull { it.code == statusCode }

    /** The lane's main way: the highlighted angle, else the first, as the MHI2Q project's parser reads it. */
    val direction: Int? get() = highlightAngle ?: angles.firstOrNull()
}

/**
 * One lane guidance event from LaneGuidanceInfoUpdate (0x5204), the lanes of one place on the route. Its layout is not
 * in upstream and has not been seen from an iPhone (the identification does not declare the message): it is the one
 * the MHI2Q project's parser reads (hook/routeguidance/rgd_tlv.c), and the data log keeps the raw bytes too.
 */
data class LaneGuidance(val index: Int? = null, val lanes: List<Lane> = emptyList(), val description: String? = null)

/** NowPlayingUpdate (0x5001), each value as last sent: an update carries only what changed. */
data class NowPlaying(
    val title: String? = null,
    val artist: String? = null,
    val album: String? = null,
    val albumArtist: String? = null,
    val genre: String? = null,
    val composer: String? = null,
    val durationMillis: Long? = null,
    val elapsedMillis: Long? = null,
    val status: PlaybackStatus? = null,
    /** Upstream's reading: true only while [status] is PLAYING. */
    val playing: Boolean = false,
    val trackNumber: Int? = null,
    val trackCount: Int? = null,
    val discNumber: Int? = null,
    val discCount: Int? = null,
    val queueIndex: Long? = null,
    val queueCount: Long? = null,
    val shuffle: ShuffleMode? = null,
    val repeat: RepeatMode? = null,
    val appName: String? = null,
    val appBundleId: String? = null,
    /** The file transfer that brings this item's artwork, matching [Artwork.transferId]. */
    val artworkTransferId: Int? = null,
)

/** Album artwork from the iAP2 file transfer (Iap2ArtworkTransfer); empty [bytes] clear it. */
class Artwork(val transferId: Int, bytes: ByteArray) {
    private val data = bytes.copyOf()

    /** The image as the iPhone sent it, usually JPEG. */
    val bytes: ByteArray get() = data.copyOf()

    val size: Int get() = data.size

    override fun equals(other: Any?) = other is Artwork && transferId == other.transferId && data.contentEquals(other.data)

    override fun hashCode() = 31 * transferId + data.contentHashCode()

    override fun toString() = "Artwork(transferId=$transferId, ${data.size} bytes)"
}

/** One call from CallStateUpdate (0x4155). */
data class Call(
    /** The call's UUID, or its remote id when the iPhone sends none. */
    val id: String,
    /** The number or address of the other side. */
    val remoteId: String? = null,
    val displayName: String? = null,
    val status: CallStatus? = null,
    val direction: CallDirection? = null,
    /** The number's label in the contact, such as "mobile". */
    val label: String? = null,
    val service: CallService? = null,
    val conferenced: Boolean? = null,
    val conferenceGroup: Int? = null,
    val disconnectReason: DisconnectReason? = null,
) {
    /** What a call card shows: the contact's name, else the number. */
    val name: String? get() = displayName ?: remoteId

    /** The call is over, or about to be: it leaves the bus's calls. */
    val ended: Boolean get() = status == CallStatus.DISCONNECTED || status == CallStatus.DISCONNECTING
}

/** CommunicationsUpdate (0x4158), each value as last sent. */
data class Communications(
    /** Signal bars as the iPhone counts them. */
    val signalStrength: Int? = null,
    val registration: RegistrationStatus? = null,
    val airplaneMode: Boolean? = null,
    /** The carrier's name, or its id ("UA-KYIVSTAR") when the iPhone leaves the name empty. */
    val carrier: String? = null,
    val telephonyEnabled: Boolean? = null,
    val faceTimeAudioEnabled: Boolean? = null,
    val faceTimeVideoEnabled: Boolean? = null,
    val muted: Boolean? = null,
    val callCount: Int? = null,
    val voicemailCount: Int? = null,
)

/** The iPhone's battery from PowerUpdate (0xAE01). */
data class Power(
    /** Percent, as sent: an iPhone 15 on iOS 18.6 reported 38 while it showed 37 %. */
    val chargeLevel: Int? = null,
    val chargingState: BatteryChargingState? = null,
    val externalChargerConnected: Boolean? = null,
) {
    val batteryPercent: Int? get() = chargeLevel?.coerceIn(0, 100)
}

/** Everything the bus knows, as of its last change. */
data class PhoneDataSnapshot(
    val route: RouteGuidance? = null,
    /**
     * The current lane event: the one whose index [RouteGuidance.laneGuidanceIndex] names. Whether to show it is
     * [RouteGuidance.laneGuidanceShowing]'s call.
     */
    val lanes: LaneGuidance? = null,
    val nowPlaying: NowPlaying? = null,
    val artwork: Artwork? = null,
    /** Calls that have not ended, in the order they started. */
    val calls: List<Call> = emptyList(),
    val communications: Communications? = null,
    val power: Power? = null,
)

/** One change on the bus; the snapshot already holds it when listeners hear of it. */
sealed interface PhoneDataEvent {
    /** The route changed; null when the phone dropped it (the iPhone never does: it sends its end as a state). */
    data class RouteChanged(val route: RouteGuidance?) : PhoneDataEvent
    /** The current lane event changed; null when there is none. */
    data class LanesChanged(val lanes: LaneGuidance?) : PhoneDataEvent
    data class NowPlayingChanged(val nowPlaying: NowPlaying?) : PhoneDataEvent
    data class ArtworkChanged(val artwork: Artwork) : PhoneDataEvent
    /** [call] as updated, also when it ended and has left [calls]. */
    data class CallChanged(val call: Call, val calls: List<Call>) : PhoneDataEvent
    data class CommunicationsChanged(val communications: Communications?) : PhoneDataEvent
    data class PowerChanged(val power: Power?) : PhoneDataEvent
    /** The phone's session ended: the snapshot is empty again. */
    data object Cleared : PhoneDataEvent
}
