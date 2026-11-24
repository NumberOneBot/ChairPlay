package io.github.numberonebot.chairplay.drive

import io.github.numberonebot.chairplay.bridge.External
import io.github.numberonebot.chairplay.bridge.TelemetryInput
import io.github.numberonebot.chairplay.drive.track.*
import io.github.numberonebot.chairplay.platform.*
import java.util.Locale
import java.util.concurrent.atomic.AtomicBoolean
import kotlin.math.*

/**
 * The spoofed GPS's car for the app: placed at gps.start facing gps.heading, driven by [control] from the window loop,
 * sent to the phone by the [location] each connection makes from it (its [drive], [placed], [settings] and [nanoTime]),
 * and shown on the GPS page by [state], which the status tick watches ([showPage]).
 * Its place goes back into gps.start and gps.heading ([savePlace]) as it stops and as the app closes, so the settings
 * always say where the car is and the next start begins there. A GPS on with no start starts where the platform locates the
 * PC ([locate], run by [lookup]), as the app opens and as the GPS is turned on.
 */
class Driving(
    settings: GpsSettings,
    private val log: (String) -> Unit = {},
    gamepad: () -> FloatArray? = GlfwGamepad::axes,
    val nanoTime: () -> Long = System::nanoTime,
    private val locate: () -> LocationResult = Platforms.current.location::read,
    private val lookup: (Runnable) -> Unit = { Thread(it, "gps-start").apply { isDaemon = true }.start() },
    /** A fresh reader of a game's telemetry, made each time bridge.telemetry comes into force. */
    private val telemetry: () -> TelemetryInput = { TelemetryInput(log = log) },
) {
    /** Wakes the window loop, which may wait for an event, to put a switch saved on another thread in force. */
    @Volatile var wake: () -> Unit = {}

    /** Saves the car's place as gps.start and gps.heading, as the GPS page saves them; the app sets it ([saveTo]). */
    @Volatile var savePlace: (Map<String, String>) -> Unit = {}

    private val looking = AtomicBoolean()

    /** Saves the car's place with [save] from now on, and starts a GPS on with no start where the platform locates the PC. */
    fun saveTo(save: (Map<String, String>) -> Unit) {
        savePlace = save
        findStart()
    }

    @Volatile var settings: GpsSettings = settings
        private set

    /** Whether the car is anywhere yet: without a start it sends no position. */
    @Volatile var placed: Boolean
        private set

    val drive: Drive

    init {
        placed = settings.start != null
        drive = Drive(settings.driven, settings.start ?: GeoPoint(0.0, 0.0), settings.headingDegrees, nanoTime)
    }

    val control = DriveControl(drive, gamepad, onStopped = ::remember, nanoTime = nanoTime)
    /** The car's track playback ([DrivingTrack]); the app says through its [DrivingTrack.live] when a track may drive. */
    internal val tracks = DrivingTrack(drive, nanoTime, { placed = true }, ::remember)
    /** The recording loaded on the GPS page; [load] puts in another, stopped at its start, from any thread. */
    val track: TrackPlayer? get() = tracks.player
    fun load(recording: TrackRecording) = tracks.load(recording).also { wake() }

    /** The location the last connection made from the car, which puts itself here for the GPS page. */
    @Volatile var location: LocationLink? = null

    // A game's telemetry while bridge.telemetry is on, its controls sent whatever the GPS does.
    private val game = GameTelemetry(telemetry) { wake() }

    /** The game's controls, which the app sends as the keys' ([BridgeControls.send]). */
    val bridgeControls get() = game.controls

    /** The game's pause, which pauses the iPhone's music ([BridgePause]). */
    val bridgePause get() = game.pause

    /** Hears the game's car display once per change ([GameTelemetry.display]). */
    var carDisplay by game::display
    // Whether the last pass had a game driving or halting the car; the window loop's thread only.
    private var following = false
    // The game's last limits, kept until the gamepad or keys drive again ([ownControls]).
    @Volatile private var gameGauges = GaugeLimits.DEFAULT

    /**
     * A pass of the window loop: a game's telemetry read with bridge.telemetry on (closed off), its controls sent, then
     * the car's own pass ([carPass]); the sooner of the two says when the next is due.
     */
    fun pass(): Double? {
        val settings = settings
        val said = if (settings.external) game.pass() else External.Absent.also { game.close() }
        return listOfNotNull(carPass(settings, said), game.due()).minOrNull()
    }

    /**
     * [DriveControl.pass] while the GPS is on, after a track playing or paused ([tracks], drive mode's switch still taking
     * its gauges) and a game's place with bridge.telemetry on ([external]). Off, the car stays put: a game's is handed back,
     * one driven or moving as the GPS went off stops, drive mode ends, one more pass hides the gauges.
     */
    private fun carPass(settings: GpsSettings, said: External): Double? {
        val on = settings.enabled && live()
        if (on && held()) return hold()
        tracks.driver(on)?.let { control.takeKeyboardRequest(); return tracks.pass(it) }
        if (on && settings.external) return external(said)
        handBack()
        if (on) return control.pass().also { ownControls() }
        if (!control.keyboard && !drive.moving) return null
        if (control.keyboard) control.toggleKeyboard()
        drive.stop()
        return DriveControl.FRAME_SECONDS
    }

    /** Whether the GPS output can send the car now: a switch on with nowhere to send drives as off does (P-06). */
    @Volatile var live: () -> Boolean = { true }

    /** Whether a GPS operation holds the app's input (S-09): the car stands, let go, drive mode kept. */
    @Volatile var held: () -> Boolean = { false }

    // A pass while held: no input reaches the car, a track pauses, and a game's place waits too; the time held drives
    // nothing after.
    private fun hold(): Double {
        tracks.hold()
        control.scripted = CarInput()
        if (drive.moving || drive.input != CarInput()) drive.stop()
        drive.input = CarInput()
        return DriveControl.GAMEPAD_POLL_SECONDS
    }

    /**
     * A pass with the GPS and bridge.telemetry on, after the game's [said]: a game sending its place puts the car where it
     * says, one paused or quiet stops it there, and with none (or one sending no place, flag bit 8) the gamepad and keys
     * drive, the place the game left saved first.
     */
    private fun external(said: External): Double? {
        // The GPS page's drive mode switch shows or hides the gauges while the game drives, too.
        control.takeKeyboardRequest()
        when (said) {
            is External.Drives -> if (said.record.position) {
                val record = said.record
                drive.follow(
                    GeoPoint(record.latitude, record.longitude), record.headingDegrees, record.speed, record.rpm, record.gear,
                    // The game's wheel angle as a share of the gauges' lock, which turns their wheel as far.
                    CarInput(record.throttle ?: 0.0, record.brake ?: 0.0, (record.steerDegrees ?: 0.0) / STEER_LOCK_DEGREES),
                )
                gameGauges = GaugeLimits(redlineRpm = record.redline, topSpeed = record.topSpeed)
                tracks.releaseGauges()
                placed = true
                following = true
                return DriveControl.FRAME_SECONDS
            }
            External.Halted -> if (game.positioned) {
                if (drive.moving) drive.stop()
                following = true
                return DriveControl.GAMEPAD_POLL_SECONDS
            }
            External.Absent -> {}
        }
        handBack()
        return control.pass().also { ownControls() }
    }

    /** The status tick's look for a game, four times a second while bridge.telemetry is on and none is there. */
    fun lookForGame() {
        if (settings.external) game.lookForGame()
    }

    // A car the game had stops where it is, parked and facing as it did, and keeps its place: the model never steps
    // on from a game's state.
    private fun handBack() {
        if (!following) return
        following = false
        drive.stop()
        remember(drive.snapshot())
    }

    // The gamepad or keys driving the car again put a game's dials away: from their first press, the gauges are the GPS
    // car's. A game sending again brings its own back.
    private fun ownControls() {
        if (drive.snapshot().input == CarInput()) return
        gameGauges = GaugeLimits.DEFAULT
        tracks.releaseGauges()
    }

    /** Turns drive mode on or off and says which; it stays off while the GPS is off. */
    fun toggleKeyboard(): Boolean = if (settings.enabled || control.keyboard) control.toggleKeyboard() else false

    /**
     * Drive mode [on] or off as the GPS page's switch asks, from its thread; the next pass of the window loop puts it in
     * force. False while the GPS is off, which keeps drive mode off.
     */
    fun driveMode(on: Boolean): Boolean {
        if (!settings.enabled) return false
        control.requestKeyboard(on)
        return true
    }

    /**
     * The gps.* settings saved as [now], [keys] among them: a place puts the car there facing the heading, a heading
     * alone turns it where it is, and a power, mass or handling remakes it. The place the car saved itself is where it
     * is: it drives on.
     */
    fun saved(now: GpsSettings, keys: Set<String>) {
        val was = settings
        settings = now
        // On, the status tick looks for a game; off, the window loop closes the telemetry on its next pass.
        if (was.external && !now.external) wake()
        val here = drive.snapshot()
        val own = now.start?.let { abs(it.latitude - here.position.latitude) < SAME && abs(it.longitude - here.position.longitude) < SAME }
        if ("gps.start" in keys && own != true) {
            now.start?.let { start ->
                drive.place(start, now.headingDegrees)
                placed = true
                log("GPS car placed at ${start.latitude}, ${start.longitude}, heading ${now.headingDegrees}°")
            }
        } else if (keys.any { it == "gps.heading" || it == "gps.start" } && headingText(here.car.headingDegrees) != headingText(now.headingDegrees)) {
            drive.turn(now.headingDegrees)
            log("GPS car turned to ${now.headingDegrees}°")
        }
        if (keys.any { it in CAR_KEYS }) {
            drive.respec(now.driven)
            log("GPS car ${now.car.powerHp.roundToInt()} hp, ${now.car.massKg.roundToInt()} kg, ${now.car.handling.key} handling")
        }
        if ("gps.enabled" in keys) findStart()
    }

    /**
     * For a GPS on with the car nowhere, asks the platform where the PC is, one ask at a time, and puts the car there facing
     * as it does, saving the place as its start. A start set meanwhile wins; no answer leaves the car nowhere.
     */
    private fun findStart() {
        if (!settings.enabled || settings.start != null || placed || !looking.compareAndSet(false, true)) return
        lookup {
            try {
                when (val found = locate()) {
                    is LocationResult.Found -> if (!placed) {
                        drive.place(found.place, drive.snapshot().car.headingDegrees)
                        placed = true
                        log("GPS car placed where Windows locates this PC: ${found.place.latitude}, ${found.place.longitude}")
                        remember(drive.snapshot())
                    }
                    LocationResult.Denied -> log("GPS start not found: Windows does not share this PC's location")
                    LocationResult.Unknown -> log("GPS start not found: Windows found no position")
                }
            } finally {
                looking.set(false)
            }
        }
    }

    /** What the GPS page shows: the car's place and motion, the controls driving it, and what the iPhone asked for. */
    fun state(): Map<String, Any?> = drivingState(this)

    // What the GPS page was last told it shows; the status tick's thread only.
    private var shown: Map<String, Any?>? = null

    /** The status tick, four times a second: tells [changed] when what the GPS page shows ([state]) changed since. */
    fun showPage(changed: () -> Unit) {
        val now = state()
        if (now == shown) return
        shown = now
        changed()
    }

    /** What the gauges show while drive mode is on; null when they are gone. */
    fun gauges(): GaugeView? {
        if (!settings.enabled || !control.keyboard) return null
        val snapshot = drive.snapshot()
        return gaugeView(settings, snapshot, snapshot, drive.spec.redlineRpm, settings.gauges, tracks.gaugeLimits ?: gameGauges)
    }

    /** Saves where the car is for the next start; the app calls it as it closes. */
    fun close() = remember(drive.snapshot())

    private fun remember(snapshot: DriveSnapshot) {
        if (!placed) return
        val place = String.format(Locale.ROOT, "%.5f, %.5f", snapshot.position.latitude, snapshot.position.longitude)
        runCatching { savePlace(mapOf("gps.start" to place, "gps.heading" to headingText(snapshot.car.headingDegrees))) }
            .onFailure { log("GPS car place not saved: $it") }
    }

    private fun headingText(degrees: Double) = (degrees.roundToInt() % 360 + 360).rem(360).toString()

    private companion object {
        /**
         * What remakes the car: its own settings, the units, whose speedometer's end it runs to, and the gauges' style,
         * how far it revs.
         */
        val CAR_KEYS = setOf("gps.car.power.hp", "gps.car.mass.kg", "gps.car.handling", "gps.units", "gps.gauges.style")
        /** Degrees apart within the five decimals the place is saved with. */
        const val SAME = 1e-5
    }
}
