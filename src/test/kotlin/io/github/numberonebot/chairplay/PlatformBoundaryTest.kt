package io.github.numberonebot.chairplay

import java.nio.file.Files
import java.nio.file.Path
import org.junit.Assert.assertTrue
import org.junit.Test

/** Ratchet: only the listed files outside `platform/windows/` may call Windows; the list only shrinks. */
class PlatformBoundaryTest {
    private val sourceRoot: Path = findSourceRoot()

    @Test
    fun `only listed files use Windows outside platform windows`() {
        val users = kotlinFiles().map { relative(it) }.filter { !it.startsWith(WINDOWS_DIR) }
            .filter { usesWindows(sourceRoot.resolve(it)) }.toSortedSet()
        val unlisted = users - ALLOWED
        val stale = ALLOWED - users
        assertTrue("Files call Windows but are not in ALLOWED (move the call to platform/windows/): $unlisted", unlisted.isEmpty())
        assertTrue("Entries in ALLOWED no longer call Windows; remove them: $stale", stale.isEmpty())
    }

    @Test
    fun `only the selection reaches the Windows adapters`() {
        val users = kotlinFiles().map { relative(it) }
            .filter { !it.startsWith(WINDOWS_DIR) && it != SELECTION }
            .filter { path -> Files.readAllLines(sourceRoot.resolve(path)).any { reachesAdapters(it) } }.toSortedSet()
        assertTrue("Files reach the Windows adapters; only $SELECTION may: $users", users.isEmpty())
    }

    @Test
    fun `the patterns catch their samples`() {
        val samples = listOf(
            "import com.sun.jna.platform.win32.User32",
            "GLFWNativeWin32.glfwGetWin32Window(handle)",
            "AV_HWDEVICE_TYPE_D3D11VA",
            "val f = AV_PIX_FMT_D3D11",
            "val p = \"explorer.exe\"",
            "val p = \"System32\\\\services.msc\"",
            "Native.load(\"user32\", User32::class.java)",
            "System.loadLibrary(\"chairplay_audio\")",
            "\"WebView2Loader\"",
            "open(\"ms-settings:network\")",
            "run(\"netsh advfirewall show\")",
            "Advapi32Util.registryGetStringValue()",
            "WinReg.HKEY_CURRENT_USER",
        )
        samples.forEach { assertTrue("No pattern matches: $it", matches(it)) }
        assertTrue("An import of an adapter is caught", reachesAdapters("import $ADAPTERS.WindowsShell"))
        assertTrue("A fully qualified use of an adapter is caught", reachesAdapters("val s = $ADAPTERS.WindowsShell()"))
        assertTrue("A comment line is skipped", !reachesAdapters(" * see $ADAPTERS.WindowsShell"))
        assertTrue("A comment line is skipped", !matches(" * see user32.dll") && !matches("// \"x.exe\""))
    }

    private fun usesWindows(file: Path): Boolean = Files.readAllLines(file).any { matches(it) }

    private fun reachesAdapters(line: String): Boolean = !isComment(line) && line.contains(ADAPTERS)

    private fun isComment(line: String): Boolean = line.trim().let { it.startsWith("*") || it.startsWith("/*") || it.startsWith("//") }

    private fun matches(line: String): Boolean {
        if (isComment(line)) return false
        return WINDOWS_PATTERNS.any { it.containsMatchIn(line) }
    }

    private fun kotlinFiles(): List<Path> = Files.walk(sourceRoot).use { stream ->
        stream.filter { Files.isRegularFile(it) && it.toString().endsWith(".kt") }.toList()
    }

    private fun relative(file: Path): String = sourceRoot.relativize(file).toString().replace('\\', '/')

    private fun findSourceRoot(): Path {
        var dir: Path? = Path.of(System.getProperty("user.dir")).toAbsolutePath()
        while (dir != null) {
            val candidate = dir.resolve(SOURCE_PATH)
            if (Files.isDirectory(candidate)) return candidate
            dir = dir.parent
        }
        error("Cannot find $SOURCE_PATH from ${System.getProperty("user.dir")}")
    }

    companion object {
        private const val SOURCE_PATH = "src/main/kotlin"
        private const val APP_DIR = "io/github/numberonebot/chairplay/"
        private const val WINDOWS_DIR = APP_DIR + "platform/windows/"
        private const val SELECTION = APP_DIR + "platform/Platforms.kt"
        private const val ADAPTERS = "chairplay.platform.windows."

        private val WINDOWS_PATTERNS: List<Regex> = listOf(
            Regex("""^import com\.sun\.jna\.platform\.win32\."""),
            Regex("""GLFWNativeWin32"""),
            Regex("""AV_HWDEVICE_TYPE_D3D11VA|AV_PIX_FMT_D3D11\b"""),
            Regex("\"" + """[\w./\\-]+\.(dll|exe|msc)""" + "\"", RegexOption.IGNORE_CASE),
            Regex(
                """Native\.load\("(?i:user32|kernel32|ws2_32|bluetoothapis|wlanapi|dwmapi|wtsapi32|advapi32|shell32|ole32|iphlpapi)""" + "\"",
            ),
            Regex("\"(chairplay_webview|chairplay_audio|chairplay_bridge|WebView2Loader)\""),
            Regex("""ms-settings:|windowsdefender:|"netsh """),
            Regex("""Advapi32Util|WinReg\.|HKEY_"""),
        )

        /** Paths relative to `src/main/kotlin/`. */
        val ALLOWED: Set<String> = emptySet()
    }
}
