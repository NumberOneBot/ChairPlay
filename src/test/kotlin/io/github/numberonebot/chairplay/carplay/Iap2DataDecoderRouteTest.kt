package io.github.numberonebot.chairplay.carplay

import io.github.numberonebot.chairplay.data.DistanceUnits
import io.github.numberonebot.chairplay.data.DrivingSide
import io.github.numberonebot.chairplay.data.JunctionType
import io.github.numberonebot.chairplay.data.Lane
import io.github.numberonebot.chairplay.data.LaneGuidance
import io.github.numberonebot.chairplay.data.LaneStatus
import io.github.numberonebot.chairplay.data.Maneuver
import io.github.numberonebot.chairplay.data.ManeuverState
import io.github.numberonebot.chairplay.data.ManeuverType
import io.github.numberonebot.chairplay.data.PhoneDataEvent.LanesChanged
import io.github.numberonebot.chairplay.data.PhoneDataEvent.RouteChanged
import io.github.numberonebot.chairplay.data.RouteGuidanceState
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** Iap2DataDecoder: route guidance, its maneuvers, and the lane guidance it makes current. */
class Iap2DataDecoderRouteTest : Iap2DataDecoderFixture() {
    private fun maneuver(index: Int, type: Int, road: String) = frame(0x5202) {
        u16(1, index)
        u8(3, type)
        string(4, road)
        u8(8, 0)
    }

    @Test
    fun `route and maneuver updates make the next maneuver`() {
        frame(0x5202) {
            u16(1, 3)
            string(2, "Turn right onto Oak Avenue")
            u8(3, 2)
            string(4, "Oak Avenue")
            u32(5, 300)
            u8(8, 1)
            u8(9, 0)
            i16(10, -90)
            i16(10, 90)
            i16(11, 90)
        }
        maneuver(4, 12, "")
        frame(0x5201) {
            u8(1, 1)
            u8(2, 2)
            string(3, "Main Street")
            string(4, "Home")
            u64(5, 1_790_000_000L)
            u64(6, 600)
            u32(7, 5_400)
            u32(10, 150)
            string(11, "150")
            u8(12, 2)
            u16List(13, listOf(3, 4))
            string(19, "Maps")
        }

        val route = last<RouteChanged>().route!!
        val next = Maneuver(
            index = 3, typeCode = 2, description = "Turn right onto Oak Avenue", afterRoad = "Oak Avenue",
            distanceBetweenMeters = 300, drivingSide = DrivingSide.LEFT, junctionType = JunctionType.INTERSECTION,
            junctionAngles = listOf(-90, 90), exitAngle = 90,
        )
        assertEquals(next, route.nextManeuver)
        assertEquals(ManeuverType.RIGHT_TURN, route.nextManeuver!!.type)
        assertEquals(listOf(next, Maneuver(4, typeCode = 12, drivingSide = DrivingSide.RIGHT)), route.currentManeuvers)
        assertEquals(RouteGuidanceState.ROUTE_SET, route.state)
        assertEquals(ManeuverState.PREPARE, route.maneuverState)
        assertEquals("Main Street", route.currentRoad)
        assertEquals("Home", route.destination)
        assertEquals(1_790_000_000L, route.arrivalEpochSeconds)
        assertEquals(600L, route.remainingSeconds)
        assertEquals(5_400L, route.remainingMeters)
        assertEquals(150L, route.distanceToManeuverMeters)
        assertEquals("150", route.distanceToManeuverText)
        assertEquals(DistanceUnits.METERS, route.distanceToManeuverUnits)
        assertEquals(listOf(3, 4), route.currentManeuverIndexes)
        assertEquals("Maps", route.sourceName)
        assertEquals(route, bus.snapshot().route)
    }

    @Test
    fun `route updates carry only what changed`() {
        maneuver(0, 1, "Elm Street")
        frame(0x5201) {
            u8(1, 1)
            string(3, "Main Street")
            u32(10, 500)
            u16List(13, listOf(0))
        }
        frame(0x5201) {
            u32(10, 120)
            bool(15, false)
        }

        val route = last<RouteChanged>().route!!
        assertEquals(120L, route.distanceToManeuverMeters)
        // Whether the navigation app shows the guidance on the CarPlay screen now.
        assertEquals(false, route.visibleInApp)
        assertEquals("Main Street", route.currentRoad)
        assertEquals(0, route.nextManeuver!!.index)
    }

    // The iPhone sends an empty current list every few seconds; upstream's BydHudRouteState keeps the turn for 3 s.
    @Test
    fun `an empty current list keeps the next maneuver for a while`() {
        maneuver(0, 1, "Elm Street")
        frame(0x5201) {
            u8(1, 1)
            u16List(13, listOf(0))
        }
        frame(0x5201) { u16List(13, emptyList()) }
        assertEquals(0, bus.snapshot().route!!.nextManeuver!!.index)

        now += 3_000_000_000L

        assertNull(bus.snapshot().route!!.nextManeuver)
        assertNull(last<RouteChanged>().route!!.nextManeuver)
    }

    @Test
    fun `arrival ends the route`() {
        maneuver(0, 12, "")
        frame(0x5201) {
            u8(1, 1)
            string(3, "Main Street")
            u16List(13, listOf(0))
        }
        frame(0x5201) { u8(1, 2) }

        val route = last<RouteChanged>().route!!
        assertEquals(RouteGuidanceState.ARRIVED, route.state)
        assertNull(route.nextManeuver)
        assertTrue(route.currentManeuvers.isEmpty())
        assertNull(route.currentRoad)
    }

    private fun lanes(index: Int?, description: String = "Lanes $index") = frame(0x5204) {
        index?.let { u16(1, it) }
        group(2) {
            u16(0, 0)
            u8(1, 2)
            i16(2, -90)
            i16(3, -90)
        }
        group(2) {
            u16(0, 1)
            u8(1, 1)
            i16(2, 0)
        }
        string(3, description)
    }

    private fun currentLanes(index: Int) = frame(0x5201) {
        u8(1, 1)
        u16(16, index)
        bool(18, true)
    }

    @Test
    fun `lane guidance becomes lanes once the route makes its event current`() {
        lanes(4, "Keep left")
        assertNull(bus.snapshot().lanes)
        assertTrue(events.filterIsInstance<LanesChanged>().isEmpty())

        currentLanes(4)

        val expected = LaneGuidance(
            4,
            listOf(Lane(0, LaneStatus.PREFERRED.code, listOf(-90), -90), Lane(1, LaneStatus.GOOD.code, listOf(0))),
            "Keep left",
        )
        assertEquals(expected, last<LanesChanged>().lanes)
        assertEquals(expected, bus.snapshot().lanes)
        assertEquals(LaneStatus.PREFERRED, expected.lanes[0].status)
        // The highlighted angle, else the first.
        assertEquals(listOf(-90, 0), expected.lanes.map { it.direction })
        assertEquals(true, bus.snapshot().route!!.laneGuidanceShowing)
    }

    // The MHI2Q project's lane cache (hook/routeguidance/rgd_hook.c): the iPhone sends a window of lane events ahead
    // of the route update that makes one current, and never needs one below the current index again.
    @Test
    fun `lane events wait for their turn and passed ones are dropped`() {
        lanes(4)
        lanes(5)
        currentLanes(4)
        assertEquals(4, bus.snapshot().lanes!!.index)

        currentLanes(5)
        assertEquals(5, last<LanesChanged>().lanes!!.index)

        currentLanes(4)
        assertNull(bus.snapshot().lanes)
        assertNull(last<LanesChanged>().lanes)
    }

    @Test
    fun `lane data without an event index is dropped`() {
        lanes(null)
        currentLanes(0)
        assertNull(bus.snapshot().lanes)
    }

    @Test
    fun `a full lane cache drops its oldest event but keeps the current one`() {
        lanes(0)
        currentLanes(0)
        for (index in 1..32) lanes(index)

        // Event 0 stayed while it was current; event 1, the oldest of the others, went to make room.
        assertEquals(0, bus.snapshot().lanes!!.index)
        currentLanes(1)
        assertNull(bus.snapshot().lanes)
        currentLanes(2)
        assertEquals(2, bus.snapshot().lanes!!.index)
    }

    @Test
    fun `the end of a route forgets its lane events`() {
        lanes(3)
        frame(0x5201) { u8(1, 0) }
        currentLanes(3)
        assertNull(bus.snapshot().lanes)
    }
}
