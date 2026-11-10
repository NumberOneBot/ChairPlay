package io.github.numberonebot.chairplay

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * The installer's directives that decide what it may touch: a per-user install without admin rights into the
 * folder ChairPlay reads its data from, never closing a running ChairPlay, recognizing an earlier install, and
 * clearing only program files and deleting user data only on request.
 */
class InstallerScriptTest {
    private val sections: Map<String, List<String>> = parse(File("packaging/installer/ChairPlay.iss").readLines())

    private fun setup(key: String): String? = sections["setup"].orEmpty()
        .map { it.split('=', limit = 2) }
        .lastOrNull { it.size == 2 && it[0].trim().equals(key, ignoreCase = true) }
        ?.get(1)?.trim()

    @Test
    fun `installs per user without administrator rights into the user's programs folder`() {
        assertEquals("lowest", setup("PrivilegesRequired"))
        assertEquals("{localappdata}\\Programs\\ChairPlay", setup("DefaultDirName"))
    }

    @Test
    fun `never closes a running ChairPlay through the Restart Manager`() {
        assertEquals("no", setup("CloseApplications"))
    }

    @Test
    fun `keeps a fixed GUID as its AppId so a new installer updates the existing install`() {
        val appId = setup("AppId").orEmpty()
        assertTrue(appId, Regex("""\{\{[0-9A-Fa-f]{8}(-[0-9A-Fa-f]{4}){3}-[0-9A-Fa-f]{12}}""").matches(appId))
    }

    // Only the program folders: the user's data stays, and nothing an earlier version left is cleaned up.
    @Test
    fun `clears only the program folders before an update`() {
        val entries = sections["installdelete"].orEmpty().map { param(it, "Name") to param(it, "Type") }
        assertEquals(
            setOf(
                "{app}\\app" to "filesandordirs",
                "{app}\\runtime" to "filesandordirs",
                "{app}\\helper" to "filesandordirs",
            ),
            entries.toSet(),
        )
        assertEquals(entries.size, entries.toSet().size)
    }

    @Test
    fun `deletes nothing more than it installed on uninstall`() {
        assertFalse(sections.containsKey("uninstalldelete"))
    }

    // Uninstalling asks whether to delete the user's data too; No is the default, and the answer a silent run gets.
    @Test
    fun `asks before deleting the user's data on uninstall, and keeps it by default`() {
        val script = File("packaging/installer/ChairPlay.iss").readText()
        assertTrue(Regex("""SuppressibleMsgBox\(DeleteDataQuestion,[^;]*MB_DEFBUTTON2[^;]*IDNO\)""").containsMatchIn(script))
    }

    // What it deletes is the list the build keeps on deploy, passed in by Gradle, not a second list in the script.
    @Test
    fun `deletes on request only the data the build names`() {
        val script = File("packaging/installer/ChairPlay.iss").readText()
        assertTrue(script.contains("#ifndef AppData"))
        assertTrue(script.contains("'{#AppData}'"))
        // Installation may check individual data files; deletion must use only the build-supplied list.
        val deletion = script.substringAfter("procedure DeleteData();", "")
            .substringBefore("procedure CurUninstallStepChanged")
        assertTrue("Missing DeleteData procedure", deletion.isNotBlank())
        for (name in userData) assertFalse("DeleteData names $name", deletion.contains(name))
        val gradle = File("gradle/installer.gradle.kts").readText()
        assertTrue(Regex(""""/DAppData=\$\{[^}]*appDataFiles""").containsMatchIn(gradle))
    }

    @Test
    fun `ships no user data file`() {
        val sources = sections["files"].orEmpty().flatMap { listOf(param(it, "Source"), param(it, "DestName")) }
        assertTrue(sections["files"].orEmpty().isNotEmpty())
        for (source in sources) for (name in userData) {
            assertFalse("$source names $name", source.contains(name, ignoreCase = true))
        }
    }

    // deploy keeps, the installer refuses in an image and deletes on request exactly appDataFiles: every data name.
    @Test
    fun `the build's data list covers all the user's data`() {
        val gradle = File("gradle/package.gradle.kts").readText()
        val list = gradle.substringAfter("val appDataFiles = listOf(").substringBefore(")")
        val names = Regex(""""([^"]+)"""").findAll(list).map { it.groupValues[1].removeSuffix("/**") }.toSet()
        for (name in userData) assertTrue("appDataFiles lacks $name", name in names)
    }

    companion object {
        private val userData = listOf(
            "settings.properties", "controls.properties", "window.properties", "identity.properties", "pairings",
            "offline-mfi", "carplay-icon.png", "logs", "screenshots", "ChairPlay.lock", "webview2", "cache",
        )

        /** Lines of each `[Section]`, by its lower-case name, without blanks, `;` comments and `#` directives. */
        private fun parse(lines: List<String>): Map<String, List<String>> {
            val result = mutableMapOf<String, MutableList<String>>()
            var current: MutableList<String>? = null
            for (raw in lines) {
                val line = raw.trim()
                if (line.isEmpty() || line.startsWith(";") || line.startsWith("#")) continue
                val header = Regex("""\[(\w+)]""").matchEntire(line)
                if (header != null) {
                    current = result.getOrPut(header.groupValues[1].lowercase()) { mutableListOf() }
                } else {
                    current?.add(line)
                }
            }
            return result
        }

        /** A parameter of a section entry (`Name: "x"; Type: y`), unquoted, or "" when the entry has none. */
        private fun param(entry: String, key: String): String = entry.split(';')
            .map { it.split(':', limit = 2) }
            .firstOrNull { it.size == 2 && it[0].trim().equals(key, ignoreCase = true) }
            ?.get(1)?.trim()?.removeSurrounding("\"").orEmpty()
    }
}
