package io.github.numberonebot.chairplay.config

import org.junit.Assert.*
import org.junit.Assume.assumeTrue
import org.junit.Test
import java.nio.file.Files
import java.nio.file.Path

class SpatialMusicConfigTest {
    @Test fun `buffered offers APAC when installed and standard withdraws it on the next connection`() {
        val library = System.getProperty("chairplay.apac.testLibrary")
        assumeTrue(library != null && System.getProperty("chairplay.wasapi.hardwareTest") == "true")
        val dir = Files.createTempDirectory("buffered-apac-config")
        Files.createDirectories(dir.resolve("apac"))
        Files.copy(Path.of(library!!), dir.resolve("apac/chairplay_apac.dll"))
        writeTestSettings(dir)
        val config = DesktopConfig(dir)
        assertTrue(config.airPlay.mainBufferedAudio)
        // The start does not load the decoder or open the device: each connection's configuration checks them.
        assertFalse(config.airPlay.spatialAudio)
        assertTrue(config.airPlayNow().spatialAudio)
        val settings = SettingsFile(dir.resolve("settings.properties"))
        for (mode in listOf("buffered", "")) {
            settings.write(mapOf("audio.music.mode" to mode))
            assertTrue(config.airPlayNow().mainBufferedAudio)
            assertTrue(config.airPlayNow().spatialAudio)
        }
        for (oldOutput in listOf("surround", "stereo", "upmix")) {
            settings.write(mapOf("audio.spatial.output" to oldOutput))
            assertTrue(config.airPlayNow().spatialAudio)
        }
        settings.write(mapOf("audio.music.mode" to "standard"))
        assertFalse(config.airPlayNow().mainBufferedAudio)
        assertFalse(config.airPlayNow().spatialAudio)
    }

    @Test fun `missing decoder keeps the buffered preference but offers buffered AAC`() {
        val config = configWith("audio.music.mode=buffered", "audio.spatial.output=stereo")
        assertTrue(config.airPlay.mainBufferedAudio)
        assertFalse(config.airPlay.spatialAudio)
        assertTrue(config.airPlayNow().mainBufferedAudio)
        assertFalse(config.airPlayNow().spatialAudio)
        assertTrue(config.airPlay.microphoneOpus)
    }

    @Test fun `old output overrides no longer affect configuration or accept UI changes`() {
        assertTrue(configWith("audio.spatial.output=upmix").airPlay.mainBufferedAudio)
        assertTrue("audio.spatial.output" in EditableSettings.validate(mapOf("audio.spatial.output" to "stereo"), emptyMap()))
    }
}
