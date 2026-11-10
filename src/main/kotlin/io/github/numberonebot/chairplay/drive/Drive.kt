package io.github.numberonebot.chairplay.drive

import kotlin.math.abs
import kotlin.math.min

/** What the car is doing at one moment: for the gauges, the GPS page, and the position sent to the phone. */
data class DriveSnapshot(val position: GeoPoint, val car: CarState, val input: CarInput)

/**
 * The spoofed car: a [CarModel] placed on the earth. The window loop calls [advance] on each pass while driving and
 * sets [input] from the gamepad or the keys; the phone's location reads [snapshot] and [drainSpeed] for its sentences.
 */
class Drive(
    spec: CarSpec,
    start: GeoPoint,
    headingDegrees: Double,
    private val nanoTime: () -> Long = System::nanoTime,
) {
    var spec: CarSpec = spec
        private set
    private val lock = Any()
    // It starts parked, its engine off until the throttle.
    private var car = CarModel(spec, CarState(headingDegrees = headingDegrees, parked = true))
    private var position = start
    private var lastNanos: Long? = null
    private var lastSampleNanos: Long? = null
    private val samples = ArrayDeque<SpeedSample>()

    /** The pedals and the wheel as the controls hold them now. */
    @Volatile var input = CarInput()

    val moving: Boolean get() = synchronized(lock) { car.state.speed != 0.0 }

    /**
     * Moves the car on by the time since the last call, in steps of at most [STEP_SECONDS]. A longer pause (the window
     * dragged, the app stalled) counts as [MAX_GAP_SECONDS], so the car never jumps down the road.
     */
    fun advance(): Unit = synchronized(lock) {
        val now = nanoTime()
        val last = lastNanos
        lastNanos = now
        if (last != null) {
            var left = ((now - last) / 1e9).coerceIn(0.0, MAX_GAP_SECONDS)
            while (left > 1e-9) {
                val dt = min(left, STEP_SECONDS)
                val state = car.step(input, dt)
                position = position.moved(state.headingDegrees, car.lastMeters)
                left -= dt
            }
        }
        sample(now)
    }

    fun snapshot(): DriveSnapshot = synchronized(lock) { DriveSnapshot(position, car.state, input) }

    /** The wheel speeds sampled since the last call, oldest first. */
    fun drainSpeed(): List<SpeedSample> = synchronized(lock) { samples.toList().also { samples.clear() } }

    /** Puts the car at [start] facing [headingDegrees], parked: a new start from the settings. */
    fun place(start: GeoPoint, headingDegrees: Double): Unit = synchronized(lock) {
        car = CarModel(spec, CarState(headingDegrees = headingDegrees, parked = true))
        position = start
        samples.clear()
    }

    /** Turns the car where it is to face [headingDegrees], as fast as it goes: a heading typed on the GPS page. */
    fun turn(headingDegrees: Double): Unit = synchronized(lock) {
        car = CarModel(spec, car.state.copy(headingDegrees = headingDegrees))
    }

    /** Stops and parks the car where it is, facing as it does: the GPS turned off mid-drive. */
    fun stop(): Unit = synchronized(lock) {
        car = CarModel(spec, CarState(headingDegrees = car.state.headingDegrees, parked = true))
        samples.clear()
    }

    /**
     * Puts the car where a game says it is, facing, as fast (m/s, negative backing up), and in the gear it says: a
     * game's telemetry driving it. The pedals and the wheel are as the game holds them ([input], at rest unless given),
     * and the wheel speed is sampled as [advance] samples it.
     */
    fun follow(
        position: GeoPoint, headingDegrees: Double, speed: Double, rpm: Double?, gear: Int?, input: CarInput = CarInput(),
    ): Unit = synchronized(lock) {
        car = CarModel(spec, CarState(speed, headingDegrees, rpm ?: 0.0, gear ?: 1, parked = false))
        this.position = position
        this.input = input
        val now = nanoTime()
        lastNanos = now
        sample(now)
    }

    /** Makes the car [spec] where it is and as fast as it goes: a new power or mass from the settings. */
    fun respec(spec: CarSpec): Unit = synchronized(lock) {
        this.spec = spec
        car = CarModel(spec, car.state)
    }

    // A wheel speed every 100 ms, the rate a head unit's $PASCD carries; kept short when nobody drains them.
    private fun sample(now: Long) {
        val last = lastSampleNanos
        if (last != null && now - last < SAMPLE_NANOS) return
        lastSampleNanos = now
        samples.addLast(SpeedSample(now / 1_000_000, abs(car.state.speed)))
        while (samples.size > MAX_SAMPLES) samples.removeFirst()
    }

    companion object {
        const val MAX_SAMPLES = 20
        private const val STEP_SECONDS = 1.0 / 60
        private const val MAX_GAP_SECONDS = 0.1
        private const val SAMPLE_NANOS = 100_000_000L
    }
}
