package io.github.numberonebot.chairplay.drive

import io.github.numberonebot.chairplay.bridge.External
import io.github.numberonebot.chairplay.bridge.TelemetryInput
import java.util.Locale
import java.util.concurrent.atomic.AtomicBoolean
import kotlin.math.abs
import kotlin.math.roundToInt

/**
 * The spoofed GPS's car for the app: placed at gps.start facing gps.heading, driven by [control] from the window loop,
 * sent to the phone by the [location] each connection makes from it (its [drive], [placed], [settings], [nanoTime] and
 * [satelliteSentences], telling [onSent]), and shown on the GPS page by [state], as each update goes.
 * Its place goes back into gps.start and gps.heading ([savePlace]) as it stops and as the app closes, so the settings
 * always say where the car is and the next start begins there. A GPS on with no start starts where Windows locates the
 * PC ([locate], run by [lookup]), as the app opens and as the GPS is turned on.
 */
class Driving(
    settings: GpsSettings,
    private val log: (String) -> Unit = {},
    gamepad: () -> FloatArray? = GlfwGamepad::axes,
    val nanoTime: () -> Long = System::nanoTime,
    /** The $GPGSV at a place and UTC, as the fix goes; null while fresh orbital data is unavailable. */
    val satelliteSentences: (GeoPoint, Long) -> String? = { _, _ -> null },
    private val locate: () -> WindowsLocation.Result = WindowsLocation::read,
    private val lookup: (Runnable) -> Unit = { Thread(it, "gps-start").apply { isDaemon = true }.start() },
    /** A fresh reader of a game's telemetry, made each time gps.external comes into force. */
    private val telemetry: () -> TelemetryInput = { TelemetryInput(log = log) },
) {
    /** Wakes the window loop, which may wait for an event, to put a switch saved on another thread in force. */
    @Volatile var wake: () -> Unit = {}

    /** Saves the car's place as gps.start and gps.heading, as the GPS page saves them; the app sets it ([saveTo]). */
    @Volatile var savePlace: (Map<String, String>) -> Unit = {}

    private val looking = AtomicBoolean()

    /** Saves the car's place with [save] from now on, and starts a GPS on with no start where Windows locates the PC. */
    fun saveTo(save: (Map<String, String>) -> Unit) {
        savePlace = save
        findStart()
    }

    /** Hears each update sent to the iPhone; the app shows the GPS page the car then. */
    @Volatile var onSent: () -> Unit = {}

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

    // The game's telemetry, read and closed on the window loop's thread only, while the GPS and gps.external are on.
    private var reader: TelemetryInput? = null
    // Whether the last poll had a game there, driving or halted.
    private var following = false
    // The red line the game last gave while driving, for the tachometer until it is gone; null for the GPS car's own.
    @Volatile private var gameRedline: Double? = null

    /**
     * [DriveControl.pass] while the GPS is on, after a game's telemetry with gps.external on ([external]); off, the
     * telemetry closes ([unfollow]). Off, the car stays put: one driven or moving as the GPS went off stops
     * where it is, drive mode ends, and one more pass draws the gauges away.
     */
    fun pass(): Double? {
        val settings = settings
        if (settings.enabled && settings.external) return external()
        unfollow()
        if (settings.enabled) return control.pass()
        if (!control.keyboard && !drive.moving) return null
        if (control.keyboard) control.toggleKeyboard()
        drive.stop()
        return DriveControl.FRAME_SECONDS
    }

    /**
     * A pass with gps.external on: a game sending puts the car where it says, one paused or quiet stops it there, and
     * with none the gamepad and keys drive, the place the game left saved first. It polls at least 20 times a second.
     */
    private fun external(): Double {
        val current = reader ?: telemetry().also { reader = it }
        // The GPS page's drive mode switch shows or hides the gauges while the game drives, too.
        control.takeKeyboardRequest()
        when (val said = current.poll()) {
            is External.Drives -> {
                val record = said.record
                drive.follow(GeoPoint(record.latitude, record.longitude), record.headingDegrees, record.speed, record.rpm, record.gear)
                gameRedline = record.redline
                placed = true
                following = true
                return DriveControl.FRAME_SECONDS
            }
            External.Halted -> {
                if (drive.moving) drive.stop()
                following = true
                return DriveControl.GAMEPAD_POLL_SECONDS
            }
            External.Absent -> {
                handBack()
                return minOf(control.pass() ?: DriveControl.GAMEPAD_POLL_SECONDS, DriveControl.GAMEPAD_POLL_SECONDS)
            }
        }
    }

    // gps.external or the GPS gone off: the file closes, and a car the game had is handed back.
    private fun unfollow() {
        reader?.close()
        reader = null
        handBack()
    }

    // A car the game had stops where it is, parked and facing as it did, and keeps its place: the model never steps
    // on from a game's state.
    private fun handBack() {
        if (!following) return
        following = false
        gameRedline = null
        drive.stop()
        remember(drive.snapshot())
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
        // The window loop reads the telemetry from its next pass, or closes it there.
        if (now.enabled && now.external && !(was.enabled && was.external)) wake()
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
     * For a GPS on with the car nowhere, asks Windows where the PC is, one ask at a time, and puts the car there facing
     * as it does, saving the place as its start. A start set meanwhile wins; no answer leaves the car nowhere.
     */
    private fun findStart() {
        if (!settings.enabled || settings.start != null || placed || !looking.compareAndSet(false, true)) return
        lookup {
            try {
                when (val found = locate()) {
                    is WindowsLocation.Found -> if (!placed) {
                        drive.place(found.place, drive.snapshot().car.headingDegrees)
                        placed = true
                        log("GPS car placed where Windows locates this PC: ${found.place.latitude}, ${found.place.longitude}")
                        remember(drive.snapshot())
                    }
                    WindowsLocation.Denied -> log("GPS start not found: Windows does not share this PC's location")
                    WindowsLocation.Unknown -> log("GPS start not found: Windows found no position")
                }
            } finally {
                looking.set(false)
            }
        }
    }

    /** What the GPS page shows: the car's place and motion, the controls driving it, and what the iPhone asked for. */
    fun state(): Map<String, Any?> {
        val snapshot = drive.snapshot()
        val car = snapshot.car
        val status = location?.status()
        return linkedMapOf(
            "position" to if (placed) listOf(snapshot.position.latitude, snapshot.position.longitude) else null,
            "heading" to car.headingDegrees.roundToInt() % 360,
            "speed" to car.speedKmh.roundToInt(),
            "rpm" to car.rpm.roundToInt(),
            "gear" to car.gearName,
            "throttle" to snapshot.input.throttle,
            "brake" to snapshot.input.brake,
            "keyboard" to control.keyboard,
            "gamepad" to control.gamepad,
            "listening" to (status?.listening ?: false),
            "sentences" to status?.requested?.map { SENTENCES[it] ?: "#$it" },
            "sent" to (status?.sent ?: 0L),
        )
    }

    /** What the gauges show while drive mode is on; null when they are gone. */
    fun gauges(): GaugeView? {
        if (!settings.enabled || !control.keyboard) return null
        val snapshot = drive.snapshot()
        val car = snapshot.car
        return GaugeView(
            speedKmh = car.speedKmh,
            rpm = car.rpm,
            gear = car.gearName,
            throttle = snapshot.input.throttle,
            brake = snapshot.input.brake,
            headingDegrees = car.headingDegrees,
            speedometer = GaugeScale.speedometer(settings.units),
            tachometer = GaugeScale.tachometer(gameRedline ?: drive.spec.redlineRpm),
            position = snapshot.position,
            size = settings.gauges,
            steer = snapshot.input.steer,
            wheelMode = settings.wheel,
            units = settings.units,
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
        /** 0xFFFA's selectors, identification fields 17 to 20 less 16. */
        val SENTENCES = mapOf(1 to "GGA", 2 to "RMC", 3 to "GSV", 4 to "PASCD")
        /** What remakes the car: its own settings, and the units, whose speedometer's end it runs to. */
        val CAR_KEYS = setOf("gps.car.power.hp", "gps.car.mass.kg", "gps.car.handling", "gps.units")
        /** Degrees apart within the five decimals the place is saved with. */
        const val SAME = 1e-5
    }
}
