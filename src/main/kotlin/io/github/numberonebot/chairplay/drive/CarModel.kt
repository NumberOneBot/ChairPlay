package io.github.numberonebot.chairplay.drive

import kotlin.math.abs
import kotlin.math.exp
import kotlin.math.min
import kotlin.math.sign

/**
 * A car as plain numbers: its power and mass decide how it pulls, its gearing how the engine turns. The defaults are a
 * quick 300 hp car of 1500 kg with a seven-speed automatic and a red line at 7000 rpm, the owner's choice, geared short
 * and gripping all round for the launch: about 4 s to 100 km/h, near 53 km/h in first. Only the speedometer limits the
 * speed ([limiterKmh]): the top gear reaches the red line near 307 km/h, past its end, and the drag stops a 300 hp car
 * near 283 km/h. After each shift at full throttle the engine stays well above where it shifts down.
 */
data class CarSpec(
    val massKg: Double = 1500.0,
    val powerHp: Double = 300.0,
    val idleRpm: Double = 700.0,
    val redlineRpm: Double = 7000.0,
    val gearRatios: List<Double> = listOf(3.6, 2.45, 1.8, 1.4, 1.12, 0.88, 0.62),
    val finalDrive: Double = 4.3,
    val wheelRadiusMeters: Double = 0.31,
    /** Drag coefficient times frontal area. */
    val dragAreaSquareMeters: Double = 0.65,
    val rollingResistance: Double = 0.012,
    /** Where the engine stops pulling, in km/h: the end of the speedometer ([GpsSettings.driven] sets it in its units). */
    val limiterKmh: Double = GaugeScale.speedometer().max,
    val handling: Handling = Handling.NORMAL,
    /**
     * Whether the brake held at a standstill backs the car up. Off for now, at the owner's wish (2026-10-24): the brake
     * only holds the car. Make the default true to have it back.
     */
    val reverse: Boolean = false,
)

/** The pedals and the wheel, each from 0 to 1; steer from -1 (left) to 1 (right). */
data class CarInput(val throttle: Double = 0.0, val brake: Double = 0.0, val steer: Double = 0.0)

/**
 * Where the car is in its motion: [speed] in m/s, negative backing up; [headingDegrees] 0 north, 90 east; [gear] -1 for
 * reverse, 0 neutral (only a game's), else 1 up to the top gear; [parked] in park with the engine off.
 */
data class CarState(
    val speed: Double = 0.0,
    val headingDegrees: Double = 0.0,
    val rpm: Double = 0.0,
    val gear: Int = 1,
    val parked: Boolean = false,
) {
    val speedKmh: Double get() = abs(speed) * 3.6

    /** The selector as the gauges show it: P, R, N (a game's neutral), or D with the gear. */
    val gearName: String get() = if (parked) "P" else if (gear < 0) "R" else if (gear == 0) "N" else "D$gear"
}

/**
 * Basic car physics with arcade handling. The engine gives its power at every speed, so the pull is power over speed,
 * held at a launch by what the driven wheels grip and cut at the limiter; drag, rolling resistance and, off the
 * throttle, the engine take it back.
 * An automatic gearbox only turns the tachometer. The wheel turns the car without slip: standing and slow at the
 * handling's pace, a standing car on the spot, and at speed as a lateral pull allows. Held at a standstill, the brake
 * backs the car up, when the car has reverse ([CarSpec.reverse]).
 */
class CarModel(val spec: CarSpec, initial: CarState = CarState()) {
    // A running engine idles at least; a game's may rev past this car's red line, and its tachometer shows it so.
    var state: CarState = initial.copy(rpm = if (initial.parked) 0.0 else initial.rpm.coerceAtLeast(spec.idleRpm))
        private set

    /** Metres covered by the last step, negative backing up. */
    var lastMeters: Double = 0.0
        private set

    private val wattsAtWheels = spec.powerHp * WATTS_PER_HP * DRIVETRAIN_EFFICIENCY
    private val gripNewtons = spec.massKg * GRAVITY * DRIVEN_GRIP
    private var standstillBrakeSeconds = 0.0
    private var standingSeconds = 0.0
    // Extra rpm above idle just after the engine starts out of park, decaying away: the cold-start flare.
    private var startFlareRpm = 0.0

    fun step(input: CarInput, dt: Double): CarState {
        val throttle = input.throttle.coerceIn(0.0, 1.0)
        val brake = input.brake.coerceIn(0.0, 1.0)
        val steer = input.steer.coerceIn(-1.0, 1.0)
        val before = state.speed
        // A game's gear (its neutral, a truck's twelfth) is only shown: the model drives on in a gear it has.
        var gear = if (state.gear < 0) -1 else state.gear.coerceIn(1, spec.gearRatios.size)
        val stopped = abs(before) < STOPPED_SPEED

        // Standing a while, the car parks and stops its engine; the throttle starts it in drive.
        standingSeconds = if (before == 0.0 && throttle == 0.0) standingSeconds + dt else 0.0
        val wasParked = state.parked
        var parked = wasParked
        if (parked && throttle > 0) {
            parked = false
            gear = 1
        }
        if (standingSeconds >= PARK_SECONDS) parked = true

        // In reverse the pedals swap: the brake drives back, the throttle brakes. Stopped, the throttle selects drive,
        // and the brake held a moment selects reverse.
        standstillBrakeSeconds = if (stopped && gear > 0 && brake > 0 && throttle == 0.0) standstillBrakeSeconds + dt else 0.0
        if (stopped && gear < 0 && throttle > 0 && brake == 0.0) gear = 1
        if (spec.reverse && standstillBrakeSeconds >= REVERSE_HOLD_SECONDS) {
            gear = -1
            parked = false
        }
        // Starting out of park flares the engine; it then settles back to idle over FLARE_SECONDS.
        if (wasParked && !parked) startFlareRpm = COLD_START_RPM - spec.idleRpm
        val drive = if (gear > 0) throttle else brake
        val stopping = if (gear > 0) brake else throttle
        val direction = if (gear > 0) 1.0 else -1.0

        val wheelRpm = wheelRpm(abs(before), gear)
        var force = drive * min(gripNewtons, wattsAtWheels / maxOf(abs(before), POWER_FLOOR_SPEED))
        if (gear > 0 && wheelRpm >= spec.redlineRpm) force = 0.0
        if (gear > 0) force *= ((spec.limiterKmh / 3.6 - abs(before)) / LIMITER_BAND).coerceIn(0.0, 1.0)
        if (gear < 0 && abs(before) >= REVERSE_TOP_SPEED) force = 0.0
        var speed = before + direction * force / spec.massKg * dt
        val resistance = spec.rollingResistance * GRAVITY + AIR_DENSITY / 2 * spec.dragAreaSquareMeters * speed * speed / spec.massKg
        // The engine brakes a moving car in gear off the throttle, fading over the pedal's first tenth; a standing car
        // still pulls away on a light pedal.
        val engineBraking = if (gear > 0 && !stopped) ENGINE_BRAKING * (1 - drive / ENGINE_BRAKING_PEDAL).coerceAtLeast(0.0) else 0.0
        val slowing = (resistance + engineBraking + stopping * BRAKE_DECELERATION) * dt
        // Resistance and brakes stop the car; they never push it the other way, nor does a pedal too light to beat them.
        val halts = abs(speed) <= slowing
        speed = if (halts) 0.0 else speed - sign(speed) * slowing

        if (gear > 0) gear = shifted(gear, wheelRpm(abs(speed), gear), throttle)
        val launch = spec.idleRpm + (LAUNCH_RPM - spec.idleRpm) * drive
        // A stopped engine winds down to nothing, a started one up from it; a start flares above idle first.
        val wheels = wheelRpm(abs(speed), gear)
        val target = if (parked) 0.0 else maxOf(wheels, launch, spec.idleRpm + startFlareRpm).coerceIn(spec.idleRpm, spec.redlineRpm)
        startFlareRpm *= exp(-dt / FLARE_SECONDS)
        // Driven up by the wheels the engine turns with them; the launch, a shift's drop and a stop ease.
        val rpm = if (!parked && wheels >= launch && target >= state.rpm) target else target + (state.rpm - target) * exp(-dt / RPM_SECONDS)

        val average = (before + speed) / 2
        // Standing or slow the car turns at its handling's pace, standing on the spot so it can be pointed before it drives
        // off; faster, the grip in a corner takes the turning down. Backing up turns it the other way.
        val turn = spec.handling
        val backwards = if (average < 0) -1.0 else 1.0
        val yawRate = steer * backwards * min(Math.toRadians(turn.turnDegreesPerSecond), turn.lateralAcceleration / maxOf(abs(average), 1.0))
        val heading = ((state.headingDegrees + Math.toDegrees(yawRate * dt)) % 360 + 360) % 360

        lastMeters = average * dt
        state = CarState(speed, heading, rpm.coerceIn(0.0, spec.redlineRpm), gear, parked)
        return state
    }

    private fun wheelRpm(speed: Double, gear: Int): Double {
        val ratio = if (gear < 0) REVERSE_RATIO else spec.gearRatios[gear - 1]
        return speed / spec.wheelRadiusMeters * ratio * spec.finalDrive * 60 / (2 * Math.PI)
    }

    // Up close to the red line at full throttle and early when cruising; down when the engine bogs, sooner with the foot
    // down. The gaps keep a shift from bouncing back: the rpm after an upshift stays above the next downshift point.
    private fun shifted(gear: Int, rpm: Double, throttle: Double): Int {
        val up = SHIFT_UP_GENTLE + (spec.redlineRpm - SHIFT_BELOW_REDLINE - SHIFT_UP_GENTLE) * throttle
        val down = SHIFT_DOWN_GENTLE + (SHIFT_DOWN_FULL - SHIFT_DOWN_GENTLE) * throttle
        return when {
            rpm >= up && gear < spec.gearRatios.size -> gear + 1
            rpm < down && gear > 1 -> gear - 1
            else -> gear
        }
    }

    companion object {
        /** In m/s: where the power at the wheels meets drag and rolling resistance. */
        fun topSpeed(spec: CarSpec): Double {
            val watts = spec.powerHp * WATTS_PER_HP * DRIVETRAIN_EFFICIENCY
            fun resisted(speed: Double) =
                (spec.rollingResistance * spec.massKg * GRAVITY + AIR_DENSITY / 2 * spec.dragAreaSquareMeters * speed * speed) * speed
            var low = 0.0
            var high = 200.0
            repeat(60) {
                val middle = (low + high) / 2
                if (resisted(middle) < watts) low = middle else high = middle
            }
            return low
        }

        private const val WATTS_PER_HP = 745.7
        private const val DRIVETRAIN_EFFICIENCY = 0.85
        private const val GRAVITY = 9.81
        private const val AIR_DENSITY = 1.2
        /** What the driven wheels grip at a launch, in the car's weight: all four, on a grip of 1. */
        private const val DRIVEN_GRIP = 1.0
        /** Below it the pull is the grip's, not power over a speed near zero. */
        private const val POWER_FLOOR_SPEED = 3.0
        /** m/s² at full brake, a sports car's on sticky tyres: about 25 m from 100 km/h. */
        private const val BRAKE_DECELERATION = 14.0
        /** m/s² off the throttle, a lower gear's engine braking: 100 km/h falls by about 20 in 3 s. */
        private const val ENGINE_BRAKING = 1.5
        private const val ENGINE_BRAKING_PEDAL = 0.1
        /** m/s under the limit over which the pull fades out, so the speed settles on it. */
        private const val LIMITER_BAND = 1.0
        private const val STOPPED_SPEED = 0.3
        private const val REVERSE_HOLD_SECONDS = 0.4
        /** How long the car stands before it parks and stops its engine. */
        private const val PARK_SECONDS = 5.0
        private const val REVERSE_TOP_SPEED = 20 / 3.6
        private const val REVERSE_RATIO = 3.4
        /** What the engine revs to as the clutch slips at a launch, at full throttle. */
        private const val LAUNCH_RPM = 3500.0
        private const val RPM_SECONDS = 0.15
        /** The cold-start flare: the engine jumps to this as it starts out of park, then settles to idle. */
        private const val COLD_START_RPM = 1300.0
        private const val FLARE_SECONDS = 0.9
        private const val SHIFT_UP_GENTLE = 2500.0
        /** How far below the red line full throttle shifts up. */
        private const val SHIFT_BELOW_REDLINE = 200.0
        private const val SHIFT_DOWN_GENTLE = 1200.0
        private const val SHIFT_DOWN_FULL = 2700.0
    }
}

/**
 * How the car turns, gps.car.handling: how fast at full lock standing and slow, in degrees a second, and the grip in a
 * corner that takes that down at speed ([lateralAcceleration] in m/s², a road car's about 9).
 */
enum class Handling(val key: String, val turnDegreesPerSecond: Double, val lateralAcceleration: Double) {
    GENTLE("gentle", 70.0, 9.0),
    NORMAL("normal", 90.0, 12.0),
    SHARP("sharp", 110.0, 16.0),
}
