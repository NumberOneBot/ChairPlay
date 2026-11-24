package io.github.numberonebot.chairplay.carplay

import org.junit.Assert.assertEquals
import org.junit.Test

/** Siri and calls told apart from the other streams by their audio type, sound and microphone alike. */
class VoiceStreamsTest {
    private val heard = mutableListOf<Boolean>()
    private val voice = VoiceStreams { heard += it }

    @Test
    fun `a call or Siri turns the voice on until its last stream stops`() {
        voice.started("call sound", "telephony")
        voice.started("call microphone", "telephony")
        voice.stopped("call sound")
        assertEquals(listOf(true), heard)
        voice.stopped("call microphone")
        assertEquals(listOf(true, false), heard)
        // Siri listening has a microphone stream alone; the engine's type names come in either case.
        voice.started("siri microphone", "speechRecognition")
        voice.stopped("siri microphone")
        assertEquals(listOf(true, false, true, false), heard)
    }

    @Test
    fun `music, prompts and the rest are no voice`() {
        voice.started("music", "media")
        voice.started("prompt", "alert")
        voice.started("other", "default")
        voice.stopped("music")
        voice.stopped("nothing")
        assertEquals(emptyList<Boolean>(), heard)
    }

    // A stream started again under another type is that type now.
    @Test
    fun `a stream started again as music is no voice any more`() {
        voice.started("stream", "telephony")
        voice.started("stream", "media")
        assertEquals(listOf(true, false), heard)
    }
}
