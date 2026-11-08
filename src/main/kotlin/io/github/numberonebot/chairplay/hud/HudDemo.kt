package io.github.numberonebot.chairplay.hud

import io.github.numberonebot.chairplay.data.DrivingSide
import io.github.numberonebot.chairplay.data.JunctionType
import io.github.numberonebot.chairplay.data.Lane
import io.github.numberonebot.chairplay.data.LaneGuidance
import io.github.numberonebot.chairplay.data.LaneStatus
import io.github.numberonebot.chairplay.data.Maneuver
import io.github.numberonebot.chairplay.data.ManeuverType
import io.github.numberonebot.chairplay.data.NowPlaying
import io.github.numberonebot.chairplay.data.PhoneDataSnapshot
import io.github.numberonebot.chairplay.data.RouteGuidance
import io.github.numberonebot.chairplay.data.RouteGuidanceState
import kotlin.math.roundToLong

/**
 * A made-up route for watching the HUD's maneuvers without an iPhone: CHAIRPLAY_HUD_DEMO=1 in the environment shows
 * it in the AltScreen's HUD in place of the iPhone's guidance, beside the iPhone's song while a session plays one
 * ([Running.over]), CHAIRPLAY_HUD_DEMO=media with a made-up song beside it. Each maneuver
 * is approached from [START_METERS] in [STEP_MILLIS], slowing down toward it, so the arrow fills and blinks before
 * the next one slides in; after the last the flag waves at the destination, guidance ends, and after a pause the
 * route starts over ([lapMillis]). It has no lanes: the lane row is the lanes demo's alone. CHAIRPLAY_HUD_DEMO=cycle
 * plays the HUD's own animations instead,
 * over and over in [CYCLE_MILLIS]: the song alone, guidance coming beside it, the song alone again and nothing.
 * CHAIRPLAY_HUD_DEMO=lanes holds one maneuver far ahead, its arrow quiet, and switches the lanes under it every
 * [LANE_STEP_MILLIS]: the recommended lane moves, the row grows past the frame's cells, goes and comes back.
 * With CHAIRPLAY_HUD_DEMO_SHOTS=1 the first lap saves screenshots too.
 */
class HudDemo(
    val withMedia: Boolean,
    val takeShots: Boolean = false,
    val cycle: Boolean = false,
    val lanes: Boolean = false,
) {
    /** How many maneuvers a lap has. */
    val steps: Int get() = ROUTE.size

    /** How many lane events the lanes demo plays before it starts over. */
    val laneEvents: Int get() = LANE_SWITCHES.size

    /** The bus as it would be [elapsedMillis] into the demo. */
    fun snapshot(elapsedMillis: Long): PhoneDataSnapshot {
        if (lanes) return laneSwitching(elapsedMillis)
        val full = route(elapsedMillis)
        if (!cycle) return full
        val into = elapsedMillis.coerceAtLeast(0) % CYCLE_MILLIS
        return PhoneDataSnapshot(
            route = full.route.takeIf { into in CYCLE_ROUTE },
            nowPlaying = full.nowPlaying.takeIf { into < CYCLE_SONG_END },
        )
    }

    /**
     * The bus as it would be [elapsedMillis] into the demo played over [live], the iPhone's: the demo's guidance and
     * lanes take the place of the iPhone's, and the rest stays the iPhone's, so the maneuver shares the HUD with the
     * song as guidance does on a drive, and a call covers both. A demo [withMedia] shows its own song instead.
     */
    fun snapshot(elapsedMillis: Long, live: PhoneDataSnapshot): PhoneDataSnapshot {
        val demo = snapshot(elapsedMillis)
        return live.copy(
            route = demo.route,
            lanes = demo.lanes,
            nowPlaying = if (withMedia) demo.nowPlaying else live.nowPlaying,
            artwork = if (withMedia) demo.artwork else live.artwork,
        )
    }

    /** One lap: every maneuver, the flag held at the destination, and a spell without guidance before the next. */
    val lapMillis: Long get() = STEP_MILLIS * ROUTE.size + ARRIVED_MILLIS + GAP_MILLIS

    private fun route(elapsedMillis: Long): PhoneDataSnapshot {
        val elapsed = elapsedMillis.coerceAtLeast(0)
        val lap = (elapsed / lapMillis).toInt()
        val inLap = elapsed % lapMillis
        val driving = STEP_MILLIS * ROUTE.size
        val song = NowPlaying(title = "Demo song", artist = "ChairPlay", album = "Maneuvers", playing = true, appName = "Demo")
            .takeIf { withMedia }
        if (inLap >= driving + ARRIVED_MILLIS) return PhoneDataSnapshot(route = null, nowPlaying = song)
        val inRoute = minOf(inLap / STEP_MILLIS, ROUTE.size - 1L).toInt()
        // Arrived: the distance stays at zero while the flag waves.
        val into = if (inLap >= driving) STEP_MILLIS else inLap % STEP_MILLIS
        val step = lap * ROUTE.size + inRoute
        val (road, maneuver) = ROUTE[inRoute]
        // A car slowing down toward the turn: the distance falls with the square of the time left, so the arrow's
        // blink over the last metres lasts a couple of seconds, as it does in town.
        val left = (STEP_MILLIS - into).toDouble() / STEP_MILLIS
        val meters = (START_METERS * left * left).roundToLong()
        val route = RouteGuidance(
            state = RouteGuidanceState.ROUTE_SET,
            currentRoad = "Demo Street",
            destination = "Demo destination",
            remainingSeconds = (ROUTE.size - inRoute) * 60L,
            remainingMeters = (ROUTE.size - inRoute) * START_METERS,
            distanceToManeuverMeters = meters,
            nextManeuver = maneuver.copy(index = step, afterRoad = road, description = road),
            // The maneuvers alone: the lane row is the lanes demo's.
            laneGuidanceShowing = false,
            sourceName = "ChairPlay demo",
        )
        return PhoneDataSnapshot(route = route, nowPlaying = song)
    }

    private fun laneSwitching(elapsedMillis: Long): PhoneDataSnapshot {
        val event = (elapsedMillis.coerceAtLeast(0) / LANE_STEP_MILLIS).toInt()
        val lanes = LANE_SWITCHES[event % LANE_SWITCHES.size]
        val route = RouteGuidance(
            state = RouteGuidanceState.ROUTE_SET,
            currentRoad = "Demo Motorway",
            destination = "Demo destination",
            remainingSeconds = 600,
            remainingMeters = 9_000,
            // Beyond the arrow's bar, so it stays quiet while the lanes switch.
            distanceToManeuverMeters = LANES_DISTANCE_METERS,
            nextManeuver = turn(ManeuverType.OFF_RAMP, 25).copy(afterRoad = "Exit 12", description = "Exit 12"),
            // Each switch is a new lane event, as the iPhone sends them.
            laneGuidanceIndex = event,
            laneGuidanceShowing = lanes.isNotEmpty(),
            sourceName = "ChairPlay demo",
        )
        return PhoneDataSnapshot(route = route, lanes = LaneGuidance(event, lanes).takeIf { lanes.isNotEmpty() })
    }

    /** When, in the lanes demo's first round, a screenshot shows each lane event settled. */
    val laneShots: List<Pair<String, Long>> get() =
        LANE_SWITCHES.indices.map { "lanes-${it + 1}" to it * LANE_STEP_MILLIS + LANE_STEP_MILLIS * 2 / 3 }

    /** When, within each step of the first lap, a screenshot shows the transition, the filling arrow and the blink. */
    val shots: List<Pair<String, Long>> = listOf("moving" to 500L, "filling" to 4_500L, "blinking" to 6_600L)

    /** When, in the first cycle, a screenshot catches each of the HUD's animations half way and the still between. */
    val cycleShots: List<Pair<String, Long>> = listOf(
        "cycle-1-alone" to 2_000L, "cycle-2-song-moving-aside" to 4_250L, "cycle-3-guidance-rising" to 4_750L,
        "cycle-4-beside" to 6_000L, "cycle-5-guidance-dissolving" to 10_250L, "cycle-6-song-coming-back" to 10_750L,
        "cycle-7-alone" to 12_000L, "cycle-8-fading" to 14_350L,
    )

    /**
     * Starts the demo's clock on a daemon thread, which calls [repaint] every [FRAME_MILLIS] and, with [takeShots],
     * [screenshot] with a name at each of the [shots] of the first lap. Returns it [Running]: its bus snapshot of the
     * moment, and its stop.
     */
    fun start(repaint: () -> Unit, screenshot: (String) -> Unit): Running {
        val started = System.nanoTime()
        fun elapsed() = (System.nanoTime() - started) / 1_000_000
        val due = when {
            !takeShots -> emptyList()
            cycle -> cycleShots
            lanes -> laneShots
            else -> (0 until steps).flatMap { step -> shots.map { (name, at) -> "${step + 1}-$name" to step * STEP_MILLIS + at } }
        }.toMutableList()
        val stopped = java.util.concurrent.atomic.AtomicBoolean()
        Thread({
            while (!stopped.get()) {
                Thread.sleep(FRAME_MILLIS)
                if (stopped.get()) break
                repaint()
                val now = elapsed()
                while (due.isNotEmpty() && due.first().second <= now) screenshot(due.removeAt(0).first)
            }
        }, "hud-demo").apply { isDaemon = true }.start()
        return Running({ live -> snapshot(elapsed(), live) }) { stopped.set(true) }
    }

    /**
     * A demo playing: the bus as it is now in it, alone ([snapshot]) or played over the iPhone's ([over]), and [stop],
     * after which it draws no more.
     */
    class Running(private val at: (PhoneDataSnapshot) -> PhoneDataSnapshot, private val onStop: () -> Unit) {
        val snapshot: () -> PhoneDataSnapshot = { at(PhoneDataSnapshot()) }

        /** The demo now over [live], the iPhone's bus: see [HudDemo.snapshot]. */
        fun over(live: PhoneDataSnapshot): PhoneDataSnapshot = at(live)

        fun stop() = onStop()
    }

    companion object {
        const val STEP_MILLIS = 7_000L
        const val START_METERS = 400L
        /** After the last maneuver: the flag at the destination, then no guidance, before the route starts over. */
        const val ARRIVED_MILLIS = 3_000L
        const val GAP_MILLIS = 3_000L
        /** How often the demo's distance is drawn again. */
        const val FRAME_MILLIS = 100L
        /** The cycle's length: the song from its start to [CYCLE_SONG_END], guidance over [CYCLE_ROUTE]. */
        const val CYCLE_MILLIS = 16_000L
        private const val CYCLE_SONG_END = 14_000L
        private val CYCLE_ROUTE = 4_000L until 10_000L
        /** How long each lane event of the lanes demo stays. */
        const val LANE_STEP_MILLIS = 2_500L
        private const val LANES_DISTANCE_METERS = 1_000L

        fun fromEnvironment(environment: Map<String, String> = System.getenv()): HudDemo? {
            val shots = environment["CHAIRPLAY_HUD_DEMO_SHOTS"]?.trim() == "1"
            return when (environment["CHAIRPLAY_HUD_DEMO"]?.trim()?.lowercase()) {
                null, "", "0", "false" -> null
                "media" -> HudDemo(withMedia = true, takeShots = shots)
                "cycle" -> HudDemo(withMedia = true, takeShots = shots, cycle = true)
                "lanes" -> HudDemo(withMedia = false, takeShots = shots, lanes = true)
                else -> HudDemo(withMedia = false, takeShots = shots)
            }
        }

        private fun turn(type: ManeuverType, exit: Int? = null, roads: List<Int> = emptyList(), junction: JunctionType = JunctionType.INTERSECTION) =
            Maneuver(
                index = 0, typeCode = type.code, exitAngle = exit, junctionAngles = roads, junctionType = junction,
                drivingSide = DrivingSide.RIGHT, distanceBetweenMeters = 300,
            )

        /**
         * A lane at [position], left to right, leading every way of [angles]; a recommended one takes the first, the
         * way the iPhone highlights.
         */
        private fun lane(position: Int, recommended: Boolean, vararg angles: Int) = Lane(
            position = position,
            statusCode = (if (recommended) LaneStatus.PREFERRED else LaneStatus.NOT_GOOD).code,
            angles = angles.toList(),
            highlightAngle = angles.first().takeIf { recommended },
        )

        /** The route's maneuvers, each with the road after it. */
        private val ROUTE = listOf(
            "Left Street" to turn(ManeuverType.LEFT_TURN, -90, listOf(-90, 0, 90)),
            "Ring Road" to turn(ManeuverType.ROUNDABOUT_EXIT_2, 0, listOf(-90, 0, 90, 180), JunctionType.ROUNDABOUT),
            "Bend Avenue" to turn(ManeuverType.SLIGHT_RIGHT_TURN, 35, listOf(35, 0)),
            "Circle Lane" to turn(ManeuverType.ROUNDABOUT_EXIT_3, 90, listOf(-90, 0, 90, 180), JunctionType.ROUNDABOUT),
            "Sharp Corner" to turn(ManeuverType.SHARP_LEFT_TURN, -135, listOf(-135, 0, 90)),
            "Back Road" to turn(ManeuverType.U_TURN),
            "Exit 12" to turn(ManeuverType.OFF_RAMP, 25),
            "Motorway" to turn(ManeuverType.CHANGE_HIGHWAY_LEFT),
            "Right Street" to turn(ManeuverType.RIGHT_TURN, 90, listOf(90, 0)),
            "Demo destination" to turn(ManeuverType.ARRIVE_AT_DESTINATION),
        )

        /** The lanes demo's events, left to right, toward an exit on the right. */
        private val LANE_SWITCHES = listOf(
            // Straight on in the middle lane.
            listOf(lane(0, false, 0), lane(1, true, 0), lane(2, false, 0, 45)),
            // The exit comes up: the right lane.
            listOf(lane(0, false, 0), lane(1, false, 0), lane(2, false, 0), lane(3, true, 45)),
            // Either of the two right lanes.
            listOf(lane(0, false, 0), lane(1, false, 0), lane(2, true, 45, 0), lane(3, true, 45)),
            // A lane added for the exit.
            listOf(lane(0, false, 0), lane(1, false, 0), lane(2, false, 0), lane(3, true, 45, 0), lane(4, true, 45)),
            // More lanes than the frame's cells: the far end becomes an ellipsis.
            List(7) { lane(it, it >= 5, if (it >= 5) 45 else 0) },
            // No lanes for a while.
            emptyList(),
            // Back, and the other way: a lane change to the left.
            listOf(lane(0, true, -45, 0), lane(1, false, 0), lane(2, false, 0)),
        )
    }
}
