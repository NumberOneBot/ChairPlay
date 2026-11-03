package io.github.numberonebot.chairplay.ui

import io.github.numberonebot.chairplay.drive.GeoPoint
import io.github.numberonebot.chairplay.drive.WindowsLocation
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.nio.file.Files

/** UiController: the pages and folders the screens open, and the firewall rule. */
class UiControllerPlacesTest : UiControllerFixture() {
    // The GPS page's Windows location: the place Windows found, or why there is none.
    @Test
    fun `Windows' location comes as a place or a reason`() {
        assertEquals(
            mapOf("latitude" to 48.85, "longitude" to 2.29),
            controller(locate = { WindowsLocation.Found(GeoPoint(48.85, 2.29)) }).windowsLocation(),
        )
        assertEquals(mapOf("error" to "denied"), controller(locate = { WindowsLocation.Denied }).windowsLocation())
        assertEquals(mapOf("error" to "unknown"), controller(locate = { WindowsLocation.Unknown }).windowsLocation())
    }

    // Copy details and New issue carry the same text: the build, the DiPlay core, and Windows; never a setting.
    @Test
    fun `the details for a report name the build, the core, and Windows`() {
        val details = (controller().state()["about"] as Map<*, *>)["details"] as String

        for (part in listOf("4e6d50df", "143f4674", "Windows 11 24H2 (build 26300)")) assertTrue(details, part in details)
    }

    @Test
    fun `the details for a report name the release version when there is one`() {
        val release = BuildInfo(Commit("4e6d50df", "2026-10-25"), Commit("143f4674", "2026-10-21"), "0.1.0")
        val details = (controller(about = release).state()["about"] as Map<*, *>)["details"] as String

        assertTrue(details, "0.1.0" in details)
    }

    @Test
    fun `New issue opens GitHub with the details filled in`() {
        val ui = controller()
        assertTrue(ui.open("issues"))

        val (program, url) = launched.single()
        assertEquals("explorer.exe", program)
        assertTrue(url, url.startsWith("https://github.com/NumberOneBot/ChairPlay/issues/new?body="))
        val body = java.net.URLDecoder.decode(url.substringAfter("body="), Charsets.UTF_8)
        assertTrue(body, body.endsWith((ui.state()["about"] as Map<*, *>)["details"] as String))
    }

    @Test
    fun `the About page's links open the project's pages, and the notices in Notepad`() {
        val ui = controller()
        val pages = mapOf(
            "author" to "https://github.com/NumberOneBot",
            "source" to "https://github.com/NumberOneBot/ChairPlay",
            "releases" to "https://github.com/NumberOneBot/ChairPlay/releases",
            "discussions" to "https://github.com/NumberOneBot/ChairPlay/discussions",
            "pull-requests" to "https://github.com/NumberOneBot/ChairPlay/pulls",
            "diplay" to "https://github.com/shihabal3amri/DiPlay",
            "xcertplay" to "https://github.com/shilapi/xcertplay",
            "livi" to "https://github.com/f-io/LIVI",
            "rgi" to "https://github.com/luka-dev/mib2q-carplay-rgi",
        )
        for ((place, url) in pages) {
            launched.clear()
            assertTrue(place, ui.open(place))
            assertEquals(listOf("explorer.exe", url), launched.single())
        }

        launched.clear()
        assertTrue(ui.open("notices"))
        assertEquals(listOf("notepad.exe", dir.resolve("THIRD_PARTY_NOTICES").toString()), launched.single())
    }

    // Add the rule: the netsh commands the page shows, run once with the administrator's consent; then read again.
    @Test
    fun `Add the rule runs the firewall commands elevated and reads the firewall again`() {
        var firewall = FirewallStatus.MISSING
        val checks = SetupChecks({ null }, { null }, { firewall }, FirewallStatus.MISSING, { true })
        val ui = controller(checks = checks)
        firewall = FirewallStatus.PRESENT
        val before = ui.version

        assertTrue(ui.addFirewallRule())

        assertEquals(listOf("netsh advfirewall ..."), elevated)
        assertEquals("present", (ui.state()["setup"] as Map<*, *>)["firewall"])
        assertTrue(ui.version > before)
    }

    @Test
    fun `Add the rule says when the consent was not given`() {
        consent = false

        assertFalse(controller().addFirewallRule())
    }

    // The screens' links open Windows' own pages; the page names a place the app knows, never a command of its own.
    @Test
    fun `a link opens the Windows page it names, and nothing else`() {
        val ui = controller()

        assertTrue(ui.open("bluetooth"))
        assertTrue(ui.open("wifi"))
        assertTrue(ui.open("firewall"))
        assertTrue(ui.open("location"))
        assertFalse(ui.open("calc.exe"))
        assertFalse(ui.open("ms-settings:bluetooth"))

        assertEquals(
            listOf(
                listOf("explorer.exe", "ms-settings:bluetooth"),
                listOf("explorer.exe", "ms-settings:network-wifi"),
                listOf("explorer.exe", "windowsdefender://network/"),
                listOf("explorer.exe", "ms-settings:privacy-location"),
            ),
            launched,
        )
    }

    // Its rules are in the firewall's own console; the shell asks for the administrator's consent it needs.
    @Test
    fun `the firewall's rules open in its console through the shell`() {
        val ui = controller()

        assertTrue(ui.open("firewall-rules"))

        assertEquals("explorer.exe", launched.single()[0])
        assertTrue(launched.single()[1], launched.single()[1].endsWith("wf.msc"))
    }

    @Test
    fun `the MFi folder opens in File Explorer, made first if it is missing`() {
        val ui = controller()

        assertTrue(ui.open("mfi-folder"))

        assertTrue(Files.isDirectory(dir.resolve("offline-mfi")))
        assertEquals(listOf(listOf("explorer.exe", dir.resolve("offline-mfi").toString())), launched)
    }

    // Under the Experiments page's folder buttons: what each folder holds, its subfolders' files too.
    @Test
    fun `the logs and screenshots folders' sizes add up their files, a missing folder 0 and left unmade`() {
        Files.createDirectories(dir.resolve("logs").resolve("old"))
        Files.write(dir.resolve("logs").resolve("chairplay.log"), ByteArray(1500))
        Files.write(dir.resolve("logs").resolve("old").resolve("carplay-data.jsonl"), ByteArray(24))

        assertEquals(mapOf("logs" to 1524L, "screenshots" to 0L), controller().folderSizes())
        assertFalse(Files.exists(dir.resolve("screenshots")))
    }

    // The Experiments page's Logs: the CarPlay data log's folder and F12's screenshots, in the app folder.
    @Test
    fun `the logs and screenshots folders open in File Explorer, made first if they are missing`() {
        val ui = controller()

        assertTrue(ui.open("logs"))
        assertTrue(ui.open("screenshots"))

        assertTrue(Files.isDirectory(dir.resolve("logs")) && Files.isDirectory(dir.resolve("screenshots")))
        assertEquals(
            listOf(listOf("explorer.exe", dir.resolve("logs").toString()), listOf("explorer.exe", dir.resolve("screenshots").toString())),
            launched,
        )
    }

    @Test
    fun `a page Windows does not open is logged`() {
        val ui = controller(launch = { throw java.io.IOException("explorer.exe not found") })

        assertFalse(ui.open("bluetooth"))

        assertTrue(logged.joinToString(), logged.any { "bluetooth" in it && "explorer.exe not found" in it })
    }
}
