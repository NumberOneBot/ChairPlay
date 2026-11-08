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
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/** Iap2DataCodes: every value of each enumeration has its own wire code, and the codes read back as the values. */
class Iap2DataCodesTest {
    private fun <E : Enum<E>> assertCovers(codes: Iap2Codes<E>, values: List<E>) {
        val wire = values.map { codes.code(it) }
        assertEquals(values.toString(), values.size, wire.toSet().size)
        assertEquals(values, wire.map { codes.of(it) })
        assertEquals(values.map { it.name }, wire.map { codes.names[it] })
    }

    @Test
    fun `every table maps both ways`() {
        assertCovers(Iap2DataCodes.routeGuidanceState, RouteGuidanceState.entries)
        assertCovers(Iap2DataCodes.maneuverState, ManeuverState.entries)
        assertCovers(Iap2DataCodes.distanceUnits, DistanceUnits.entries)
        assertCovers(Iap2DataCodes.drivingSide, DrivingSide.entries)
        assertCovers(Iap2DataCodes.junctionType, JunctionType.entries)
        assertCovers(Iap2DataCodes.maneuverType, ManeuverType.entries)
        assertCovers(Iap2DataCodes.laneStatus, LaneStatus.entries)
        assertCovers(Iap2DataCodes.playbackStatus, PlaybackStatus.entries)
        assertCovers(Iap2DataCodes.shuffleMode, ShuffleMode.entries)
        assertCovers(Iap2DataCodes.repeatMode, RepeatMode.entries)
        assertCovers(Iap2DataCodes.callStatus, CallStatus.entries)
        assertCovers(Iap2DataCodes.callDirection, CallDirection.entries)
        assertCovers(Iap2DataCodes.callService, CallService.entries)
        assertCovers(Iap2DataCodes.disconnectReason, DisconnectReason.entries)
        assertCovers(Iap2DataCodes.registrationStatus, RegistrationStatus.entries)
        assertCovers(Iap2DataCodes.batteryChargingState, BatteryChargingState.entries)
    }

    // The app's maneuver and lane codes are Apple's: the HUD and the MHI2Q renderer draw by them.
    @Test
    fun `maneuver and lane codes are the app's own`() {
        for (type in ManeuverType.entries) assertEquals(type.code, Iap2DataCodes.maneuverType.code(type))
        for (status in LaneStatus.entries) assertEquals(status.code, Iap2DataCodes.laneStatus.code(status))
    }

    @Test
    fun `a code the spec does not list reads as nothing`() {
        assertNull(Iap2DataCodes.callStatus.of(7))
        assertNull(Iap2DataCodes.routeGuidanceState.of(null))
    }
}
