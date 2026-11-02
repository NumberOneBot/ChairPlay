package io.github.numberonebot.chairplay.config

import org.junit.Assert.*
import org.junit.Test
import java.nio.file.Files

class BufferedMusicConfigTest {
    @Test fun `standard remains the default and buffered only changes the next connection`() {
        val dir = Files.createTempDirectory("buffered-music")
        writeTestSettings(dir)
        val config = DesktopConfig(dir)
        assertFalse(config.airPlay.mainBufferedAudio)
        SettingsFile(dir.resolve("settings.properties")).write(mapOf("audio.music.mode" to "buffered"))
        assertFalse(config.airPlay.mainBufferedAudio)
        assertTrue(config.airPlayNow().mainBufferedAudio)
        assertTrue(config.airPlayNow().microphoneOpus)
        SettingsFile(dir.resolve("settings.properties")).write(mapOf("audio.music.mode" to "standard"))
        assertFalse(config.airPlayNow().mainBufferedAudio)
    }

    @Test fun `buffered is validated and applies at reconnect`() {
        assertEquals(Applies.RECONNECT, EditableSettings.applies("audio.music.mode"))
        assertEquals("buffered", EditableSettings.normalized("audio.music.mode", " Buffered "))
        assertTrue(EditableSettings.validate(mapOf("audio.music.mode" to "buffered"), emptyMap()).isEmpty())
        assertTrue("audio.music.mode" in EditableSettings.validate(mapOf("audio.music.mode" to "spatial"), emptyMap()))
        assertTrue(configWith("audio.music.mode= Buffered ").airPlay.mainBufferedAudio)
        assertThrows(IllegalArgumentException::class.java) { configWith("audio.music.mode=spatial") }
    }
}
