package io.github.numberonebot.chairplay.drive.track

import io.github.numberonebot.chairplay.drive.GeoPoint
import java.io.InputStream
import java.time.OffsetDateTime
import java.time.format.DateTimeParseException
import javax.xml.stream.XMLInputFactory
import javax.xml.stream.XMLStreamConstants
import javax.xml.stream.XMLStreamException
import javax.xml.stream.XMLStreamReader

/** Reads the first `trk` of a GPX 1.0 or 1.1 file; elements are matched by local name, whatever the namespace. */
object GpxReader {
    private class Raw(val epochSeconds: Double, val position: GeoPoint, val altitude: Double?)

    fun read(name: String, input: InputStream): TrackRecording {
        val rawSegments = try {
            firstTrack(input)
        } catch (e: XMLStreamException) {
            throw TrackFormatException(TrackFormatReason.NOT_GPX)
        }
        if (rawSegments == null) throw TrackFormatException(TrackFormatReason.NO_TRACK)
        val start = rawSegments.firstOrNull { it.isNotEmpty() }?.first()?.epochSeconds
            ?: throw TrackFormatException(TrackFormatReason.NO_TIMES)
        val segments = rawSegments.filter { it.isNotEmpty() }.map { segment ->
            segment.map { TrackPoint(it.epochSeconds - start, it.position, it.altitude) }
        }
        return TrackRecording(name, segments)
    }

    /** The first track's segments with their timed points, or null when the file has no track point at all. */
    private fun firstTrack(input: InputStream): List<List<Raw>>? {
        val factory = XMLInputFactory.newFactory()
        factory.setProperty(XMLInputFactory.SUPPORT_DTD, false)
        factory.setProperty(XMLInputFactory.IS_SUPPORTING_EXTERNAL_ENTITIES, false)
        val reader = factory.createXMLStreamReader(input)
        try {
            val segments = mutableListOf<MutableList<Raw>>()
            var inTrack = false
            var sawPoint = false
            while (reader.hasNext()) {
                when (reader.next()) {
                    XMLStreamConstants.START_ELEMENT -> when (reader.localName) {
                        "trk" -> inTrack = true
                        "trkseg" -> if (inTrack) segments.add(mutableListOf())
                        "trkpt" -> if (inTrack) {
                            if (segments.isEmpty()) segments.add(mutableListOf())
                            sawPoint = true
                            readPoint(reader)?.let { point ->
                                val segment = segments.last()
                                // A point not later than the one before it is a repeat or a clock step back: skipped.
                                if (segment.isEmpty() || point.epochSeconds > segment.last().epochSeconds) segment.add(point)
                            }
                        }
                    }
                    XMLStreamConstants.END_ELEMENT -> if (reader.localName == "trk" && inTrack) {
                        return if (sawPoint) segments else null
                    }
                }
            }
            return if (sawPoint) segments else null
        } finally {
            reader.close()
        }
    }

    /** Reads a `trkpt` (the reader stands on its start) through its end; null when it has no time or no place. */
    private fun readPoint(reader: XMLStreamReader): Raw? {
        val latitude = reader.getAttributeValue(null, "lat")?.toDoubleOrNull()
        val longitude = reader.getAttributeValue(null, "lon")?.toDoubleOrNull()
        var altitude: Double? = null
        var time: Double? = null
        var depth = 1
        while (depth > 0 && reader.hasNext()) {
            when (reader.next()) {
                XMLStreamConstants.START_ELEMENT -> {
                    depth++
                    // Only the point's own children: an extension may carry its own time or ele.
                    if (depth == 2) when (reader.localName) {
                        "ele" -> altitude = reader.elementText.trim().toDoubleOrNull().also { depth-- }
                        "time" -> time = parseTime(reader.elementText).also { depth-- }
                    }
                }
                XMLStreamConstants.END_ELEMENT -> depth--
            }
        }
        if (latitude == null || longitude == null || time == null) return null
        return Raw(time, GeoPoint(latitude, longitude), altitude)
    }

    private fun parseTime(text: String): Double? =
        try {
            val t = OffsetDateTime.parse(text.trim())
            t.toEpochSecond() + t.nano / 1e9
        } catch (e: DateTimeParseException) {
            null
        }
}
