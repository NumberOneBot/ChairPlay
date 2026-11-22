package io.github.numberonebot.chairplay.drive.track

import io.github.numberonebot.chairplay.drive.GaugeLimits
import io.github.numberonebot.chairplay.drive.GeoPoint
import io.github.numberonebot.chairplay.drive.SpeedUnits
import java.io.InputStream

/** Reads position, motion and optional RPM, gear, throttle and brake channels of a VBOX/RaceBox text recording. */
object VboReader {
    private const val SECONDS_PER_DAY = 86_400.0
    private val whitespace = Regex("\\s+")

    fun read(name: String, input: InputStream): TrackRecording = input.bufferedReader().use { reader ->
        val names = mutableListOf<String>()
        var section = ""
        var velocityUnit = "kmh"
        while (true) {
            val line = reader.readLine()?.removePrefix("\uFEFF")?.trim()?.lowercase() ?: break
            if (line.startsWith('[') && line.endsWith(']')) {
                section = line
                if (section == "[data]") break
            } else if (line.isNotEmpty()) {
                when (section) {
                    "[header]" -> {
                        val title = line.split(whitespace, limit = 2)
                        if (title.first() == "velocity") velocityUnit = title.getOrElse(1) { "kmh" }
                    }
                    "[column names]" -> names.addAll(line.split(whitespace))
                }
            }
        }

        val timeColumn = names.indexOf("time")
        val latitudeColumn = names.indexOfFirst { it == "lat" || it == "latitude" }
        val longitudeColumn = names.indexOfFirst { it in setOf("long", "lng", "lon", "longitude") }
        if (section != "[data]" || timeColumn < 0 || latitudeColumn < 0 || longitudeColumn < 0) {
            throw TrackFormatException(TrackFormatReason.NOT_VBO)
        }
        val speedColumn = names.indexOf("velocity")
        val headingColumn = names.indexOf("heading")
        val altitudeColumn = names.indexOf("height")
        val rpmColumn = names.indexOf("rpm")
        val gearColumn = names.indexOf("gear")
        val throttleColumn = names.indexOf("throttle")
        val brakeColumn = names.indexOf("brake")
        val speedFactor = when (velocityUnit) {
            "km/h", "kmh", "kph" -> 1.0 / 3.6
            "mph" -> SpeedUnits.IMPERIAL.toKmh(1.0) / 3.6
            "m/s", "mps" -> 1.0
            else -> null
        }

        val points = mutableListOf<TrackPoint>()
        var sawPosition = false
        var start = 0.0
        var dayOffset = 0.0
        var lastTime = Double.NEGATIVE_INFINITY
        for (raw in reader.lineSequence()) {
            val line = raw.trim()
            if (line.startsWith('[') && line.endsWith(']')) break
            val fields = line.split(whitespace)
            val latitude = fields.number(latitudeColumn)?.div(60)?.takeIf { it in -90.0..90.0 } ?: continue
            // The VBO longitude field uses positive minutes west of Greenwich.
            val longitude = fields.number(longitudeColumn)?.div(-60)?.takeIf { it in -180.0..180.0 } ?: continue
            sawPosition = true
            val clock = fields.number(timeColumn)?.let(::timeOfDay) ?: continue
            if (lastTime - dayOffset - clock > SECONDS_PER_DAY / 2) dayOffset += SECONDS_PER_DAY
            val time = clock + dayOffset
            if (time <= lastTime) continue
            if (points.isEmpty()) start = time
            lastTime = time

            val speed = speedFactor?.let { factor -> fields.number(speedColumn)?.times(factor) }
                ?.takeIf { it in 0.0..MAX_RECORDED_SPEED }
            val gear = fields.number(gearColumn)
                ?.takeIf { it in -1.0..Int.MAX_VALUE.toDouble() && it % 1.0 == 0.0 }
                ?.toInt()
            points.add(TrackPoint(
                time - start,
                GeoPoint(latitude, longitude),
                altitude = fields.number(altitudeColumn),
                speed = speed,
                heading = fields.number(headingColumn)?.takeIf { it in 0.0..360.0 },
                rpm = fields.number(rpmColumn)?.takeIf { it in 0.0..MAX_RECORDED_RPM.toDouble() },
                gear = gear,
                throttle = pedalShare(fields.number(throttleColumn)),
                brake = pedalShare(fields.number(brakeColumn)),
            ))
        }

        if (!sawPosition) throw TrackFormatException(TrackFormatReason.NO_TRACK)
        if (points.isEmpty()) throw TrackFormatException(TrackFormatReason.NO_TIMES)
        val peakSpeed = points.asSequence().mapNotNull { it.speed }.maxOrNull()
        val peakRpm = points.asSequence().mapNotNull { it.rpm }.maxOrNull()
        TrackRecording(name, listOf(points), GaugeLimits(peakSpeed = peakSpeed, peakRpm = peakRpm))
    }

    private fun List<String>.number(index: Int): Double? = getOrNull(index)?.toDoubleOrNull()?.takeIf { it.isFinite() }

    private fun timeOfDay(hhmmss: Double): Double? {
        if (hhmmss !in 0.0..<240_000.0) return null
        val hours = (hhmmss / 10_000).toInt()
        val minutes = (hhmmss / 100).toInt() % 100
        val seconds = hhmmss % 100
        if (minutes >= 60 || seconds >= 60) return null
        return hours * 3600 + minutes * 60 + seconds
    }
}
