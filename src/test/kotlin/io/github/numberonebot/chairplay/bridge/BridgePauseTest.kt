package io.github.numberonebot.chairplay.bridge

import io.github.numberonebot.chairplay.input.Control
import io.github.numberonebot.chairplay.phone.MediaKey
import org.junit.Assert.assertEquals
import org.junit.Test

/** A game's pause (telemetry flag bit 2) as a car switched off: the iPhone's music pauses and plays again (owner, 2026-11-24). */
class BridgePauseTest {
    private var playing = true
    private val sent = mutableListOf<Control>()
    private val pause = BridgePause().also {
        it.playing = { playing }
        it.send = { control -> sent += control; playing = !playing }
    }
    private val toggle = Control.Media(MediaKey.PLAY_PAUSE)

    @Test
    fun `music playing pauses with the game and plays again as it goes on`() {
        pause.update(false)
        pause.update(true)
        pause.update(true)
        assertEquals(listOf(toggle), sent)
        pause.update(false)
        assertEquals(listOf(toggle, toggle), sent)
    }

    @Test
    fun `music not playing stays as it is`() {
        playing = false
        pause.update(true)
        pause.update(false)
        assertEquals(emptyList<Control>(), sent)
    }

    // Music the player started in the menu is theirs: going on with the game leaves it playing.
    @Test
    fun `music played again in the menu is not toggled as the game goes on`() {
        pause.update(true)
        playing = true
        pause.update(false)
        assertEquals(listOf(toggle), sent)
    }

    // A game first found in its menu is a car switched off already.
    @Test
    fun `a game first found paused pauses the music`() {
        pause.update(true)
        assertEquals(listOf(toggle), sent)
    }

    // A game quiet or gone says nothing of its pause: the music stays as it is.
    @Test
    fun `no record changes nothing`() {
        pause.update(true)
        pause.update(null)
        pause.update(null)
        assertEquals(listOf(toggle), sent)
        pause.update(false)
        assertEquals(listOf(toggle, toggle), sent)
    }
}
