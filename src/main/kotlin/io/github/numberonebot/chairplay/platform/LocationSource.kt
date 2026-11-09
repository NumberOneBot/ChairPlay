package io.github.numberonebot.chairplay.platform

import io.github.numberonebot.chairplay.drive.GeoPoint

/** What the system said when asked where the PC is. */
sealed interface LocationResult {
    data class Found(val place: GeoPoint) : LocationResult

    /** Location is off for this PC, or desktop apps may not use it. */
    data object Denied : LocationResult

    /** No position came in time, or the service could not be asked. */
    data object Unknown : LocationResult
}

/** Where the system locates this PC. */
fun interface LocationSource {
    /** Asks the system; blocks until it answers or gives up. */
    fun read(): LocationResult
}
