package io.github.numberonebot.chairplay.carplay

import com.shilapi.xcertplay.transport.Iap2LocationMessages
import com.shilapi.xcertplay.transport.Iap2LocationProvider
import com.shilapi.xcertplay.transport.PascdEncoder
import com.shilapi.xcertplay.transport.VehicleGear
import com.shilapi.xcertplay.transport.VehicleSpeedReading
import com.shilapi.xcertplay.transport.VehicleSpeedSample
import io.github.numberonebot.chairplay.drive.CarInput
import io.github.numberonebot.chairplay.drive.Drive
import io.github.numberonebot.chairplay.drive.DriveSnapshot
import io.github.numberonebot.chairplay.drive.Driving
import io.github.numberonebot.chairplay.drive.GpsSettings
import io.github.numberonebot.chairplay.drive.LocationLink
import io.github.numberonebot.chairplay.drive.LocationStatus
import io.github.numberonebot.chairplay.drive.NmeaFix
import io.github.numberonebot.chairplay.drive.NmeaFraming
import io.github.numberonebot.chairplay.drive.NmeaSentences
import io.github.numberonebot.chairplay.drive.PacedLocation
import java.util.Locale
import kotlin.math.abs

/**
 * The spoofed car as the iPhone's GPS, for iAP2 LocationInformation: its place, speed and course in the sentences
 * gps.nmea picks; and $PASCD, its wheel speed ten times a second, when asked, for the iPhone to reckon between the fixes.
 * A car not [placed] anywhere yet sends no position, only its wheel speed.
 *
 * [attach]ed to the tunnel, it sends on its own at gps.rate fixes a second (the iPhone asks for one every 250 ms), each
 * with its own time, and DiPlay's once-a-second update sends nothing; unattached, it answers that update.
 */
class CarPlayLocation(
    private val drive: Drive,
    private val log: (String) -> Unit = {},
    private val nanoTime: () -> Long = System::nanoTime,
    private val wallMillis: () -> Long = System::currentTimeMillis,
    private val placed: () -> Boolean = { true },
    private val settings: () -> GpsSettings = { GpsSettings(enabled = true, rate = 1) },
    /** Runs the action every period, read again each time, until closed. */
    private val schedule: (period: () -> Long, action: () -> Unit) -> AutoCloseable = ::repeatEvery,
    /** What it sends through: the GPS output's coordinator opens it while CarPlay's method sends. */
    private val gate: SendGate = SendGate(open = true),
) : Iap2LocationProvider, PacedLocation, LocationLink {
    /**
     * The app's car for a connection that identified with the GPS on: its clock, place and settings; it becomes the
     * link [driving]'s GPS page reads, the updates sent counted there.
     */
    constructor(
        driving: Driving,
        log: (String) -> Unit,
        wallMillis: () -> Long = System::currentTimeMillis,
        schedule: (period: () -> Long, action: () -> Unit) -> AutoCloseable = ::repeatEvery,
        gate: SendGate = SendGate(open = true),
    ) : this(driving.drive, log, driving.nanoTime, wallMillis, placed = { driving.placed },
        settings = { driving.settings }, schedule = schedule, gate = gate) {
        driving.location = this
    }

    @Volatile private var listening = false
    @Volatile private var requested: Set<Int> = emptySet()
    @Volatile private var sent = 0L
    @Volatile private var send: ((List<String>) -> Unit)? = null
    private val lock = Any()
    private var sender: AutoCloseable? = null
    // A minute of what went, for the log.
    private var windowStart = -1L
    private var windowSent = 0
    private var windowPositions = 0
    private var windowSamples = 0

    override fun attach(send: (List<String>) -> Unit) {
        this.send = send
    }

    override fun onRequested(components: Set<Int>) {
        requested = components
    }

    override fun start(): Boolean {
        listening = true
        val now = settings()
        log("GPS: the iPhone asks for ${components(requested)}; sending ${now.nmea.key} sentences " +
            if (send != null) "at ${now.rate} a second, ${now.framing.key}" else "once a second")
        val link = send ?: return true
        synchronized(lock) {
            sender?.close()
            sender = schedule({ 1000L / settings().rate.coerceAtLeast(1) }) { tick(link) }
        }
        return true
    }

    override fun stop() {
        listening = false
        synchronized(lock) {
            sender?.close()
            sender = null
        }
    }

    /** DiPlay's once-a-second update: nothing while this sends on its own. */
    override fun latestNmea(): String? = if (sending()) null else gate.pass { compose()?.also { sent++ } }

    override fun status() = LocationStatus(listening, requested, sent)

    private fun sending() = synchronized(lock) { sender != null }

    private fun tick(link: (List<String>) -> Unit) = gate.pass { send(link) }

    private fun send(link: (List<String>) -> Unit) {
        val nmea = compose() ?: return
        try {
            // gps.framing: one string as DiPlay sends it, a message for each sentence, or a parameter for each.
            val sentences = nmea.split("\r\n").filter(String::isNotEmpty).map { "$it\r\n" }
            when (settings().framing) {
                NmeaFraming.JOINED -> link(listOf(nmea))
                NmeaFraming.MESSAGES -> sentences.forEach { link(listOf(it)) }
                NmeaFraming.PARAMETERS -> link(sentences)
            }
            sent++
        } catch (error: Exception) {
            log("GPS: the tunnel took no more updates (${error.message}); sending stops")
            stop()
        }
    }

    /** One update: the position and the wheel speed since the last. */
    private fun compose(): String? {
        val now = settings()
        val car = drive.snapshot()
        val wall = wallMillis()
        val hasPosition = placed()
        val position = if (hasPosition) {
            val course = when {
                !now.sendHeading -> null
                car.car.speed > 0 -> car.car.headingDegrees
                car.car.speed < 0 -> (car.car.headingDegrees + 180) % 360
                else -> null
            }
            NmeaSentences.position(NmeaFix(car.position, abs(car.car.speed), course), wall, now.nmea)
        } else null
        val reading = if (Iap2LocationMessages.VEHICLE_SPEED_DATA in requested) wheelSpeed(car) else null
        record(position != null, reading?.samples?.size ?: 0, now)
        val text = listOfNotNull(position, reading?.let(PascdEncoder::encode)).joinToString("")
        return text.ifEmpty { null }
    }

    // Parked with the loop idle, nothing was sampled: one sample at the standstill keeps the wheel speed going.
    private fun wheelSpeed(car: DriveSnapshot): VehicleSpeedReading {
        val samples = drive.drainSpeed().map { VehicleSpeedSample(it.timeMillis, it.metersPerSecond) }
            .ifEmpty { listOf(VehicleSpeedSample(nanoTime() / 1_000_000, abs(car.car.speed))) }
        val gear = when {
            car.car.gear < 0 -> VehicleGear.REVERSE
            car.car.speed == 0.0 && car.input == CarInput() -> VehicleGear.PARK
            else -> VehicleGear.DRIVE
        }
        return VehicleSpeedReading(gear, samples)
    }

    private fun record(withPosition: Boolean, samples: Int, now: GpsSettings) {
        val millis = nanoTime() / 1_000_000
        if (windowStart < 0) windowStart = millis
        windowSent++
        if (withPosition) windowPositions++
        windowSamples += samples
        if (millis - windowStart < SUMMARY_MILLIS) return
        log("GPS: last ${(millis - windowStart) / 1000} s updates=$windowSent withPosition=$windowPositions " +
            "wheelSpeedSamples=$windowSamples rate=${if (send != null) now.rate else 1} nmea=${now.nmea.key} " +
            "speed=${String.format(Locale.US, "%.1f", drive.snapshot().car.speedKmh)} km/h")
        windowStart = millis
        windowSent = 0
        windowPositions = 0
        windowSamples = 0
    }

    private companion object {
        const val SUMMARY_MILLIS = 60_000L
        val NAMES = mapOf(1 to "GGA", 2 to "RMC", 4 to "PASCD")

        /** The sentence types among 0xFFFA's parameters; the others (0, and 0x8001 on with a number each) as they are. */
        fun components(requested: Set<Int>) = requested.sorted().joinToString(", ") { NAMES[it] ?: "#$it" }
    }
}

/** Runs [action] on a daemon thread every [period] milliseconds, read again each time, until closed. */
fun repeatEvery(period: () -> Long, action: () -> Unit): AutoCloseable {
    val thread = Thread({
        try {
            while (!Thread.currentThread().isInterrupted) {
                val started = System.nanoTime()
                action()
                val left = period() - (System.nanoTime() - started) / 1_000_000
                if (left > 0) Thread.sleep(left)
            }
        } catch (_: InterruptedException) {
        }
    }, "gps-sender").apply {
        isDaemon = true
        start()
    }
    return AutoCloseable { thread.interrupt() }
}
