package io.github.numberonebot.chairplay.drive

import io.github.numberonebot.chairplay.platform.windows.onComThread

/**
 * Where Windows locates this PC, through its Location API (the one .NET's GeoCoordinateWatcher uses), for putting the
 * GPS car there. It answers within [WAIT_SECONDS], with a place, a refusal (location off, or desktop apps not let use
 * it), or no fix.
 */
object WindowsLocation {
    sealed interface Result

    data class Found(val place: GeoPoint) : Result

    /** Location is off for this PC, or desktop apps may not use it. */
    data object Denied : Result

    /** No position came in time, or the service could not be asked. */
    data object Unknown : Result

    private const val WAIT_SECONDS = 10L

    /** Asks Windows; blocks for up to about [WAIT_SECONDS] and a few seconds more. */
    fun read(): Result =
        onComThread("location", (WAIT_SECONDS + 10) * 1000) {
            val (status, place) = LocationApi.poll(WAIT_SECONDS * 1000)
            result(status, place)
        } ?: Unknown

    /** What the report's [status] and its latitude and longitude, if one came, say; a place off the globe is none. */
    internal fun result(status: Int, place: Pair<Double, Double>?): Result = when {
        status == LocationApi.REPORT_ACCESS_DENIED -> Denied
        status != LocationApi.REPORT_RUNNING || place == null -> Unknown
        place.first !in -90.0..90.0 || place.second !in -180.0..180.0 -> Unknown
        else -> Found(GeoPoint(place.first, place.second))
    }
}
