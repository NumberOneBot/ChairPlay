package io.github.numberonebot.chairplay.drive.track

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.fail
import org.junit.Test

class GpxReaderTest {
    private fun pt(lat: Double, lon: Double, time: String?, ele: Double? = null): String =
        "<trkpt lat=\"$lat\" lon=\"$lon\">" +
            (ele?.let { "<ele>$it</ele>" } ?: "") +
            (time?.let { "<time>$it</time>" } ?: "") + "</trkpt>"

    private fun gpx(body: String, root: String = "<gpx version=\"1.1\" xmlns=\"http://www.topografix.com/GPX/1/1\">"): String =
        "<?xml version=\"1.0\"?>$root$body</gpx>"

    private fun read(text: String): TrackRecording = GpxReader.read("t", text.byteInputStream())

    private fun reasonOf(text: String): TrackFormatReason? =
        try {
            read(text)
            fail("expected TrackFormatException")
            null
        } catch (e: TrackFormatException) {
            e.reason
        }

    private val seg3 = pt(50.0, 6.0, "2024-08-10T14:00:00Z") + pt(50.0, 6.001, "2024-08-10T14:00:01Z") +
        pt(50.0, 6.002, "2024-08-10T14:00:02Z")

    @Test
    fun `reads the first track only`() {
        val other = pt(10.0, 10.0, "2024-08-10T15:00:00Z") + pt(10.0, 10.001, "2024-08-10T15:00:01Z")
        val r = read(gpx("<trk><trkseg>$seg3</trkseg></trk><trk><trkseg>$other</trkseg></trk>"))
        assertEquals(1, r.segments.size)
        assertEquals(3, r.segments[0].size)
        assertEquals(50.0, r.segments[0][0].position.latitude, 1e-9)
    }

    @Test
    fun `each trkseg is a segment`() {
        val second = pt(50.1, 6.0, "2024-08-10T14:00:10Z") + pt(50.1, 6.001, "2024-08-10T14:00:11Z") +
            pt(50.1, 6.002, "2024-08-10T14:00:12Z")
        val r = read(gpx("<trk><name>x</name><trkseg>$seg3</trkseg><trkseg>$second</trkseg></trk>"))
        assertEquals(2, r.segments.size)
        assertEquals(12.0, r.duration, 1e-6)
        assertEquals(10.0, r.segments[1][0].seconds, 1e-6)
    }

    @Test
    fun `accepts RaceBox version 1_0 with the 1_1 namespace`() {
        val root = "<gpx version=\"1.0\" xmlns=\"http://www.topografix.com/GPX/1/1\" creator=\"RaceBox\">"
        val body = "<wpt lat=\"50.0\" lon=\"6.0\"><name>start</name></wpt>" +
            "<trk><trkseg>" + pt(50.0, 6.0, "2024-08-10T14:00:00Z").replace("</trkpt>", "<extensions><speed>3</speed></extensions></trkpt>") +
            pt(50.0, 6.001, "2024-08-10T14:00:01Z") + "</trkseg></trk>"
        val r = read(gpx(body, root))
        assertEquals(2, r.segments[0].size)
    }

    @Test
    fun `times with offset and fractions`() {
        val body = "<trk><trkseg>" + pt(50.0, 6.0, "2024-08-10T14:39:26.040+00:00") +
            pt(50.0, 6.001, "2024-08-10T14:39:26.08Z") + "</trkseg></trk>"
        val r = read(gpx(body))
        assertEquals(0.0, r.segments[0][0].seconds, 1e-6)
        assertEquals(0.04, r.segments[0][1].seconds, 1e-6)
    }

    @Test
    fun `ele becomes altitude`() {
        val body = "<trk><trkseg>" + pt(50.0, 6.0, "2024-08-10T14:00:00Z", 123.5) +
            pt(50.0, 6.001, "2024-08-10T14:00:01Z") + "</trkseg></trk>"
        val s = read(gpx(body)).segments[0]
        assertEquals(123.5, s[0].altitude!!, 1e-9)
        assertNull(s[1].altitude)
    }

    @Test
    fun `skips a point not later than the previous one`() {
        val body = "<trk><trkseg>" + pt(50.0, 6.0, "2024-08-10T14:00:00Z") +
            pt(50.0, 6.001, "2024-08-10T14:00:00Z") + pt(50.0, 6.002, "2024-08-10T13:59:59Z") +
            pt(50.0, 6.003, "2024-08-10T14:00:01Z") + "</trkseg></trk>"
        val s = read(gpx(body)).segments[0]
        assertEquals(2, s.size)
        assertEquals(6.003, s[1].position.longitude, 1e-9)
    }

    @Test
    fun `distance sums inside segments only`() {
        val far = pt(51.0, 7.0, "2024-08-10T14:00:10Z") + pt(51.0, 7.001, "2024-08-10T14:00:11Z")
        val one = pt(50.0, 6.0, "2024-08-10T14:00:00Z") + pt(50.0, 6.001, "2024-08-10T14:00:01Z")
        val r = read(gpx("<trk><trkseg>$one</trkseg><trkseg>$far</trkseg></trk>"))
        val expected = distanceMeters(r.segments[0][0].position, r.segments[0][1].position) +
            distanceMeters(r.segments[1][0].position, r.segments[1][1].position)
        assertEquals(expected, r.distanceMeters, 1e-6)
    }

    @Test
    fun `broken xml is not readable`() {
        assertEquals(TrackFormatReason.NOT_GPX, reasonOf("<gpx><trk>"))
    }

    @Test
    fun `no track`() {
        assertEquals(TrackFormatReason.NO_TRACK, reasonOf(gpx("<wpt lat=\"1\" lon=\"2\"/>")))
    }

    @Test
    fun `points without times`() {
        val body = "<trk><trkseg>" + pt(50.0, 6.0, null) + pt(50.0, 6.001, null) + "</trkseg></trk>"
        assertEquals(TrackFormatReason.NO_TIMES, reasonOf(gpx(body)))
    }

    @Test
    fun `a single point is too short`() {
        val body = "<trk><trkseg>" + pt(50.0, 6.0, "2024-08-10T14:00:00Z") + "</trkseg></trk>"
        assertEquals(TrackFormatReason.TOO_SHORT, reasonOf(gpx(body)))
    }

    // The page words each reason by its key (webui/src/gps/trackApi.ts): the keys are the protocol.
    @Test
    fun `each reason travels as its code`() {
        assertEquals(listOf("not-gpx", "no-track", "no-times", "too-short"), TrackFormatReason.entries.map { it.key })
    }
}
