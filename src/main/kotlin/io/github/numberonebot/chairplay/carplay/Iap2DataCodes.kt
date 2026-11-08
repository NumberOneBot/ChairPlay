package io.github.numberonebot.chairplay.carplay

import io.github.numberonebot.chairplay.data.BatteryChargingState
import io.github.numberonebot.chairplay.data.CallDirection
import io.github.numberonebot.chairplay.data.CallService
import io.github.numberonebot.chairplay.data.CallStatus
import io.github.numberonebot.chairplay.data.DisconnectReason
import io.github.numberonebot.chairplay.data.DistanceUnits
import io.github.numberonebot.chairplay.data.DrivingSide
import io.github.numberonebot.chairplay.data.JunctionType
import io.github.numberonebot.chairplay.data.LaneStatus
import io.github.numberonebot.chairplay.data.ManeuverState
import io.github.numberonebot.chairplay.data.ManeuverType
import io.github.numberonebot.chairplay.data.PlaybackStatus
import io.github.numberonebot.chairplay.data.RegistrationStatus
import io.github.numberonebot.chairplay.data.RepeatMode
import io.github.numberonebot.chairplay.data.RouteGuidanceState
import io.github.numberonebot.chairplay.data.ShuffleMode

/** One enumeration of Apple's iAP2 spec: each value's code on the wire, both ways. */
internal class Iap2Codes<E : Enum<E>>(vararg pairs: Pair<Int, E>) {
    private val byCode: Map<Int, E> = mapOf(*pairs)
    private val byValue: Map<E, Int> = pairs.associate { (code, value) -> value to code }

    /** Null for a code the spec, as known here, does not list. */
    fun of(code: Int?): E? = code?.let(byCode::get)

    fun code(value: E): Int = byValue.getValue(value)

    /** The data log's names for the codes. */
    val names: Map<Int, String> get() = byCode.mapValues { it.value.name }
}

/** The wire codes of the phone data's enumerations, as the iPhone sends them. */
internal object Iap2DataCodes {
    val routeGuidanceState = Iap2Codes(
        0 to RouteGuidanceState.NO_ROUTE_SET, 1 to RouteGuidanceState.ROUTE_SET, 2 to RouteGuidanceState.ARRIVED,
        3 to RouteGuidanceState.LOADING, 4 to RouteGuidanceState.LOCATING, 5 to RouteGuidanceState.REROUTING,
        6 to RouteGuidanceState.PROCEED_TO_ROUTE,
    )
    val maneuverState = Iap2Codes(
        0 to ManeuverState.CONTINUE, 1 to ManeuverState.INITIAL, 2 to ManeuverState.PREPARE, 3 to ManeuverState.EXECUTE,
    )
    val distanceUnits = Iap2Codes(
        0 to DistanceUnits.KILOMETERS, 1 to DistanceUnits.MILES, 2 to DistanceUnits.METERS, 3 to DistanceUnits.YARDS,
        4 to DistanceUnits.FEET,
    )
    val drivingSide = Iap2Codes(0 to DrivingSide.RIGHT, 1 to DrivingSide.LEFT)
    val junctionType = Iap2Codes(0 to JunctionType.INTERSECTION, 1 to JunctionType.ROUNDABOUT)

    /** The app's maneuver codes are Apple's. */
    val maneuverType = Iap2Codes(*ManeuverType.entries.map { it.code to it }.toTypedArray())

    /** The app's lane codes are Apple's. */
    val laneStatus = Iap2Codes(*LaneStatus.entries.map { it.code to it }.toTypedArray())
    val playbackStatus = Iap2Codes(
        0 to PlaybackStatus.STOPPED, 1 to PlaybackStatus.PLAYING, 2 to PlaybackStatus.PAUSED,
        3 to PlaybackStatus.SEEK_FORWARD, 4 to PlaybackStatus.SEEK_BACKWARD,
    )
    val shuffleMode = Iap2Codes(0 to ShuffleMode.OFF, 1 to ShuffleMode.SONGS, 2 to ShuffleMode.ALBUMS)
    val repeatMode = Iap2Codes(0 to RepeatMode.OFF, 1 to RepeatMode.ONE, 2 to RepeatMode.ALL)
    val callStatus = Iap2Codes(
        0 to CallStatus.DISCONNECTED, 1 to CallStatus.SENDING, 2 to CallStatus.RINGING, 3 to CallStatus.CONNECTING,
        4 to CallStatus.ACTIVE, 5 to CallStatus.HELD, 6 to CallStatus.DISCONNECTING,
    )
    val callDirection = Iap2Codes(0 to CallDirection.UNKNOWN, 1 to CallDirection.INCOMING, 2 to CallDirection.OUTGOING)
    val callService = Iap2Codes(
        0 to CallService.UNKNOWN, 1 to CallService.TELEPHONY, 2 to CallService.FACETIME_AUDIO,
        3 to CallService.FACETIME_VIDEO,
    )
    val disconnectReason = Iap2Codes(0 to DisconnectReason.ENDED, 1 to DisconnectReason.DECLINED, 2 to DisconnectReason.FAILED)
    val registrationStatus = Iap2Codes(
        0 to RegistrationStatus.UNKNOWN, 1 to RegistrationStatus.NOT_REGISTERED, 2 to RegistrationStatus.SEARCHING,
        3 to RegistrationStatus.DENIED, 4 to RegistrationStatus.REGISTERED_HOME, 5 to RegistrationStatus.REGISTERED_ROAMING,
        6 to RegistrationStatus.EMERGENCY_CALLS_ONLY,
    )
    val batteryChargingState = Iap2Codes(
        0 to BatteryChargingState.DISABLED, 1 to BatteryChargingState.CHARGING, 2 to BatteryChargingState.CHARGED,
    )
}
