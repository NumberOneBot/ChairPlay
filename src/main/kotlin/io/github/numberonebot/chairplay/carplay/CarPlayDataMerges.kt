package io.github.numberonebot.chairplay.carplay

import io.github.numberonebot.chairplay.data.Call
import io.github.numberonebot.chairplay.data.Communications
import io.github.numberonebot.chairplay.data.Lane
import io.github.numberonebot.chairplay.data.LaneGuidance
import io.github.numberonebot.chairplay.data.Maneuver
import io.github.numberonebot.chairplay.data.NowPlaying
import io.github.numberonebot.chairplay.data.Power
import io.github.numberonebot.chairplay.data.RouteGuidance

// Updates carry only what changed: an absent parameter keeps the previous value, a present empty string clears it.

internal fun DecodedFields.string(name: String, previous: String?): String? =
    if (has(name)) (values[name] as String).trim().ifEmpty { null } else previous

internal fun DecodedFields.long(name: String, previous: Long?): Long? =
    if (has(name)) (values[name] as Number).toLong() else previous

internal fun DecodedFields.int(name: String, previous: Int?): Int? =
    if (has(name)) (values[name] as Number).toInt() else previous

internal fun DecodedFields.bool(name: String, previous: Boolean?): Boolean? =
    if (has(name)) values[name] as Boolean else previous

@Suppress("UNCHECKED_CAST")
internal fun DecodedFields.ints(name: String, previous: List<Int>): List<Int> =
    if (has(name)) values[name] as List<Int> else previous

internal fun <E : Enum<E>> DecodedFields.coded(name: String, codes: Iap2Codes<E>, previous: E?): E? =
    if (has(name)) codes.of((values[name] as CodedValue).code) else previous

internal fun DecodedFields.group(name: String): DecodedFields? = values[name] as? DecodedFields

internal fun RouteGuidance.merged(fields: DecodedFields) = copy(
    state = fields.coded("routeGuidanceState", Iap2DataCodes.routeGuidanceState, state),
    maneuverState = fields.coded("maneuverState", Iap2DataCodes.maneuverState, maneuverState),
    currentRoad = fields.string("currentRoadName", currentRoad),
    destination = fields.string("destinationName", destination),
    arrivalEpochSeconds = fields.long("estimatedTimeOfArrival", arrivalEpochSeconds),
    remainingSeconds = fields.long("timeRemainingToDestination", remainingSeconds),
    remainingMeters = fields.long("distanceRemaining", remainingMeters),
    remainingText = fields.string("distanceRemainingDisplayStr", remainingText),
    remainingUnits = fields.coded("distanceRemainingDisplayUnits", Iap2DataCodes.distanceUnits, remainingUnits),
    distanceToManeuverMeters = fields.long("distanceToNextManeuver", distanceToManeuverMeters),
    distanceToManeuverText = fields.string("distanceToNextManeuverDisplayStr", distanceToManeuverText),
    distanceToManeuverUnits = fields.coded("distanceToNextManeuverDisplayUnits", Iap2DataCodes.distanceUnits, distanceToManeuverUnits),
    currentManeuverIndexes = fields.ints("currentManeuverList", currentManeuverIndexes),
    maneuverCount = fields.int("maneuverCount", maneuverCount),
    visibleInApp = fields.bool("visibleInApp", visibleInApp),
    laneGuidanceIndex = fields.int("laneGuidanceIndex", laneGuidanceIndex),
    laneGuidanceTotal = fields.int("laneGuidanceTotalCount", laneGuidanceTotal),
    laneGuidanceShowing = fields.bool("laneGuidanceShowing", laneGuidanceShowing),
    sourceName = fields.string("sourceName", sourceName),
)

internal fun Maneuver.merged(fields: DecodedFields) = copy(
    typeCode = if (fields.has("maneuverType")) (fields.values["maneuverType"] as CodedValue).code else typeCode,
    description = fields.string("maneuverDescription", description),
    afterRoad = fields.string("afterManeuverRoadName", afterRoad),
    distanceBetweenMeters = fields.long("distanceBetweenManeuver", distanceBetweenMeters),
    distanceBetweenText = fields.string("distanceBetweenManeuverDisplayStr", distanceBetweenText),
    distanceBetweenUnits = fields.coded("distanceBetweenManeuverDisplayUnits", Iap2DataCodes.distanceUnits, distanceBetweenUnits),
    drivingSide = fields.coded("drivingSide", Iap2DataCodes.drivingSide, drivingSide),
    junctionType = fields.coded("junctionType", Iap2DataCodes.junctionType, junctionType),
    junctionAngles = fields.ints("junctionElementAngles", junctionAngles),
    exitAngle = fields.int("junctionElementExitAngle", exitAngle),
)

/** A lane update describes all its lanes. */
internal fun laneGuidance(fields: DecodedFields) = LaneGuidance(
    index = fields.int("laneGuidanceIndex", null),
    lanes = (fields.values["laneInformation"] as? List<*>).orEmpty().filterIsInstance<DecodedFields>().map {
        Lane(
            position = it.int("position", null),
            statusCode = (it.values["status"] as? CodedValue)?.code,
            angles = it.ints("angles", emptyList()),
            highlightAngle = it.int("highlightAngle", null),
        )
    },
    description = fields.string("laneGuidanceDescription", null),
)

/** What upstream's CarPlayPlaybackStatus does not read; the decoder takes the rest from it. */
internal fun NowPlaying.merged(fields: DecodedFields): NowPlaying {
    val item = fields.group("mediaItem")
    val playback = fields.group("playback")
    var next = this
    if (item != null) {
        next = next.copy(
            albumArtist = item.string("albumArtist", albumArtist),
            genre = item.string("genre", genre),
            composer = item.string("composer", composer),
            trackNumber = item.int("albumTrackNumber", trackNumber),
            trackCount = item.int("albumTrackCount", trackCount),
            discNumber = item.int("albumDiscNumber", discNumber),
            discCount = item.int("albumDiscCount", discCount),
        )
    }
    if (playback != null) {
        next = next.copy(
            status = playback.coded("playbackStatus", Iap2DataCodes.playbackStatus, status),
            queueIndex = playback.long("queueIndex", queueIndex),
            queueCount = playback.long("queueCount", queueCount),
            shuffle = playback.coded("shuffleMode", Iap2DataCodes.shuffleMode, shuffle),
            repeat = playback.coded("repeatMode", Iap2DataCodes.repeatMode, repeat),
            appBundleId = playback.string("appBundleId", appBundleId),
        )
    }
    return next
}

internal fun Call.merged(fields: DecodedFields) = copy(
    remoteId = fields.string("remoteId", remoteId),
    displayName = fields.string("displayName", displayName),
    status = fields.coded("status", Iap2DataCodes.callStatus, status),
    direction = fields.coded("direction", Iap2DataCodes.callDirection, direction),
    label = fields.string("label", label),
    service = fields.coded("service", Iap2DataCodes.callService, service),
    conferenced = fields.bool("isConferenced", conferenced),
    conferenceGroup = fields.int("conferenceGroup", conferenceGroup),
    disconnectReason = fields.coded("disconnectReason", Iap2DataCodes.disconnectReason, disconnectReason),
)

internal fun Communications.merged(fields: DecodedFields) = copy(
    signalStrength = fields.int("signalStrength", signalStrength),
    registration = fields.coded("registrationStatus", Iap2DataCodes.registrationStatus, registration),
    airplaneMode = fields.bool("airplaneModeStatus", airplaneMode),
    carrier = fields.string("carrierName", null)?.ifEmpty { null } ?: fields.string("carrierId", null) ?: carrier,
    telephonyEnabled = fields.bool("telephonyEnabled", telephonyEnabled),
    faceTimeAudioEnabled = fields.bool("faceTimeAudioEnabled", faceTimeAudioEnabled),
    faceTimeVideoEnabled = fields.bool("faceTimeVideoEnabled", faceTimeVideoEnabled),
    muted = fields.bool("muteStatus", muted),
    callCount = fields.int("currentCallCount", callCount),
    voicemailCount = fields.int("newVoicemailCount", voicemailCount),
)

internal fun Power.merged(fields: DecodedFields) = copy(
    chargeLevel = fields.int("batteryChargeLevel", chargeLevel),
    chargingState = fields.coded("batteryChargingState", Iap2DataCodes.batteryChargingState, chargingState),
    externalChargerConnected = fields.bool("isExternalChargerConnected", externalChargerConnected),
)
