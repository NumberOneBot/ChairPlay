package io.github.numberonebot.chairplay.drive

import io.github.numberonebot.chairplay.drive.track.TrackPhase
import io.github.numberonebot.chairplay.drive.track.TrackPoint
import io.github.numberonebot.chairplay.drive.track.TrackRecording
import io.github.numberonebot.chairplay.platform.LocationResult
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotSame
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.Locale

/** A loaded track as the GPS car's source: it drives the car while it plays or pauses, and hands it back where it is. */
class DrivingTrackTest {
    private var now = 0L
    private val kyiv = GeoPoint(50.4501, 30.5234)
    private val driving = Driving(GpsSettings(true, start = kyiv), gamepad = { null }, nanoTime = { now },
        locate = { LocationResult.Unknown }, lookup = { it.run() })
    private val saved = mutableListOf<Map<String, String>>()
    // Ten seconds north at about 11 m/s.
    private val recording = TrackRecording("spa.gpx", listOf(listOf(
        TrackPoint(0.0, GeoPoint(50.0, 6.0)), TrackPoint(10.0, GeoPoint(50.001, 6.0)),
    )))

    init {
        driving.savePlace = { saved += it }
    }

    private fun drive(seconds: Double) {
        repeat((seconds * 60).toInt()) {
            now += 16_666_667
            driving.pass()
        }
    }

    private fun position() = driving.drive.snapshot().position

    private fun assertAt(expected: GeoPoint, actual: GeoPoint) {
        assertEquals(expected.latitude, actual.latitude, 1e-7)
        assertEquals(expected.longitude, actual.longitude, 1e-7)
    }

    private fun place(at: GeoPoint) = String.format(Locale.ROOT, "%.5f, %.5f", at.latitude, at.longitude)

    private fun playing(): Unit = driving.run {
        load(recording)
        track!!.play()
    }

    @Test
    fun `a playing track puts the car where the recording is, and the keys drive nothing`() {
        driving.control.scripted = CarInput(1.0, 0.0, 0.5)
        playing()

        drive(1.0)

        val sample = recording.at(driving.track!!.time)
        assertEquals(1.0, driving.track!!.time, 1e-3)
        assertAt(sample.position, position())
        assertEquals(sample.speed, driving.drive.snapshot().car.speed, 1e-9)
        assertTrue(driving.drive.snapshot().car.speed > 10)
    }

    // Drive mode is the keys and the gauges: a track blocks the keys only, so the page's switch works while it plays,
    // and the gauges show the recording (owner, 2026-11-11).
    @Test
    fun `the page's drive mode switch works while a track plays, its gauges showing the recording`() {
        driving.control.scripted = CarInput(1.0, 0.0, 0.5)
        playing()
        drive(1.0)

        driving.driveMode(true)
        drive(0.5)
        val sample = recording.at(driving.track!!.time)
        assertTrue(driving.control.keyboard)
        assertAt(sample.position, position())
        assertEquals(sample.speed * 3.6, driving.gauges()!!.speedKmh, 1e-6)

        driving.driveMode(false)
        drive(0.5)
        assertTrue(!driving.control.keyboard)
        assertNull(driving.gauges())
    }

    @Test
    fun `a paused track holds the car where it is, standing`() {
        playing()
        drive(1.0)
        driving.track!!.pause()
        val there = position()

        drive(2.0)

        assertAt(there, position())
        assertEquals(0.0, driving.drive.snapshot().car.speed, 0.0)
        assertEquals(TrackPhase.PAUSED, driving.track!!.phase)
    }

    @Test
    fun `a car that can no longer send pauses the track, and coming back does not play it`() {
        var live = true
        driving.live = { live }
        playing()
        drive(1.0)
        val there = position()

        live = false
        drive(1.0)
        assertEquals(TrackPhase.PAUSED, driving.track!!.phase)
        assertAt(there, position())
        assertEquals(place(there), saved.last()["gps.start"])

        live = true
        drive(1.0)
        assertEquals(TrackPhase.PAUSED, driving.track!!.phase)
        assertEquals(0.0, driving.drive.snapshot().car.speed, 0.0)
    }

    @Test
    fun `leaving USB pauses the track and hands the car back, and USB again does not play it`() {
        var usb = true
        driving.tracks.live = { usb }
        playing()
        drive(1.0)
        val there = position()

        usb = false
        drive(0.1)
        assertEquals(TrackPhase.PAUSED, driving.track!!.phase)
        assertEquals(place(there), saved.last()["gps.start"])
        driving.control.scripted = CarInput(1.0, 0.0, 0.0)
        drive(1.0)
        assertTrue(driving.drive.snapshot().car.speed > 0)

        driving.control.scripted = CarInput()
        usb = true
        drive(1.0)
        assertEquals(TrackPhase.PAUSED, driving.track!!.phase)
    }

    @Test
    fun `a GPS operation pauses the track, so its clock does not run through it`() {
        var held = false
        driving.held = { held }
        playing()
        drive(1.0)
        val time = driving.track!!.time

        held = true
        drive(5.0)

        assertEquals(TrackPhase.PAUSED, driving.track!!.phase)
        assertEquals(time, driving.track!!.time, 0.02)
    }

    @Test
    fun `a stopped track leaves the car standing where it was, saves its place, and hands it back to the keys`() {
        playing()
        drive(2.0)
        val there = position()

        driving.track!!.stop()
        drive(0.1)

        assertAt(there, position())
        assertEquals(place(there), saved.last()["gps.start"])
        assertEquals(0.0, driving.drive.snapshot().car.speed, 0.0)
        driving.control.scripted = CarInput(1.0, 0.0, 0.0)
        drive(1.0)
        assertTrue(driving.drive.snapshot().car.speed > 0)
    }

    @Test
    fun `the recording's end leaves the car at its last point, saved, and hands it back to the keys`() {
        playing()

        drive(11.0)

        assertEquals(TrackPhase.STOPPED, driving.track!!.phase)
        assertAt(GeoPoint(50.001, 6.0), position())
        assertEquals(place(GeoPoint(50.001, 6.0)), saved.last()["gps.start"])
        assertEquals(0.0, driving.drive.snapshot().car.speed, 0.0)
        driving.control.scripted = CarInput(1.0, 0.0, 0.0)
        drive(1.0)
        assertTrue(driving.drive.snapshot().car.speed > 0)
    }

    @Test
    fun `a new recording loaded while one plays leaves the car where it was, saved, and waits stopped at its start`() {
        playing()
        drive(2.0)
        val old = driving.track
        val there = position()

        driving.load(recording)
        drive(0.1)

        assertNotSame(old, driving.track)
        assertEquals(TrackPhase.STOPPED to 0.0, driving.track!!.state())
        assertAt(there, position())
        assertEquals(place(there), saved.last()["gps.start"])
    }

    @Test
    fun `loading wakes the window loop`() {
        var woke = 0
        driving.wake = { woke++ }

        driving.load(recording)

        assertEquals(1, woke)
    }

    @Test
    fun `the GPS page sees the recording loaded, its phase, time, length and distance`() {
        assertNull(driving.state()["track"])
        playing()
        drive(1.0)

        assertEquals(
            mapOf("name" to "spa.gpx", "phase" to "playing", "time" to 1.0, "duration" to 10.0, "distanceKm" to 0.11),
            driving.state()["track"],
        )
    }
}
