package io.github.numberonebot.chairplay.config

import com.shilapi.xcertplay.airplay.AirPlayInfoPlist
import io.github.numberonebot.chairplay.log.LogLevel
import io.github.numberonebot.chairplay.media.DuckSetting
import io.github.numberonebot.chairplay.phone.AltscreenContent
import io.github.numberonebot.chairplay.ui.BuildInfo
import io.github.numberonebot.chairplay.media.decibelGain
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test
import java.nio.file.Files
import java.nio.file.Path

class DesktopConfigTest {
    // H.264 alone unless H.265 is asked for: a new user's iPhone sends H.264.
    @Test
    fun `desktop advertises HEVC to the phone only when asked`() {
        val config = configWith()
        assertFalse(AirPlayInfoPlist.build(config.airPlay).containsKey("hevcInfo"))
        assertTrue(AirPlayInfoPlist.build(configWith("video.codecs=h264+h265").airPlay).containsKey("hevcInfo"))
        assertTrue(config.hardwareVideo)
        assertFalse(configWith("video.decoder=software").hardwareVideo)
    }

    @Test
    fun `codec choice controls the HEVC capability on reconnect`() {
        val dir = Files.createTempDirectory("chairplay-config")
        writeTestSettings(dir)
        val file = SettingsFile(dir.resolve("settings.properties"))
        file.write(mapOf("video.codecs" to "h264"))
        val config = DesktopConfig(dir)
        assertFalse(AirPlayInfoPlist.build(config.airPlay).containsKey("hevcInfo"))
        file.write(mapOf("video.codecs" to "h264+h265"))
        assertTrue(config.now().airPlay.hevc)
    }

    @Test
    fun `video modes are validated and apply on reconnect`() {
        for ((key, values) in mapOf("video.codecs" to listOf("h264", "h264+h265"),
            "video.decoder" to listOf("software", "hardware"))) {
            values.forEach { assertTrue(EditableSettings.validate(mapOf(key to it), emptyMap()).isEmpty()) }
            assertTrue(EditableSettings.validate(mapOf(key to "unsupported"), emptyMap()).containsKey(key))
            assertEquals(Applies.RECONNECT, EditableSettings.applies(key))
        }
    }

    @Test
    fun `identity persists across loads`() {
        val dir = Files.createTempDirectory("chairplay-config")
        writeTestSettings(dir)

        val first = DesktopConfig(dir).identity
        val second = DesktopConfig(dir).identity

        assertEquals(first.pairingId, second.pairingId)
        assertEquals(first.publicKeyHex, second.publicKeyHex)
    }

    // One accent colour for the screens, the controls window, and the HUD; everything lighter or fainter is made from it.
    @Test
    fun `the accent colour is the maneuvers' blue unless set`() {
        assertEquals(0x52C4FF, configWith().accent)
        assertEquals(0x30D158, configWith("ui.accent=#30d158").accent)
        assertEquals("#30d158", configWith("ui.accent=#30d158").accentText)
        assertEquals("#52c4ff", configWith().accentText)
        for (bad in listOf("ui.accent=blue", "ui.accent=#0a84f", "ui.accent=0a84ff")) {
            val error = assertThrows(IllegalArgumentException::class.java) { configWith(bad).accent }
            assertTrue(error.message, "ui.accent" in error.message.orEmpty())
        }
    }

    // A file written by an older version gains the settings it lacks, after its own lines, which stay as they were.
    @Test
    fun `an older settings file gains the missing settings and keeps its own`() {
        val dir = Files.createTempDirectory("chairplay-config")
        val old = "wifi.ssid=Home\nwifi.passphrase=secret123\ndisplay.width=1920\n"
        Files.writeString(dir.resolve("settings.properties"), old)

        val config = DesktopConfig(dir)

        val text = Files.readString(dir.resolve("settings.properties"))
        assertTrue(text, text.startsWith(old))
        for (key in DesktopConfig.SETTING_KEYS) assertTrue("$key missing", Regex("(?m)^${Regex.escape(key)}=").containsMatchIn(text))
        assertEquals(1, Regex("(?m)^display\\.width=").findAll(text).count())
        assertEquals(1920, config.airPlay.main.widthPixels)
        DesktopConfig(dir)
        assertEquals(text, Files.readString(dir.resolve("settings.properties")))
    }

    // The names are matched against the devices at start, where they exist; the config keeps them as written.
    @Test
    fun `sound devices are read as written`() {
        assertEquals("default", configWith().audioOutput)
        val config = configWith("audio.output=ULTRAGEAR", "audio.input=default")
        assertEquals("ULTRAGEAR", config.audioOutput)
        assertEquals("default", config.audioInput)
    }

    // As before the start screen: the app connects at start and CarPlay covers the screen once the iPhone is there.
    @Test
    fun `the app connects and shows CarPlay on its own unless told not to`() {
        assertTrue(configWith().autoconnect)
        assertTrue(configWith().autoshow)
        val config = configWith("ui.autoconnect=false", "ui.autoshow=false")
        assertFalse(config.autoconnect)
        assertFalse(config.autoshow)
    }

    // The start screen asks for the Wi-Fi, so a missing network no longer stops the start; each bring-up reads it anew.
    @Test
    fun `a missing Wi-Fi network leaves the start to the start screen`() {
        val dir = Files.createTempDirectory("chairplay-config")
        Files.writeString(dir.resolve("settings.properties"), "wifi.ssid=\n")

        val config = DesktopConfig(dir)

        assertNull(config.wifi)
        assertNull(config.now().wifi)
        SettingsFile(dir.resolve("settings.properties")).write(mapOf("wifi.ssid" to "Home", "wifi.passphrase" to "secret123"))
        assertEquals("Home", config.now().wifi?.ssid)
        assertEquals("secret123", config.now().wifi?.passphrase)
    }

    // What the iPhone learns of its screens it asks at each connection, so a changed one applies at the next; the name
    // stays as the app started with it, as the radio keeps it.
    @Test
    fun `the next connection takes the screens from the file as it is now`() {
        val dir = Files.createTempDirectory("chairplay-config")
        Files.writeString(dir.resolve("settings.properties"), "display.width=800\ndisplay.height=480\n")
        val config = DesktopConfig(dir)

        SettingsFile(dir.resolve("settings.properties")).write(
            mapOf(
                "display.width" to "1280", "display.height" to "720", "display.fps" to "30", "display.safe.top" to "10",
                "display.right.hand.drive" to "true", "device.name" to "Other", "altscreen.enabled" to "true",
            ),
        )
        val next = config.airPlayNow()

        assertEquals(1280 to 720, next.main.widthPixels to next.main.heightPixels)
        assertEquals(30, next.main.fps)
        assertEquals(10, next.main.safeArea?.top)
        assertTrue(next.rightHandDrive)
        assertEquals(DesktopConfig.DEFAULT_DEVICE_NAME, next.deviceName)
        assertEquals(1440 to 540, next.cluster?.widthPixels to next.cluster?.heightPixels)
        assertEquals(WindowSize(1440, 540), config.now().altscreen?.size)
        assertEquals(800, config.displayWidth)
        assertNull(config.altscreen)
    }

    // Upstream ships its call echo canceller off, as an experiment; so does the desktop.
    @Test
    fun `call echo cancellation is off unless asked for`() {
        assertFalse(configWith().echoCancellation)
        assertTrue(configWith("audio.echo.cancellation=true").echoCancellation)
    }

    // The music starts silent for a while after it starts, then rises: the iPhone's first audio comes in bursts.
    @Test
    fun `the music starts silent for 2 s and rises over 800 ms unless set`() {
        assertEquals(2000L to 800L, configWith().let { it.musicStartSilenceMillis to it.musicStartFadeMillis })
        val set = configWith("audio.start.silence.ms=0", "audio.start.fade.ms=300")
        assertEquals(0L to 300L, set.musicStartSilenceMillis to set.musicStartFadeMillis)
        assertTrue(runCatching { configWith("audio.start.fade.ms=-1").musicStartFadeMillis }.exceptionOrNull() is IllegalArgumentException)
    }

    // Under a navigation prompt the music plays at the iPhone's level, or at -14 dB, Apple's own, when it sends none.
    @Test
    fun `the music ducks to the iPhone's level, else to -14 dB, unless set`() {
        assertEquals(DuckSetting(decibelGain(-14.0), iPhoneFirst = true), configWith().duckSetting)
        assertEquals(DuckSetting(0.1f, iPhoneFirst = false), configWith("audio.duck.db=-20", "audio.duck.iphone=false").duckSetting)
        assertTrue(DesktopConfig.defaultSettingsText().contains("\naudio.duck.db=-14\n"))
        for (wrong in listOf("-31", "1", "half")) {
            assertTrue(wrong, runCatching { configWith("audio.duck.db=$wrong").duckSetting }.exceptionOrNull() is IllegalArgumentException)
        }
    }

    @Test
    fun `the controls window opens only when asked for`() {
        assertFalse(configWith().controlsWindow)
        assertTrue(configWith("window.controls=true").controlsWindow)
    }

    // DiPlay's cluster display, at the size asked for, with even margins instead of its car cluster's crop.
    @Test
    fun `an enabled altscreen asks the iPhone for its map at the given size`() {
        val config = configWith("altscreen.enabled=true", "altscreen.width=1600", "altscreen.height=600", "altscreen.cards=auto")

        val cluster = config.airPlay.cluster!!
        assertEquals(1600, cluster.widthPixels)
        assertEquals(600, cluster.heightPixels)
        assertEquals(30, cluster.fps)
        assertEquals("maps:/car/instrumentcluster", cluster.initialUrl)
        assertEquals(AltscreenContent.MAP_CARD, config.altscreen!!.content)
        assertEquals(WindowSize(1600, 600), config.altscreen!!.size)
    }

    // The CarPlay data log is for diagnostics and grows during every drive: off unless asked for.
    @Test
    fun `the CarPlay data log is off unless asked for`() {
        assertFalse(configWith().logCarPlayData)
        assertTrue(configWith("log.carplay.data=true").logCarPlayData)
        assertTrue(DesktopConfig.defaultSettingsText().contains("\nlog.carplay.data=false\n"))
        assertTrue(runCatching { configWith("log.carplay.data=yes").logCarPlayData }.exceptionOrNull() is IllegalArgumentException)
    }

    // The app's log tells what happens without DiPlay's packet-by-packet lines unless asked for.
    @Test
    fun `the log level defaults to the build's, a set level is read, and an unknown one reads as the default`() {
        // A new settings file carries the level of its build: warning in a release, info in a development build.
        val default = defaultLogLevel(BuildInfo.load().version)
        assertEquals(default, configWith().logLevel.key)
        assertEquals(LogLevel.TRACE, configWith("log.level=Trace").logLevel)
        assertTrue(DesktopConfig.defaultSettingsText().contains("\nlog.level=$default\n"))
        assertEquals(LogLevel.INFO, configWith("log.level=loud").logLevel)
    }

    // The name the iPhone shows for the car, in CarPlay settings, /info and the Bonjour service; the window
    // title stays ChairPlay.
    @Test
    fun `the device name comes from the settings`() {
        assertEquals("ChairPlay", configWith().airPlay.deviceName)
        assertEquals("ChairPlay", configWith("device.name=  ").airPlay.deviceName)
        assertEquals("Гараж CarPlay", configWith("device.name=Гараж CarPlay").airPlay.deviceName)
    }

    // Bonjour instance names hold at most 63 bytes of UTF-8.
    @Test
    fun `rejects a device name Bonjour cannot carry`() {
        val error = runCatching { configWith("device.name=" + "Я".repeat(32)).airPlay }.exceptionOrNull()
        assertTrue(error is IllegalArgumentException && "device.name" in error.message!!)
    }

    // The packaged exe keeps its settings, keys and pairings in its own folder.
    @Test
    fun `the app folder is the packaged exe's folder`() {
        val exe = Path.of("C:", "Users", "dark", "Documents", "ChairPlayApp", "ChairPlay.exe")

        assertEquals(exe.parent, DesktopConfig.appDirectory(exe.toString()))
    }

    // Started another way (installDist, an IDE), the app keeps its files in the working directory.
    @Test
    fun `without the packaged exe the app folder is the working directory`() {
        assertEquals(Path.of(System.getProperty("user.dir")).toAbsolutePath(), DesktopConfig.appDirectory(null))
    }

    // Without Opus among the microphone formats the wireless iPhone opened no audio stream at all and
    // played music on its own speaker (iPhone 15, iOS 18.6); upstream: "PCM-only wireless negotiation is
    // not a universal fallback".
    @Test
    fun `offers opus for the microphone`() {
        val dir = Files.createTempDirectory("chairplay-config")
        writeTestSettings(dir)

        val formats = AirPlayInfoPlist.build(DesktopConfig(dir).airPlay)["audioFormats"] as List<*>
        val default = formats.map { it as Map<*, *> }.single { it["type"] == 100 && it["audioType"] == "default" }

        assertTrue((default["audioInputFormats"] as Int) and 0x70000000 != 0)
    }
}
