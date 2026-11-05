package io.github.numberonebot.chairplay.drive

import com.shilapi.xcertplay.transport.Iap2LocationProvider
import java.util.Locale
import java.util.concurrent.atomic.AtomicBoolean
import kotlin.math.abs
import kotlin.math.roundToInt

/**
 * The spoofed GPS's car for the app: placed at gps.start facing gps.heading, driven by [control] from the window loop,
 * sent to the iPhone by the [location] each connection makes, and shown on the GPS page by [state], as each update goes.
 * Its place goes back into gps.start and gps.heading ([savePlace]) as it stops and as the app closes, so the settings
 * always say where the car is and the next start begins there. A GPS on with no start starts where Windows locates the
 * PC ([locate], run by [lookup]), as the app opens and as the GPS is turned on.
 */
class Driving(
    settings: GpsSettings,
    private val log: (String) -> Unit = {},
    gamepad: () -> FloatArray? = GlfwGamepad::axes,
    private val nanoTime: () -> Long = System::nanoTime,
    private val satelliteSentences: (GeoPoint, Long) -> String? = { _, _ -> null },
    private val locate: () -> WindowsLocation.Result = WindowsLocation::read,
    private val lookup: (Runnable) -> Unit = { Thread(it, "gps-start").apply { isDaemon = true }.start() },
) {
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
        drive = Drive(settings.car, settings.start ?: GeoPoint(0.0, 0.0), settings.headingDegrees, nanoTime)
    }

    val control = DriveControl(drive, gamepad, onStopped = ::remember, nanoTime = nanoTime)

    @Volatile private var link: DriveLocation? = null

    /**
     * [DriveControl.pass] while the GPS is on. Off, the car stays put: one driven or moving as the GPS went off stops
     * where it is, drive mode ends, and one more pass draws the gauges away.
     */
    fun pass(): Double? {
        if (settings.enabled) return control.pass()
        if (!control.keyboard && !drive.moving) return null
        if (control.keyboard) control.toggleKeyboard()
        drive.stop()
        return DriveControl.FRAME_SECONDS
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

    /** The iPhone's GPS for a connection that identified with it. */
    fun location(gps: GpsSettings): Iap2LocationProvider =
        DriveLocation(drive, gps.satellites, log, nanoTime = nanoTime, placed = { placed }, settings = { settings },
            onSent = { onSent() }, satelliteSentences = satelliteSentences).also { link = it }

    /**
     * The gps.* settings saved as [now], [keys] among them: a place puts the car there facing the heading, a heading
     * alone turns it where it is, and a power, mass or handling remakes it. The place the car saved itself is where it
     * is: it drives on.
     */
    fun saved(now: GpsSettings, keys: Set<String>) {
        settings = now
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
            drive.respec(now.car)
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
        val status = link?.status()
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
            tachometer = GaugeScale.tachometer(drive.spec),
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
        val CAR_KEYS = setOf("gps.car.power.hp", "gps.car.mass.kg", "gps.car.handling")
        /** Degrees apart within the five decimals the place is saved with. */
        const val SAME = 1e-5
    }
}
