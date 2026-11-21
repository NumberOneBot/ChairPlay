package io.github.numberonebot.chairplay.drive.track

import io.github.numberonebot.chairplay.drive.GaugeLimits
import io.github.numberonebot.chairplay.drive.GeoPoint
import java.io.InputStream
import java.time.OffsetDateTime
import java.time.format.DateTimeParseException
import javax.xml.stream.XMLInputFactory
import javax.xml.stream.XMLStreamConstants
import javax.xml.stream.XMLStreamException
import javax.xml.stream.XMLStreamReader

/** Reads the first `trk` of a GPX 1.0 or 1.1 file, with optional ChairPlay gauge limits. */
object GpxReader {
    private const val GPX_NAMESPACE = "http://www.topografix.com/GPX/1/1"
    private const val TELEMETRY_NAMESPACE = "urn:chairplay:gpx:telemetry:1"

    private class RawPoint(
        val epochSeconds: Double, val position: GeoPoint, val altitude: Double?,
        val speed: Double?, val rpm: Double?, val gear: Int?,
    )

    private class RawTrack(val segments: List<List<RawPoint>>, val gaugeLimits: GaugeLimits)

    fun read(name: String, input: InputStream): TrackRecording {
        val track = try {
            firstTrack(input)
        } catch (e: XMLStreamException) {
            throw TrackFormatException(TrackFormatReason.NOT_GPX)
        } ?: throw TrackFormatException(TrackFormatReason.NO_TRACK)
        val start = track.segments.firstOrNull { it.isNotEmpty() }?.first()?.epochSeconds
            ?: throw TrackFormatException(TrackFormatReason.NO_TIMES)
        val segments = track.segments.filter { it.isNotEmpty() }.map { segment ->
            segment.map { TrackPoint(it.epochSeconds - start, it.position, it.altitude, it.speed, rpm = it.rpm, gear = it.gear) }
        }
        val points = track.segments.asSequence().flatten()
        val limits = track.gaugeLimits.copy(
            peakSpeed = points.mapNotNull { it.speed }.maxOrNull(),
            peakRpm = points.mapNotNull { it.rpm }.maxOrNull(),
        )
        return TrackRecording(name, segments, limits)
    }

    /** The first track's segments with their timed points, or null when the file has no track point at all. */
    private fun firstTrack(input: InputStream): RawTrack? {
        val factory = XMLInputFactory.newFactory()
        factory.setProperty(XMLInputFactory.SUPPORT_DTD, false)
        factory.setProperty(XMLInputFactory.IS_SUPPORTING_EXTERNAL_ENTITIES, false)
        val reader = factory.createXMLStreamReader(input)
        try {
            val segments = mutableListOf<MutableList<RawPoint>>()
            var limits = GaugeLimits.DEFAULT
            var trackDepth = 0
            var depth = 0
            var sawPoint = false
            while (reader.hasNext()) {
                when (reader.next()) {
                    XMLStreamConstants.START_ELEMENT -> {
                        depth++
                        when (reader.localName) {
                            "trk" -> if (trackDepth == 0) trackDepth = depth
                            "trkseg" -> if (trackDepth != 0) segments.add(mutableListOf())
                            "trkpt" -> if (trackDepth != 0) {
                                if (segments.isEmpty()) segments.add(mutableListOf())
                                sawPoint = true
                                readPoint(reader)?.let { point ->
                                    val segment = segments.last()
                                    // Ignore repeats and clock steps back inside the segment.
                                    if (segment.isEmpty() || point.epochSeconds > segment.last().epochSeconds) segment.add(point)
                                }
                                depth--
                            }
                            "extensions" -> if (trackDepth != 0 && depth == trackDepth + 1 && reader.namespaceURI == GPX_NAMESPACE) {
                                val fields = readExtension(reader, "gauges", "redline", "top_speed")
                                limits = GaugeLimits(
                                    redlineRpm = fields["redline"]?.toDoubleOrNull()?.takeIf { it > 1000 && it <= 20000 },
                                    topSpeed = readGaugeSpeed(fields["top_speed"])?.takeIf { it > 0 },
                                )
                                depth--
                            }
                        }
                    }
                    XMLStreamConstants.END_ELEMENT -> {
                        if (reader.localName == "trk" && depth == trackDepth) return if (sawPoint) RawTrack(segments, limits) else null
                        depth--
                    }
                }
            }
            return if (sawPoint) RawTrack(segments, limits) else null
        } finally {
            reader.close()
        }
    }

    /** Reads a `trkpt` (the reader stands on its start) through its end; null when it has no time or no place. */
    private fun readPoint(reader: XMLStreamReader): RawPoint? {
        val latitude = reader.getAttributeValue(null, "lat")?.toDoubleOrNull()
        val longitude = reader.getAttributeValue(null, "lon")?.toDoubleOrNull()
        var altitude: Double? = null
        var time: Double? = null
        var speed: Double? = null
        var rpm: Double? = null
        var gear: Int? = null
        var depth = 1
        while (depth > 0 && reader.hasNext()) {
            when (reader.next()) {
                XMLStreamConstants.START_ELEMENT -> {
                    depth++
                    // Only the point's own children: an extension may carry its own time or ele.
                    if (depth == 2) when (reader.localName) {
                        "ele" -> altitude = reader.elementText.trim().toDoubleOrNull().also { depth-- }
                        "time" -> time = parseTime(reader.elementText).also { depth-- }
                        "extensions" -> if (reader.namespaceURI == GPX_NAMESPACE) {
                            val fields = readExtension(reader, "telemetry", "speed", "rpm", "gear")
                            speed = readGaugeSpeed(fields["speed"])
                            rpm = fields["rpm"]?.toIntOrNull()?.takeIf { it in 0..MAX_RECORDED_RPM }?.toDouble()
                            gear = fields["gear"]?.toIntOrNull()?.takeIf { it >= -1 }
                            depth--
                        }
                    }
                }
                XMLStreamConstants.END_ELEMENT -> depth--
            }
        }
        if (latitude == null || longitude == null || time == null) return null
        return RawPoint(time, GeoPoint(latitude, longitude), altitude, speed, rpm, gear)
    }

    /** Consumes an extensions element, taking only text fields directly inside the named ChairPlay block. */
    private fun readExtension(reader: XMLStreamReader, block: String, vararg names: String): Map<String, String> {
        val fields = mutableMapOf<String, String>()
        val text = StringBuilder()
        var depth = 1
        var inBlock = false
        var field: String? = null
        while (depth > 0 && reader.hasNext()) {
            when (reader.next()) {
                XMLStreamConstants.START_ELEMENT -> when (++depth) {
                    2 -> inBlock = reader.namespaceURI == TELEMETRY_NAMESPACE && reader.localName == block
                    3 -> if (inBlock && reader.namespaceURI == TELEMETRY_NAMESPACE && reader.localName in names) {
                        field = reader.localName
                    }
                    4 -> field = null // Nested markup is not a numeric value.
                }
                XMLStreamConstants.CHARACTERS, XMLStreamConstants.CDATA -> if (field != null) text.append(reader.text)
                XMLStreamConstants.END_ELEMENT -> if (depth-- == 3) {
                    field?.let { fields.putIfAbsent(it, text.toString().trim()) }
                    field = null
                    text.setLength(0)
                }
            }
        }
        return fields
    }

    private fun readGaugeSpeed(text: String?): Double? = text?.toDoubleOrNull()?.takeIf { it in 0.0..MAX_RECORDED_SPEED }

    private fun parseTime(text: String): Double? =
        try {
            val t = OffsetDateTime.parse(text.trim())
            t.toEpochSecond() + t.nano / 1e9
        } catch (e: DateTimeParseException) {
            null
        }
}
