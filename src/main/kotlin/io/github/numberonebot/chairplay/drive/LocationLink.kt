package io.github.numberonebot.chairplay.drive

/** A location source that sends at its own pace on the link it is [attach]ed to, rather than DiPlay's once a second. */
interface PacedLocation {
    /**
     * [send] puts one LocationInformation on the link, with each given NMEA text as an NMEA parameter of its own; it
     * throws once the link is gone.
     */
    fun attach(send: (List<String>) -> Unit)
}

/** What the GPS page shows of a phone's GPS: whether it listens, the sentence types it asked for, and the updates sent. */
data class LocationStatus(val listening: Boolean, val requested: Set<Int>, val sent: Long)

/** The car as one connection's location sends it, as the GPS page reads it ([Driving.location]). */
fun interface LocationLink {
    fun status(): LocationStatus
}
