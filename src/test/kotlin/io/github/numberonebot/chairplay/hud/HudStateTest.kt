package io.github.numberonebot.chairplay.hud

import io.github.numberonebot.chairplay.data.Call
import io.github.numberonebot.chairplay.data.CallDirection
import io.github.numberonebot.chairplay.data.CallStatus
import io.github.numberonebot.chairplay.data.DistanceUnits
import io.github.numberonebot.chairplay.data.Lane
import io.github.numberonebot.chairplay.data.LaneGuidance
import io.github.numberonebot.chairplay.data.Maneuver
import io.github.numberonebot.chairplay.data.NowPlaying
import io.github.numberonebot.chairplay.data.PhoneDataSnapshot
import io.github.numberonebot.chairplay.data.PlaybackStatus
import io.github.numberonebot.chairplay.data.RouteGuidance
import io.github.numberonebot.chairplay.data.RouteGuidanceState
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test
import java.time.ZoneOffset

class HudStateTest {
    private val zone = ZoneOffset.ofHours(3)

    // As the data log of a Google Maps route showed it.
    private val route = RouteGuidance(
        state = RouteGuidanceState.ROUTE_SET,
        currentRoad = "вул. Хрещатик",
        destination = "Олімпійський",
        arrivalEpochSeconds = 1_792_622_969, // 2026-10-21 22:49:29 UTC, 01:49 at UTC+3
        remainingSeconds = 1_320,
        remainingText = "15",
        remainingUnits = DistanceUnits.KILOMETERS,
        distanceToManeuverText = "150",
        distanceToManeuverUnits = DistanceUnits.METERS,
        nextManeuver = Maneuver(index = 1, typeCode = 2, description = "вул. Липська", afterRoad = "вул. Липська"),
    )

    @Test
    fun `a route shows the next maneuver and the arrival`() {
        val hud = hudState(PhoneDataSnapshot(route = route), zone)

        val maneuver = hud.maneuver!!
        assertEquals("вул. Липська", maneuver.road)
        assertEquals(2, maneuver.typeCode)
        assertEquals("01:49", hud.arrival!!.time)
        assertEquals("Олімпійський", hud.arrival!!.destination)
    }

    // The maneuver renderer draws from the iPhone's own maneuver and the metres to it.
    @Test
    fun `the maneuver keeps what the renderer needs`() {
        val hud = hudState(PhoneDataSnapshot(route = route.copy(distanceToManeuverMeters = 152)), zone)

        assertEquals(route.nextManeuver, hud.maneuver!!.next)
        assertEquals(152L, hud.maneuver!!.distanceMeters)
    }

    // The MHI2Q bridge's gate (BAPBridge.laneGuidanceVisible): the iPhone's laneGuidanceShowing alone, at any distance.
    @Test
    fun `lanes show while the iPhone says so`() {
        val lanes = LaneGuidance(4, listOf(Lane(0, 2, listOf(0))))
        fun hudLanes(showing: Boolean?) =
            hudState(PhoneDataSnapshot(route = route.copy(laneGuidanceIndex = 4, laneGuidanceShowing = showing), lanes = lanes), zone)
                .maneuver!!.lanes

        assertEquals(lanes, hudLanes(true))
        assertNull(hudLanes(false))
        assertNull(hudLanes(null))
    }

    private val song = NowPlaying(title = "Gasoline", artist = "Twin Fin", playing = true)

    // Without guidance the HUD shows the song alone; with it, the route and the song side by side. The iPhone does
    // not say which app is on the CarPlay screen: visibleInApp stayed true while the music app was in front.
    @Test
    fun `with guidance the route and the song show side by side`() {
        val hud = hudState(PhoneDataSnapshot(route = route.copy(visibleInApp = true), nowPlaying = song), zone)

        assertNotNull(hud.maneuver)
        assertEquals("Gasoline", hud.media!!.title)
    }

    // Apple Maps starts a route away from it with "proceed to route", Google Maps with "route set".
    @Test
    fun `guidance toward the route counts as guidance`() {
        for (state in listOf(RouteGuidanceState.PROCEED_TO_ROUTE, RouteGuidanceState.LOCATING, RouteGuidanceState.REROUTING)) {
            assertNotNull(state.name, hudState(PhoneDataSnapshot(route = route.copy(state = state)), zone).maneuver)
        }
        for (state in listOf(RouteGuidanceState.LOADING, RouteGuidanceState.ARRIVED)) {
            assertNull(state.name, hudState(PhoneDataSnapshot(route = route.copy(state = state)), zone).maneuver)
        }
    }

    @Test
    fun `without guidance only the song shows`() {
        val hud = hudState(PhoneDataSnapshot(route = route.copy(state = RouteGuidanceState.NO_ROUTE_SET), nowPlaying = song), zone)

        assertNull(hud.maneuver)
        assertEquals("Gasoline", hud.media!!.title)
    }

    @Test
    fun `no route shows no maneuver`() {
        val hud = hudState(PhoneDataSnapshot(route = route.copy(state = RouteGuidanceState.NO_ROUTE_SET)), zone)

        assertNull(hud.maneuver)
        assertNull(hud.arrival)
    }

    @Test
    // As the iPhone's Now Playing lays them out: the artist and the album each on a line of its own.
    fun `the song shows title, artist and album, its app and how far it has played`() {
        val song = NowPlaying(
            title = "Gasoline", artist = "Twin Fin", album = "Gasoline", durationMillis = 166_721, elapsedMillis = 41_680,
            status = PlaybackStatus.PLAYING, playing = true, appName = "YouTube Music",
        )
        val media = hudState(PhoneDataSnapshot(nowPlaying = song), zone).media!!

        assertEquals("Gasoline", media.title)
        assertEquals("Twin Fin", media.artist)
        assertEquals("Gasoline", media.album)
        assertEquals("YouTube Music", media.source)
        assertEquals(0.25f, media.progress!!, 0.001f)
        assertEquals(true, media.playing)
    }

    @Test
    fun `a ringing or active call goes on top`() {
        val ringing = Call(id = "1", displayName = "Mom", status = CallStatus.RINGING, direction = CallDirection.INCOMING)
        assertNotNull(hudState(PhoneDataSnapshot(calls = listOf(ringing)), zone).call)
        assertNotNull(hudState(PhoneDataSnapshot(calls = listOf(ringing.copy(status = CallStatus.ACTIVE))), zone).call)
        assertNull(hudState(PhoneDataSnapshot(calls = listOf(ringing.copy(status = CallStatus.DISCONNECTED))), zone).call)
    }

    // Text from the iPhone is cut before it reaches the drawing, which measures it.
    @Test
    fun `very long text is cut`() {
        val media = hudState(PhoneDataSnapshot(nowPlaying = NowPlaying(title = "x".repeat(10_000))), zone).media!!
        assertEquals(256, media.title!!.length)
    }

    @Test
    fun `an empty snapshot shows nothing`() {
        assertEquals(HudState(), hudState(PhoneDataSnapshot(), zone))
    }
}
