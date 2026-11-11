package io.github.numberonebot.chairplay.drive.track

import io.github.numberonebot.chairplay.drive.GeoPoint
import org.junit.Test
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue

class TrackPlayerTest {
    private var now = 0L
    private val recording = TrackRecording(
        "t",
        listOf(listOf(TrackPoint(0.0, GeoPoint(10.0, 20.0)), TrackPoint(10.0, GeoPoint(10.001, 20.0)))),
    )
    private val player = TrackPlayer(recording) { now }

    private fun seconds(n: Long) {
        now += n * 1_000_000_000L
    }

    @Test
    fun `a new player is stopped at zero`() {
        assertEquals(TrackPhase.STOPPED, player.phase)
        assertEquals(0.0, player.time, 0.0)
        assertFalse(player.owns)
    }

    @Test
    fun `time runs while playing and holds while paused`() {
        player.play()
        seconds(2)
        player.advance()
        assertEquals(2.0, player.time, 1e-9)
        assertTrue(player.owns)
        player.pause()
        seconds(5)
        player.advance()
        assertEquals(2.0, player.time, 1e-9)
        assertEquals(TrackPhase.PAUSED, player.phase)
        assertTrue(player.owns)
        player.play()
        seconds(1)
        player.advance()
        assertEquals(3.0, player.time, 1e-9)
    }

    @Test
    fun `stop returns to zero`() {
        player.play()
        seconds(3)
        player.advance()
        player.stop()
        assertEquals(TrackPhase.STOPPED, player.phase)
        assertEquals(0.0, player.time, 0.0)
    }

    @Test
    fun `stop when already stopped after the end resets the time`() {
        player.play()
        seconds(20)
        player.advance()
        assertEquals(10.0, player.time, 0.0)
        player.stop()
        assertEquals(0.0, player.time, 0.0)
    }

    @Test
    fun `playing to the end stops at the duration`() {
        player.play()
        seconds(11)
        player.advance()
        assertEquals(TrackPhase.STOPPED, player.phase)
        assertEquals(recording.duration, player.time, 0.0)
        assertFalse(player.owns)
    }

    @Test
    fun `play at the end starts from the beginning`() {
        player.play()
        seconds(11)
        player.advance()
        player.play()
        assertEquals(0.0, player.time, 0.0)
        assertEquals(TrackPhase.PLAYING, player.phase)
    }

    @Test
    fun `pause only works while playing`() {
        player.pause()
        assertEquals(TrackPhase.STOPPED, player.phase)
    }

    @Test
    fun `advance does nothing unless playing`() {
        seconds(5)
        player.advance()
        assertEquals(0.0, player.time, 0.0)
    }
}
