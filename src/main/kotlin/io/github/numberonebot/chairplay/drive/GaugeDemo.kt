package io.github.numberonebot.chairplay.drive

/**
 * The [engine]'s readings and the [car]'s place, heading and steering, with the source's [limits] or the GPS car's scales.
 */
internal fun gaugeView(
    settings: GpsSettings, engine: DriveSnapshot, car: DriveSnapshot, redlineRpm: Double, size: GaugeSize,
    limits: GaugeLimits,
) = GaugeView(
    speedKmh = engine.car.speedKmh,
    rpm = engine.car.rpm,
    gear = engine.car.gearName,
    throttle = engine.input.throttle,
    brake = engine.input.brake,
    headingDegrees = car.car.headingDegrees,
    speedometer = limits.speedometer(settings.units, settings.design.speedometer),
    tachometer = limits.tachometer(redlineRpm, settings.design.tachometer),
    position = car.position,
    size = size,
    steer = car.input.steer,
    wheelMode = settings.wheel,
    units = settings.units,
    design = settings.design,
)

/**
 * The Experiments page's gauges demo: drive mode's gauges, large, in the style set, over a made-up run of the GPS car's
 * engine on its own [Drive], so the car itself stays where it is and the iPhone hears nothing: full throttle up the
 * gears for [THROTTLE_SECONDS], the brakes to a stop, a pause, and the same again until it ends. No GPS or iPhone is
 * needed; the gauges show the car's place and heading as they are.
 */
class GaugeDemo(private val driving: Driving, private val nanoTime: () -> Long = System::nanoTime) {
    @Volatile private var engine: Drive? = null
    private var phaseStarted = 0L
    private var braking = false
    private var stoppedAt: Long? = null
    private var thread: Thread? = null

    val playing: Boolean get() = engine != null

    /** What the gauges show while it plays; null while it does not. */
    fun view(): GaugeView? {
        val engine = engine ?: return null
        return gaugeView(
            driving.settings, engine.snapshot(), driving.drive.snapshot(), engine.spec.redlineRpm, GaugeSize.LARGE, GaugeLimits.DEFAULT,
        )
    }

    /** Plays it, [repaint] after each frame, until [stop]. */
    @Synchronized
    fun start(repaint: () -> Unit) {
        stop()
        begin()
        thread = Thread({
            try {
                while (!Thread.currentThread().isInterrupted) {
                    step()
                    repaint()
                    Thread.sleep(FRAME_MILLIS)
                }
            } catch (_: InterruptedException) {
            }
        }, "gauge-demo").apply {
            isDaemon = true
            start()
        }
    }

    /** Ends it; the gauges go back to drive mode's, or away. */
    @Synchronized
    fun stop() {
        thread?.interrupt()
        thread = null
        end()
    }

    /** A new run from a standstill, at the car's place and heading. */
    internal fun begin() {
        val car = driving.drive.snapshot()
        engine = Drive(driving.settings.driven, car.position, car.car.headingDegrees, nanoTime)
        phaseStarted = nanoTime()
        braking = false
        stoppedAt = null
    }

    internal fun end() {
        engine = null
    }

    /** One frame of the run: the pedals as its phase has them, and the engine moved on. */
    internal fun step() {
        val engine = engine ?: return
        val now = nanoTime()
        val state = engine.snapshot().car
        when {
            !braking && now - phaseStarted >= (THROTTLE_SECONDS * 1e9).toLong() -> braking = true
            braking && state.speed <= 0.0 -> {
                val stopped = stoppedAt ?: now.also { stoppedAt = it }
                if (now - stopped >= (PAUSE_SECONDS * 1e9).toLong()) {
                    braking = false
                    stoppedAt = null
                    phaseStarted = now
                }
            }
        }
        engine.input = when {
            !braking -> CarInput(throttle = 1.0)
            stoppedAt == null -> CarInput(brake = 1.0)
            else -> CarInput()
        }
        engine.advance()
    }

    companion object {
        /** How long the throttle stays down: up through the gears to well past 100 km/h. */
        const val THROTTLE_SECONDS = 6.0
        const val PAUSE_SECONDS = 1.0
        private const val FRAME_MILLIS = 16L
    }
}
