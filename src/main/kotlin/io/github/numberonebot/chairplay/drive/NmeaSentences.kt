package io.github.numberonebot.chairplay.drive

import java.time.Instant
import java.time.ZoneOffset
import java.util.Locale
import kotlin.math.abs

/** gps.nmea: which sentences carry the car's place, an experiment in what the iPhone takes. */
enum class NmeaDialect(val key: String) {
    /** DiPlay's (after LIVI): $GPGGA and $GPRMC. */
    CLASSIC("classic"),

    /** A current GNSS receiver's: $GNGGA, $GNRMC with its mode, $GNGSA with the fix's precision, $GNVTG with the track. */
    MODERN("modern"),
}

/**
 * gps.framing: how an update's sentences go into LocationInformation messages. The iPhone follows a sentence in a message
 * of its own, or in a parameter of its own; the sentences joined into one string, as DiPlay sends them, it ignores, and
 * keeps its own location (checked with Apple Maps, iOS 26, 2026-10-24).
 */
enum class NmeaFraming(val key: String) {
    /** As DiPlay sends them: all of them as one string, the message's one NMEA parameter. */
    JOINED("joined"),

    /** Each sentence in a message of its own. */
    MESSAGES("messages"),

    /** Each sentence a parameter of one message, the NMEA parameter repeated. */
    PARAMETERS("params"),
}

/** Where the car is and how it goes, for one fix: its [course] over the ground, null standing still. */
data class NmeaFix(val position: GeoPoint, val metersPerSecond: Double, val course: Double?)

/**
 * The car's fix as NMEA sentences, in [NmeaDialect.CLASSIC] DiPlay's NmeaLocationEncoder character for character on a
 * whole second, but with the time's hundredths, so several fixes a second each have their own.
 */
object NmeaSentences {
    fun position(fix: NmeaFix, millis: Long, dialect: NmeaDialect): String {
        val utc = Instant.ofEpochMilli(millis).atOffset(ZoneOffset.UTC)
        val time = format("%02d%02d%02d.%02d", utc.hour, utc.minute, utc.second, utc.nano / 10_000_000)
        val date = format("%02d%02d%02d", utc.dayOfMonth, utc.monthValue, utc.year % 100)
        val (latitude, north) = coordinate(fix.position.latitude, latitude = true)
        val (longitude, east) = coordinate(fix.position.longitude, latitude = false)
        val knots = format("%.2f", fix.metersPerSecond.coerceAtLeast(0.0) * KNOTS_PER_METER_PER_SECOND)
        val course = fix.course?.let { format("%.2f", it) }.orEmpty()
        val place = "$latitude,$north,$longitude,$east"
        return when (dialect) {
            NmeaDialect.CLASSIC -> sentence("GPGGA,$time,$place,1,08,$HDOP,0.0,M,0.0,M,,") +
                sentence("GPRMC,$time,A,$place,$knots,$course,$date,,")
            NmeaDialect.MODERN -> sentence("GNGGA,$time,$place,1,$SATELLITES_USED,$HDOP,0.0,M,0.0,M,,") +
                sentence("GNRMC,$time,A,$place,$knots,$course,$date,,,A") +
                sentence("GNGSA,A,3,${MODELED_USED_PRNS.joinToString(",") { format("%02d", it) }}" +
                    ",".repeat(12 - MODELED_USED_PRNS.size) + ",$PDOP,$HDOP,$VDOP") +
                sentence("GNVTG,$course,T,,M,$knots,N,${format("%.2f", fix.metersPerSecond.coerceAtLeast(0.0) * 3.6)},K,A")
        }
    }

    /** "$body*hh\r\n", the checksum the XOR of the body's characters. */
    fun sentence(body: String): String = "$$body*${format("%02X", body.fold(0) { sum, character -> sum xor character.code })}\r\n"

    // As DiPlay writes them: degrees and minutes to four places, a minute that rounds to 60 carried into the degree.
    private fun coordinate(value: Double, latitude: Boolean): Pair<String, String> {
        val absolute = abs(value)
        var degrees = absolute.toInt()
        var minutes = (absolute - degrees) * 60
        if (minutes >= 60 - MINUTE_ROUNDING) {
            degrees += 1
            minutes = 0.0
        }
        val text = format(if (latitude) "%02d" else "%03d", degrees) + format("%07.4f", minutes)
        val side = if (latitude) (if (value >= 0) "N" else "S") else (if (value >= 0) "E" else "W")
        return text to side
    }

    private fun format(format: String, vararg arguments: Any): String = String.format(Locale.US, format, *arguments)

    private const val KNOTS_PER_METER_PER_SECOND = 1.94384449
    private const val MINUTE_ROUNDING = 0.00005
    /** DiPlay's from an accuracy of 2.5 m, its best. */
    private const val HDOP = "0.5"
    private const val PDOP = "0.9"
    private const val VDOP = "0.7"
    private const val SATELLITES_USED = "10"
    // The satellites the modern dialect's GSA says the fix uses.
    private val MODELED_USED_PRNS = listOf(2, 5, 7, 9, 13, 15, 18, 20, 24, 29)
}
