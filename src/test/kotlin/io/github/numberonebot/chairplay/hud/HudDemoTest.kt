package io.github.numberonebot.chairplay.hud

import io.github.numberonebot.chairplay.data.Artwork
import io.github.numberonebot.chairplay.data.Call
import io.github.numberonebot.chairplay.data.CallDirection
import io.github.numberonebot.chairplay.data.CallStatus
import io.github.numberonebot.chairplay.data.LaneStatus
import io.github.numberonebot.chairplay.data.ManeuverType
import io.github.numberonebot.chairplay.data.NowPlaying
import io.github.numberonebot.chairplay.data.PhoneDataSnapshot
import io.github.numberonebot.chairplay.data.RouteGuidance
import io.github.numberonebot.chairplay.data.RouteGuidanceState
import io.github.numberonebot.chairplay.hud.maneuver.ArrowProgress
import io.github.numberonebot.chairplay.hud.maneuver.arrowProgress
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class HudDemoTest {
    @Test
    fun `the demo drives toward each maneuver in turn and starts over`() {
        val demo = HudDemo(withMedia = false)
        val start = demo.snapshot(0).route!!
        assertEquals(RouteGuidanceState.ROUTE_SET, start.state)
        assertEquals(ManeuverType.LEFT_TURN.code, start.nextManeuver!!.typeCode)
        assertEquals(HudDemo.START_METERS, start.distanceToManeuverMeters)

        val near = demo.snapshot(HudDemo.STEP_MILLIS - 100).route!!
        assertEquals(0, near.nextManeuver!!.index)
        assertTrue("${near.distanceToManeuverMeters}", near.distanceToManeuverMeters!! < 20)

        val second = demo.snapshot(HudDemo.STEP_MILLIS).route!!.nextManeuver!!
        assertEquals(1, second.index)
        assertEquals(ManeuverType.ROUNDABOUT_EXIT_2.code, second.typeCode)

        val again = demo.snapshot(demo.lapMillis).route!!.nextManeuver!!
        assertEquals(ManeuverType.LEFT_TURN.code, again.typeCode)
        // A new lap has new indexes, so the renderer pushes the maneuver again.
        assertEquals(demo.steps, again.index)
    }

    @Test
    fun `the demo slows down toward each maneuver, so the arrow blinks for a while`() {
        val demo = HudDemo(withMedia = false)
        // Fast far away...
        assertTrue(demo.snapshot(1_000).route!!.distanceToManeuverMeters!! > 280)
        // ...and the blink's last 45 metres take the last two seconds and more.
        val near = demo.snapshot(HudDemo.STEP_MILLIS - 2_000).route!!.distanceToManeuverMeters!!
        assertTrue("$near m two seconds before", near in 1..44)
    }

    @Test
    fun `after the arrival the guidance goes, and the route starts over after a pause`() {
        val demo = HudDemo(withMedia = true)
        val arrived = HudDemo.STEP_MILLIS * demo.steps
        // The flag stays a while at the destination...
        val holding = demo.snapshot(arrived + 500)
        assertEquals(ManeuverType.ARRIVE_AT_DESTINATION.code, holding.route!!.nextManeuver!!.typeCode)
        assertEquals(0L, holding.route!!.distanceToManeuverMeters)
        // ...then guidance ends while the song plays on...
        val gap = demo.snapshot(demo.lapMillis - 500)
        assertNull(gap.route)
        assertNotNull(gap.nowPlaying)
        // ...and the next lap starts.
        assertNotNull(demo.snapshot(demo.lapMillis).route)
    }

    @Test
    fun `the HUD shows the demo's route, with a song when asked`() {
        assertNull(HudDemo(withMedia = false).snapshot(0).nowPlaying)
        val withSong = hudState(HudDemo(withMedia = true).snapshot(0))
        assertNotNull(withSong.media)
        assertEquals("400 m", withSong.maneuver!!.distance)
    }

    // The two demos stay apart: the maneuvers alone in the route, the lane row only in the lanes demo.
    @Test
    fun `the maneuvers demo shows no lanes, which are the lanes demo's`() {
        for (demo in listOf(HudDemo(withMedia = false), HudDemo(withMedia = true), HudDemo(withMedia = true, cycle = true))) {
            for (step in 0 until demo.steps) {
                for (at in listOf(500L, 4_500L, 6_600L)) {
                    val snapshot = demo.snapshot(step * HudDemo.STEP_MILLIS + at)
                    assertNull("step $step", snapshot.lanes)
                    assertTrue("step $step", snapshot.route?.laneGuidanceShowing != true)
                    assertNull("step $step", hudState(snapshot).maneuver?.lanes)
                }
            }
        }
        val lanes = HudDemo.fromEnvironment(mapOf("CHAIRPLAY_HUD_DEMO" to "lanes"))!!.snapshot(100)
        assertNotNull(hudState(lanes).maneuver!!.lanes)
    }

    // The Experiments page plays a demo over the iPhone's session: its made-up route takes the place of the iPhone's
    // guidance only, so the maneuver shares the HUD with the song as guidance does on a drive, and a call still covers
    // both.
    @Test
    fun `a demo played over a session keeps the iPhone's song, cover and calls`() {
        val song = NowPlaying(title = "Live song", artist = "Live artist", playing = true, appName = "Music", artworkTransferId = 7)
        val cover = Artwork(7, byteArrayOf(1, 2, 3))
        val guidance = RouteGuidance(state = RouteGuidanceState.ROUTE_SET, currentRoad = "Real Road", distanceToManeuverMeters = 900)
        val live = PhoneDataSnapshot(route = guidance, nowPlaying = song, artwork = cover)
        val demo = HudDemo(withMedia = false)

        val shown = demo.snapshot(0, live)
        assertEquals(demo.snapshot(0).route, shown.route)
        assertEquals(demo.snapshot(0).lanes, shown.lanes)
        assertEquals(cover, shown.artwork)
        val hud = hudState(shown)
        assertEquals("Live song", hud.media!!.title)
        assertEquals("400 m", hud.maneuver!!.distance)

        // In the demo's gap without guidance the song is alone, as after a route.
        val gap = hudState(demo.snapshot(demo.lapMillis - 500, live))
        assertNull(gap.maneuver)
        assertEquals("Live song", gap.media!!.title)

        // A call takes over the demo as it takes over guidance.
        val ringing = Call(id = "1", displayName = "Mom", status = CallStatus.RINGING, direction = CallDirection.INCOMING)
        assertEquals("Incoming call · Mom", hudState(demo.snapshot(0, live.copy(calls = listOf(ringing)))).call)

        // The lanes demo likewise.
        assertEquals("Live song", hudState(HudDemo(withMedia = false, lanes = true).snapshot(0, live)).media!!.title)

        // A demo with its own song shows that one, coming and going as the demo has it.
        assertEquals("Demo song", hudState(HudDemo(withMedia = true).snapshot(0, live)).media!!.title)
        assertNull(hudState(HudDemo(withMedia = true, cycle = true).snapshot(15_000, live)).media)

        // Without a session the demo is what it was.
        assertEquals(demo.snapshot(0), demo.snapshot(0, PhoneDataSnapshot()))
    }

    @Test(timeout = 10_000)
    fun `a running demo plays over the bus it is given`() {
        val running = HudDemo(withMedia = false).start({}) {}
        try {
            val live = PhoneDataSnapshot(nowPlaying = NowPlaying(title = "Live song", playing = true))
            val shown = running.over(live)
            assertNotNull(shown.route)
            assertEquals("Live song", shown.nowPlaying!!.title)
        } finally {
            running.stop()
        }
    }

    // The Experiments page starts a demo and stops it again while the app runs: a stopped one draws no more.
    @Test(timeout = 10_000)
    fun `a started demo draws until it is stopped`() {
        val repaints = java.util.concurrent.atomic.AtomicInteger()
        val running = HudDemo(withMedia = false).start({ repaints.incrementAndGet() }) {}
        Thread.sleep(HudDemo.FRAME_MILLIS * 4)
        assertTrue(repaints.get() > 0)
        assertNotNull(running.snapshot().route)

        running.stop()
        Thread.sleep(HudDemo.FRAME_MILLIS * 2)
        val stopped = repaints.get()
        Thread.sleep(HudDemo.FRAME_MILLIS * 4)
        assertEquals(stopped, repaints.get())
    }

    @Test
    fun `only the environment variable turns it on`() {
        assertNull(HudDemo.fromEnvironment(emptyMap()))
        assertEquals(false, HudDemo.fromEnvironment(mapOf("CHAIRPLAY_HUD_DEMO" to "1"))!!.withMedia)
        assertEquals(true, HudDemo.fromEnvironment(mapOf("CHAIRPLAY_HUD_DEMO" to "media"))!!.withMedia)
        val cycle = HudDemo.fromEnvironment(mapOf("CHAIRPLAY_HUD_DEMO" to "cycle"))!!
        assertTrue(cycle.cycle)
        assertTrue(cycle.withMedia)
    }

    @Test
    fun `the lanes demo switches lanes under one maneuver`() {
        val demo = HudDemo.fromEnvironment(mapOf("CHAIRPLAY_HUD_DEMO" to "lanes"))!!
        assertTrue(demo.lanes)
        val events = (0 until demo.laneEvents).map { demo.snapshot(it * HudDemo.LANE_STEP_MILLIS + 100) }
        // The maneuver stays the same, so nothing slides: only the lanes change.
        assertEquals(1, events.map { it.route!!.nextManeuver!!.index to it.route!!.nextManeuver!!.typeCode }.toSet().size)
        // Far enough that the arrow stays quiet: no fill or blink to watch past the lanes.
        val m = hudState(events[0]).maneuver!!
        assertEquals(ArrowProgress.OFF, arrowProgress(m.distanceMeters, m.next!!.distanceBetweenMeters, m.typeCode))
        // Every event is a new one for the bus and the renderer...
        val shown = events.filter { it.route!!.laneGuidanceShowing == true }
        assertEquals(shown.size, shown.map { it.route!!.laneGuidanceIndex }.toSet().size)
        assertTrue(shown.all { it.lanes!!.index == it.route!!.laneGuidanceIndex })
        // ...the recommended lane moves, the row grows past the frame's five cells, and it goes and comes back.
        val recommended = shown.map { it.lanes!!.lanes.indexOfFirst { lane -> lane.statusCode == LaneStatus.PREFERRED.code } }
        assertTrue("recommended lanes $recommended", recommended.toSet().size >= 3)
        assertTrue(shown.any { it.lanes!!.lanes.size > 5 })
        assertTrue(events.any { it.route!!.laneGuidanceShowing == false })
        // And over again.
        assertEquals(events[0].lanes!!.lanes, demo.snapshot(demo.laneEvents * HudDemo.LANE_STEP_MILLIS + 100).lanes!!.lanes)
    }

    @Test
    fun `the cycle brings guidance beside the song and takes it away again`() {
        val demo = HudDemo(withMedia = true, cycle = true)
        fun at(millis: Long) = hudState(demo.snapshot(millis))
        assertNotNull(at(1_000).media)
        assertNull(at(1_000).maneuver)
        assertNotNull(at(5_000).media)
        assertNotNull(at(5_000).maneuver)
        assertNotNull(at(11_000).media)
        assertNull(at(11_000).maneuver)
        assertNull(at(15_000).media)
        assertNull(at(15_000).maneuver)
        // And over again.
        assertNotNull(at(HudDemo.CYCLE_MILLIS + 5_000).maneuver)
    }
}
