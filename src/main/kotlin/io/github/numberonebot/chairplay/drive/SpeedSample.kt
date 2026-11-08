package io.github.numberonebot.chairplay.drive

/** One wheel speed of the car, never negative, [timeMillis] on the monotonic clock (the drive's nanoTime in ms). */
data class SpeedSample(val timeMillis: Long, val metersPerSecond: Double)
