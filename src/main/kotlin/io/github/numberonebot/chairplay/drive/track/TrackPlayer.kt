package io.github.numberonebot.chairplay.drive.track

enum class TrackPhase(val key: String) { STOPPED("stopped"), PLAYING("playing"), PAUSED("paused") }

/**
 * Plays a [recording] against a clock. The window loop calls [advance] while HTTP threads call
 * play, pause and stop, so every member is synchronized.
 */
class TrackPlayer(val recording: TrackRecording, private val nanoTime: () -> Long) {
    private var currentPhase = TrackPhase.STOPPED
    private var currentTime = 0.0
    private var lastNanos = 0L

    val phase: TrackPhase @Synchronized get() = currentPhase

    val time: Double @Synchronized get() = currentTime

    /** The phase and the time read together, so a stop on another thread never splits them. */
    @Synchronized
    fun state(): Pair<TrackPhase, Double> = currentPhase to currentTime

    /** True while the track drives the car: playing or paused. */
    val owns: Boolean @Synchronized get() = currentPhase != TrackPhase.STOPPED

    @Synchronized
    fun play() {
        if (currentPhase == TrackPhase.PLAYING) return
        if (currentPhase == TrackPhase.STOPPED && currentTime >= recording.duration) currentTime = 0.0
        lastNanos = nanoTime()
        currentPhase = TrackPhase.PLAYING
    }

    @Synchronized
    fun pause() {
        if (currentPhase != TrackPhase.PLAYING) return
        advance()
        if (currentPhase == TrackPhase.PLAYING) currentPhase = TrackPhase.PAUSED
    }

    @Synchronized
    fun stop() {
        currentPhase = TrackPhase.STOPPED
        currentTime = 0.0
    }

    @Synchronized
    fun advance() {
        if (currentPhase != TrackPhase.PLAYING) return
        val now = nanoTime()
        currentTime += (now - lastNanos) / 1e9
        lastNanos = now
        if (currentTime >= recording.duration) {
            currentTime = recording.duration
            currentPhase = TrackPhase.STOPPED
        }
    }
}
