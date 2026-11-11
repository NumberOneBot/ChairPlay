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
}
