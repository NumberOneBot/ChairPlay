package io.github.numberonebot.chairplay.drive

import kotlin.math.roundToInt

/**
 * What the GPS page shows of [driving]: the car's place and motion, the controls driving it, what the iPhone asked for,
 * and the recording loaded for track playback (null while none is).
 */
internal fun drivingState(driving: Driving): Map<String, Any?> {
    val snapshot = driving.drive.snapshot()
    val car = snapshot.car
    val status = driving.location?.status()
    return linkedMapOf(
        "position" to if (driving.placed) listOf(snapshot.position.latitude, snapshot.position.longitude) else null,
        "heading" to car.headingDegrees.roundToInt() % 360,
        "speed" to car.speedKmh.roundToInt(),
        "rpm" to car.rpm.roundToInt(),
        "gear" to car.gearName,
        "throttle" to snapshot.input.throttle,
        "brake" to snapshot.input.brake,
        "keyboard" to driving.control.keyboard,
        "gamepad" to driving.control.gamepad,
        "listening" to (status?.listening ?: false),
        "sentences" to status?.requested?.map { SENTENCES[it] ?: "#$it" },
        "sent" to (status?.sent ?: 0L),
        "track" to driving.track?.let { player ->
            // One read of both: a stop between two would show the time of one phase with the other.
            val (phase, time) = player.state()
            mapOf(
                "name" to player.recording.name,
                "phase" to phase.key,
                "time" to (time * 10).roundToInt() / 10.0,
                "duration" to player.recording.duration,
                "distanceKm" to (player.recording.distanceMeters / 10).roundToInt() / 100.0,
            )
        },
    )
}

/** 0xFFFA's selectors, identification fields 17 to 20 less 16. */
private val SENTENCES = mapOf(1 to "GGA", 2 to "RMC", 4 to "PASCD")
