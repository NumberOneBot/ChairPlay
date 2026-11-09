package io.github.numberonebot.chairplay.platform.windows

import io.github.numberonebot.chairplay.drive.GeoPoint
import io.github.numberonebot.chairplay.platform.LocationResult
import io.github.numberonebot.chairplay.platform.LocationSource

/**
 * Where Windows locates this PC, through its Location API (the one .NET's GeoCoordinateWatcher uses), for putting the
 * GPS car there. It answers within [WAIT_SECONDS], with a place, a refusal (location off, or desktop apps not let use
 * it), or no fix.
 */
internal object WindowsLocation : LocationSource {
    private const val WAIT_SECONDS = 10L

    /** Asks Windows; blocks for up to about [WAIT_SECONDS] and a few seconds more. */
    override fun read(): LocationResult =
        onComThread("location", (WAIT_SECONDS + 10) * 1000) {
            val (status, place) = LocationApi.poll(WAIT_SECONDS * 1000)
            result(status, place)
        } ?: LocationResult.Unknown

    /** What the report's [status] and its latitude and longitude, if one came, say; a place off the globe is none. */
    internal fun result(status: Int, place: Pair<Double, Double>?): LocationResult = when {
        status == LocationApi.REPORT_ACCESS_DENIED -> LocationResult.Denied
        status != LocationApi.REPORT_RUNNING || place == null -> LocationResult.Unknown
        place.first !in -90.0..90.0 || place.second !in -180.0..180.0 -> LocationResult.Unknown
        else -> LocationResult.Found(GeoPoint(place.first, place.second))
    }
}
