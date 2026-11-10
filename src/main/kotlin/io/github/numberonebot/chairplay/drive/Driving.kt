package io.github.numberonebot.chairplay.drive

import io.github.numberonebot.chairplay.bridge.External
import io.github.numberonebot.chairplay.bridge.TelemetryInput
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
    /** A fresh reader of a game's telemetry, made each time gps.external comes into force. */
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

    /** The location the last connection made from the car, which puts itself here for the GPS page. */
    @Volatile var location: LocationLink? = null

    // The game's telemetry while the GPS and gps.external are on: read on the status tick while no game is there
    // ([lookForGame]), on the window loop while one is ([gameThere]), and closed there; the lock keeps the two apart.
    private val telemetryLock = Any()
    private var reader: TelemetryInput? = null
    @Volatile private var gameThere = false
    // Whether the last poll had a game there, driving or halted; the window loop's thread only.
    private var following = false
    // The red line and top speed (m/s) the game last gave while driving, for the gauges until the gamepad or keys first
    // drive the car again ([ownControls]); null for the GPS car's own.
    @Volatile private var gameRedline: Double? = null
    @Volatile private var gameTopSpeed: Double? = null

    /**
     * [DriveControl.pass] while the GPS is on, after a game's telemetry with gps.external on ([external]); off, the
     * telemetry closes ([unfollow]). Off, the car stays put: one driven or moving as the GPS went off stops
     * where it is, drive mode ends, and one more pass draws the gauges away.
     */
    fun pass(): Double? {
        val settings = settings
        val on = settings.enabled && live()
        if (on && held()) return hold()
        if (on && settings.external) return external()
        unfollow()
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

    // A pass while held: no input reaches the car, and a game's place waits too; the time held drives nothing after.
    private fun hold(): Double {
        control.scripted = CarInput()
        if (drive.moving || drive.input != CarInput()) drive.stop()
        drive.input = CarInput()
        return DriveControl.GAMEPAD_POLL_SECONDS
    }

    /**
     * A pass with gps.external on: a game sending puts the car where it says, one paused or quiet stops it there, and
     * with none the gamepad and keys drive, the place the game left saved first. It polls at least 20 times a second
     * while a game is there, and not at all while none is: the status tick looks for one.
     */
    private fun external(): Double? {
        // The GPS page's drive mode switch shows or hides the gauges while the game drives, too.
        control.takeKeyboardRequest()
        if (gameThere) when (val said = poll()) {
            is External.Drives -> {
                val record = said.record
                drive.follow(
                    GeoPoint(record.latitude, record.longitude), record.headingDegrees, record.speed, record.rpm, record.gear,
                    // The game's wheel angle as a share of the gauges' lock, which turns their wheel as far.
                    CarInput(record.throttle ?: 0.0, record.brake ?: 0.0, (record.steerDegrees ?: 0.0) / STEER_LOCK_DEGREES),
                )
                gameRedline = record.redline
                gameTopSpeed = record.topSpeed
                placed = true
                following = true
                return DriveControl.FRAME_SECONDS
            }
            External.Halted -> {
                if (drive.moving) drive.stop()
                following = true
                return DriveControl.GAMEPAD_POLL_SECONDS
            }
            External.Absent -> gameThere = false
        }
        handBack()
        return control.pass().also { ownControls() }
    }

    /**
     * The status tick's look for a game, four times a second while the GPS and gps.external are on and none is there:
     * one sending or paused wakes the window loop, which follows it from its next pass until it is gone.
     */
    fun lookForGame() {
        synchronized(telemetryLock) {
            val settings = settings
            if (!settings.enabled || !settings.external || gameThere || poll() == External.Absent) return
            gameThere = true
        }
        wake()
    }

    private fun poll(): External = synchronized(telemetryLock) { (reader ?: telemetry().also { reader = it }).poll() }

    // gps.external or the GPS gone off: the file closes, and a car the game had is handed back.
    private fun unfollow() {
        synchronized(telemetryLock) {
            reader?.close()
            reader = null
            gameThere = false
        }
        handBack()
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
        gameRedline = null
        gameTopSpeed = null
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
        if (was.enabled && was.external && !(now.enabled && now.external)) wake()
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
        return gaugeView(
            settings, snapshot, snapshot, gameRedline ?: drive.spec.redlineRpm, settings.gauges, gameTopSpeed?.times(3.6),
        )
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
