package io.github.numberonebot.chairplay.carplay

import com.shilapi.xcertplay.iap2.message.Iap2Messages
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** CarPlayDataMessages: the route guidance, maneuver and lane guidance updates as they decode. */
class CarPlayDataMessagesRouteTest {
    @Test
    fun `route guidance update decodes every documented field`() {
        val frame = Iap2Messages.buildRaw(0x5201) {
            u16List(0, listOf(42))
            u8(1, 1)
            u8(2, 2)
            string(3, "Main Street")
            string(4, "Home")
            u64(5, 1_790_000_000L)
            u64(6, 600)
            u32(7, 5_400)
            string(8, "5.4")
            u8(9, 0)
            u32(10, 150)
            string(11, "150")
            u8(12, 2)
            u16List(13, listOf(3, 4))
            u16(14, 12)
            bool(15, true)
            u16(16, 1)
            u16(17, 3)
            bool(18, true)
            string(19, "Maps")
            bool(20, true)
        }

        val fields = CarPlayDataMessages.decode(frame)!!

        assertEquals(
            mapOf(
                "displayComponentIds" to listOf(42),
                "routeGuidanceState" to CodedValue(1, "ROUTE_SET"),
                "maneuverState" to CodedValue(2, "PREPARE"),
                "currentRoadName" to "Main Street",
                "destinationName" to "Home",
                "estimatedTimeOfArrival" to 1_790_000_000L,
                "timeRemainingToDestination" to 600L,
                "distanceRemaining" to 5_400L,
                "distanceRemainingDisplayStr" to "5.4",
                "distanceRemainingDisplayUnits" to CodedValue(0, "KILOMETERS"),
                "distanceToNextManeuver" to 150L,
                "distanceToNextManeuverDisplayStr" to "150",
                "distanceToNextManeuverDisplayUnits" to CodedValue(2, "METERS"),
                "currentManeuverList" to listOf(3, 4),
                "maneuverCount" to 12,
                "visibleInApp" to true,
                "laneGuidanceIndex" to 1,
                "laneGuidanceTotalCount" to 3,
                "laneGuidanceShowing" to true,
                "sourceName" to "Maps",
                "sourceSupportsRouteGuidance" to true,
            ),
            fields.values,
        )
        assertTrue(fields.unknown.isEmpty())
        assertEquals("RouteGuidanceUpdate", CarPlayDataMessages.name(0x5201))
    }

    // The junction's road angles arrive as one parameter per road.
    @Test
    fun `maneuver update decodes the junction angles`() {
        val frame = Iap2Messages.buildRaw(0x5202) {
            u16(1, 3)
            string(2, "Turn right onto Oak Avenue")
            u8(3, 2)
            string(4, "Oak Avenue")
            u32(5, 300)
            u8(8, 0)
            u8(9, 1)
            i16(10, -90)
            i16(10, 0)
            i16(10, 90)
            i16(11, 90)
        }

        val values = CarPlayDataMessages.decode(frame)!!.values

        assertEquals(3, values["index"])
        assertEquals("Turn right onto Oak Avenue", values["maneuverDescription"])
        assertEquals(CodedValue(2, "RIGHT_TURN"), values["maneuverType"])
        assertEquals("Oak Avenue", values["afterManeuverRoadName"])
        assertEquals(300L, values["distanceBetweenManeuver"])
        assertEquals(CodedValue(0, "RIGHT"), values["drivingSide"])
        assertEquals(CodedValue(1, "ROUNDABOUT"), values["junctionType"])
        assertEquals(listOf(-90, 0, 90), values["junctionElementAngles"])
        assertEquals(90, values["junctionElementExitAngle"])
    }

    // The lane layout of the MHI2Q project's parser (hook/routeguidance/rgd_tlv.c, from the head unit's own
    // libesoiap2 parseLaneInformation): position, status, the angles one per parameter, the highlighted angle.
    @Test
    fun `lane guidance decodes its lanes and keeps the raw bytes`() {
        val frame = Iap2Messages.buildRaw(0x5204) {
            u16(1, 2)
            group(2) {
                u16(0, 0)
                u8(1, 0)
                i16(2, -45)
            }
            group(2) {
                u16(0, 1)
                u8(1, 2)
                i16(2, 0)
                i16(2, 90)
                i16(3, 90)
            }
            string(3, "Use the right lane")
        }

        val values = CarPlayDataMessages.decode(frame)!!.values
        val lanes = values["laneInformation"] as List<*>

        assertEquals(2, values["laneGuidanceIndex"])
        assertEquals(2, lanes.size)
        assertEquals(
            mapOf("position" to 0, "status" to CodedValue(0, "NOT_GOOD"), "angles" to listOf(-45)),
            (lanes[0] as DecodedFields).values,
        )
        assertEquals(
            mapOf("position" to 1, "status" to CodedValue(2, "PREFERRED"), "angles" to listOf(0, 90), "highlightAngle" to 90),
            (lanes[1] as DecodedFields).values,
        )
        assertEquals("Use the right lane", values["laneGuidanceDescription"])
        assertTrue(CarPlayDataMessages.keepsRawBytes(0x5204))
        assertFalse(CarPlayDataMessages.keepsRawBytes(0x5201))
    }

    @Test
    fun `a lane's angles may also come packed into one parameter`() {
        val frame = Iap2Messages.buildRaw(0x5204) {
            u16(1, 0)
            group(2) {
                u16(0, 0)
                u8(1, 1)
                raw(2, byteArrayOf(0, 0, 0xFF.toByte(), 0xD3.toByte()))
                i16(2, 45)
            }
        }

        val lane = (CarPlayDataMessages.decode(frame)!!.values["laneInformation"] as List<*>).single() as DecodedFields

        assertEquals(listOf(0, -45, 45), lane.values["angles"])
    }
}
