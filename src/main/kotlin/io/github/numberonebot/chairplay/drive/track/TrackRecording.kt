package io.github.numberonebot.chairplay.drive.track

import io.github.numberonebot.chairplay.drive.GeoPoint

/** One recorded fix. [seconds] count from the recording's first point; [speed] is in m/s, [heading] in degrees. */
data class TrackPoint(
    val seconds: Double,
    val position: GeoPoint,
    val altitude: Double? = null,
    val speed: Double? = null,
    val heading: Double? = null,
)

/** A file that cannot be played as a track; [reason] is what the owner reads after "Can't play this file". */
class TrackFormatException(val reason: String) : Exception(reason)

/** A recorded drive: runs of points in time order. Positions are not joined across the gap between segments. */
class TrackRecording(val name: String, val segments: List<List<TrackPoint>>) {
    init {
        if (segments.flatten().map { it.seconds }.distinct().size < 2) {
            throw TrackFormatException("Its GPS track is too short.")
        }
    }

    /** The last point's seconds. */
    val duration: Double = segments.last().last().seconds

    /** The length of the track, summed inside segments only. */
    val distanceMeters: Double = segments.sumOf { segment ->
        segment.zipWithNext().sumOf { (a, b) -> distanceMeters(a.position, b.position) }
    }

    private val points: List<TrackPoint> = segments.flatten()

    /** Index of the segment each entry of [points] belongs to. */
    private val segmentOf: IntArray = segments.flatMapIndexed { i, s -> s.map { i } }.toIntArray()

    /** Where the car is [seconds] into the recording, clamped to the recording. */
    fun at(seconds: Double): TrackSample {
        val t = seconds.coerceIn(0.0, duration)
        var lo = 0
        var hi = points.size - 1
        while (lo < hi) {
            val mid = (lo + hi + 1) ushr 1
            if (points[mid].seconds <= t) lo = mid else hi = mid - 1
        }
        val i = lo
        val here = points[i]
        val sameSegmentAfter = i + 1 < points.size && segmentOf[i + 1] == segmentOf[i]
        if (sameSegmentAfter) {
            val next = points[i + 1]
            val span = next.seconds - here.seconds
            val f = if (span > 0.0) ((t - here.seconds) / span).coerceIn(0.0, 1.0) else 0.0
            return TrackSample(
                GeoPoint(
                    here.position.latitude + (next.position.latitude - here.position.latitude) * f,
                    here.position.longitude + (next.position.longitude - here.position.longitude) * f,
                ),
                pairHeading(i, f),
                pairSpeed(i, f),
            )
        }
        // The last point of a segment: a gap follows, or the recording ends.
        val heading = here.heading
            ?: if (i > 0 && segmentOf[i - 1] == segmentOf[i]) pairHeading(i - 1, 1.0) else 0.0
        return TrackSample(here.position, heading, 0.0)
    }

    private fun pairSpeed(i: Int, f: Double): Double {
        val a = points[i]
        val b = points[i + 1]
        val sa = a.speed
        val sb = b.speed
        if (sa != null && sb != null) return sa + (sb - sa) * f
        val span = b.seconds - a.seconds
        return if (span > 0.0) distanceMeters(a.position, b.position) / span else 0.0
    }

    private fun pairHeading(i: Int, f: Double): Double {
        val a = points[i]
        val b = points[i + 1]
        val ha = a.heading
        val hb = b.heading
        if (ha != null && hb != null) return normalize(ha + shortArc(ha, hb) * f)
        return movingHeading(i)
    }

    /** The bearing of pair [i], or, if it moved less than half a meter, that of the nearest moving pair. */
    private fun movingHeading(i: Int): Double {
        val seg = segmentOf[i]
        fun moving(j: Int) = distanceMeters(points[j].position, points[j + 1].position) >= MOVING_METERS
        fun inSegment(j: Int) = j >= 0 && j + 1 < points.size && segmentOf[j] == seg && segmentOf[j + 1] == seg
        val found = (i downTo 0).takeWhile { inSegment(it) }.firstOrNull { moving(it) }
            ?: (i + 1 until points.size - 1).takeWhile { inSegment(it) }.firstOrNull { moving(it) }
            ?: return 0.0
        return bearingDegrees(points[found].position, points[found + 1].position)
    }

    private fun shortArc(from: Double, to: Double): Double = ((to - from + 540.0) % 360.0 + 360.0) % 360.0 - 180.0

    private fun normalize(degrees: Double): Double = ((degrees % 360.0) + 360.0) % 360.0

    private companion object {
        const val MOVING_METERS = 0.5
    }
}

/** The car's place, [heading] in degrees from north and [speed] in m/s at one moment of a recording. */
data class TrackSample(val position: GeoPoint, val heading: Double, val speed: Double)
