package io.github.numberonebot.chairplay.drive

import io.github.numberonebot.chairplay.bridge.TelemetryInput
import io.github.numberonebot.chairplay.bridge.TelemetryRecord
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * gps.external on with no game: the status tick looks for one, and only a game found wakes the window loop, which
 * follows it until it is gone (DrivingExternalTest covers the following).
 */
class DrivingGameLookoutTest {
    private val paris = GeoPoint(48.85, 2.29)
    private var now = 0L
    private val source = FakeTelemetrySource()
    private var woken = 0
    private val driving = Driving(
        GpsSettings(true, start = GeoPoint(50.4501, 30.5234), external = true),
        gamepad = { null },
        nanoTime = { now },
        lookup = { it.run() },
        telemetry = { TelemetryInput(source, {}, { now }) },
    ).also { it.wake = { woken++ } }

    private fun record(counter: Int) = TelemetryRecord(
        counter = counter, paused = false, gear = 3, speed = 20.0, headingDegrees = 135.0,
        latitude = paris.latitude, longitude = paris.longitude, rpm = 4200.0, source = "AC",
    )

    private fun at(seconds: Double) { now = (seconds * 1e9).toLong() }

    // With no game, the telemetry is looked at on the status tick only: an idle window loop waits for an event.
    @Test
    fun `with no game the window loop is not woken for telemetry`() {
        assertNull(driving.pass())
        assertEquals(0, source.reads)
        driving.lookForGame()
        assertEquals(1, source.reads)
        assertNull(driving.pass())
        assertEquals(1, source.reads)
        assertEquals(0, woken)
    }

    // A game's news, seen on the status tick, wakes the loop, which follows it from its next pass; the tick looks no more.
    @Test
    fun `a game found on the status tick is followed on the window loop`() {
        source.record = record(0)
        driving.lookForGame()
        assertEquals(0, woken)

        at(0.25)
        source.record = record(1)
        driving.lookForGame()
        assertEquals(1, woken)
        assertEquals(DriveControl.FRAME_SECONDS, driving.pass()!!, 0.0)
        assertEquals(paris, driving.drive.snapshot().position)
        val reads = source.reads
        driving.lookForGame()
        assertEquals(reads, source.reads)
    }

    // Gone for five seconds, the game is looked for on the status tick again, and the loop waits for events.
    @Test
    fun `a game gone leaves the window loop idle`() {
        source.record = record(0)
        driving.lookForGame()
        source.record = record(1)
        driving.lookForGame()
        driving.pass()
        at(0.6)
        assertEquals(DriveControl.GAMEPAD_POLL_SECONDS, driving.pass()!!, 0.0)

        at(5.0)
        assertNull(driving.pass())
        val reads = source.reads
        assertNull(driving.pass())
        assertEquals(reads, source.reads)
        woken = 0
        driving.lookForGame()
        assertEquals(reads + 1, source.reads)
        assertEquals(0, woken)
    }
}
