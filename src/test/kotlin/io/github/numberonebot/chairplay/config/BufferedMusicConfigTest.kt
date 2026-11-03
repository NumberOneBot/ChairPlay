package io.github.numberonebot.chairplay.config

import org.junit.Assert.*
import org.junit.Test
import java.nio.file.Files

class BufferedMusicConfigTest {
    @Test fun `new settings default to buffered music`() {
        assertTrue(configWith().airPlay.mainBufferedAudio)
    }

    @Test fun `missing playback mode on reconnect uses buffered while an explicit standard choice survives reload`() {
        val dir = Files.createTempDirectory("buffered-default")
        writeTestSettings(dir)
        val file = SettingsFile(dir.resolve("settings.properties"))
        file.write(mapOf("audio.music.mode" to "standard"))
        val config = DesktopConfig(dir)
        assertFalse(config.airPlay.mainBufferedAudio)
        assertFalse(DesktopConfig(dir).airPlay.mainBufferedAudio)
        Files.writeString(dir.resolve("settings.properties"), "wifi.ssid=Home\nwifi.passphrase=secret123\n")
        assertTrue(config.airPlayNow().mainBufferedAudio)
        assertFalse(config.airPlay.mainBufferedAudio)
    }

    @Test fun `a saved standard choice survives and buffered only changes the next connection`() {
        val dir = Files.createTempDirectory("buffered-music")
        writeTestSettings(dir)
        SettingsFile(dir.resolve("settings.properties")).write(mapOf("audio.music.mode" to "standard"))
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
        assertTrue("audio.music.mode" in EditableSettings.validate(mapOf("audio.music.mode" to "unsupported"), emptyMap()))
        assertTrue(configWith("audio.music.mode= Buffered ").airPlay.mainBufferedAudio)
        assertThrows(IllegalArgumentException::class.java) { configWith("audio.music.mode=unsupported") }
    }
}
