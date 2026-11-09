package io.github.numberonebot.chairplay.ui

import io.github.numberonebot.chairplay.platform.windows.WindowsShell
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.net.URLDecoder
import java.nio.file.Files
import java.nio.file.Path

/** Every place the screens name opens with its command: pinned on Windows' shell. */
class UiPlacesTest {
    private val dir: Path = Files.createTempDirectory("chairplay-places")
    private val setup = SetupInfo(dir.resolve("offline-mfi"), FirewallStatus.MISSING, "netsh advfirewall ...", dir.resolve("settings.properties"), 1280, 720)
    private val about = BuildInfo(Commit("4e6d50df", "2026-10-23"), Commit("143f4674", "2026-10-21"))
    private val launched = mutableListOf<List<String>>()

    private fun places() = UiPlaces(setup, { launched += it }, {}, about, { "Windows 11 24H2 (build 26300)" }, WindowsShell())

    @Test
    fun `each place opens with today's command`() {
        val project = "https://github.com/NumberOneBot/ChairPlay"
        val wf = Path.of(System.getenv("SystemRoot") ?: "C:/Windows", "System32", "wf.msc").toString()
        val expected = listOf(
            "bluetooth" to listOf("explorer.exe", "ms-settings:bluetooth"),
            "wifi" to listOf("explorer.exe", "ms-settings:network-wifi"),
            "firewall" to listOf("explorer.exe", "windowsdefender://network/"),
            "firewall-rules" to listOf("explorer.exe", wf),
            "mfi-folder" to listOf("explorer.exe", dir.resolve("offline-mfi").toString()),
            "location" to listOf("explorer.exe", "ms-settings:privacy-location"),
            "discussions" to listOf("explorer.exe", "$project/discussions"),
            "pull-requests" to listOf("explorer.exe", "$project/pulls"),
            "author" to listOf("explorer.exe", "https://github.com/NumberOneBot"),
            "source" to listOf("explorer.exe", project),
            "releases" to listOf("explorer.exe", "$project/releases"),
            "notices" to listOf("notepad.exe", dir.resolve("THIRD_PARTY_NOTICES").toString()),
            "diplay" to listOf("explorer.exe", "https://github.com/shihabal3amri/DiPlay"),
            "xcertplay" to listOf("explorer.exe", "https://github.com/shilapi/xcertplay"),
            "livi" to listOf("explorer.exe", "https://github.com/f-io/LIVI"),
            "rgi" to listOf("explorer.exe", "https://github.com/luka-dev/mib2q-carplay-rgi"),
            "logs" to listOf("explorer.exe", dir.resolve("logs").toString()),
            "screenshots" to listOf("explorer.exe", dir.resolve("screenshots").toString()),
            "buy-me-a-coffee" to listOf("explorer.exe", "https://www.buymeacoffee.com/alexstrelets"),
        )
        val ui = places()
        for ((place, command) in expected) {
            launched.clear()
            assertTrue(place, ui.open(place))
            assertEquals(place, command, launched.single())
        }

        launched.clear()
        assertTrue(ui.open("issues"))
        val (program, url) = launched.single()
        assertEquals("explorer.exe", program)
        assertTrue(url.startsWith("$project/issues/new?body="))
        assertEquals(
            "What happened, and what you expected:\n\n\n---\n${ui.details}",
            URLDecoder.decode(url.substringAfter("body="), Charsets.UTF_8),
        )
        assertFalse(ui.open("calc.exe"))
    }
}
