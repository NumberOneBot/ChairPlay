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
    fun `only the selection imports the Windows adapters`() {
        val importers = kotlinFiles().map { relative(it) }
            .filter { !it.startsWith(WINDOWS_DIR) && it != SELECTION }
            .filter { path -> Files.readAllLines(sourceRoot.resolve(path)).any { it.startsWith(ADAPTER_IMPORT) } }.toSortedSet()
        val offenders = importers - IMPORTERS_LEFT
        val stale = IMPORTERS_LEFT - importers
        assertTrue("Files import the Windows adapters; only $SELECTION may: $offenders", offenders.isEmpty())
        assertTrue("Entries in IMPORTERS_LEFT no longer import the Windows adapters; remove them: $stale", stale.isEmpty())
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
        assertTrue("A comment line is skipped", !matches(" * see user32.dll") && !matches("// \"x.exe\""))
    }

    private fun usesWindows(file: Path): Boolean = Files.readAllLines(file).any { matches(it) }

    private fun matches(line: String): Boolean {
        val trimmed = line.trim()
        if (trimmed.startsWith("*") || trimmed.startsWith("/*") || trimmed.startsWith("//")) return false
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
        private const val ADAPTER_IMPORT = "import io.github.numberonebot.chairplay.platform.windows."

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
        val ALLOWED: Set<String> = sortedSetOf(
            "${APP_DIR}Firewall.kt",
            "${APP_DIR}bluetooth/WindowsBluetooth.kt",
            "${APP_DIR}bridge/BridgeNative.kt",
            "${APP_DIR}input/WindowsRawApi.kt",
            "${APP_DIR}input/WindowsRawKeyboard.kt",
            "${APP_DIR}media/ClientDrag.kt",
            "${APP_DIR}media/CloseSource.kt",
            "${APP_DIR}media/D3D11Video.kt",
            "${APP_DIR}media/DefaultAudioDevices.kt",
            "${APP_DIR}media/TitleBar.kt",
            "${APP_DIR}media/VideoDecoder.kt",
            "${APP_DIR}media/VideoWindow.kt",
            "${APP_DIR}media/WindowCommands.kt",
            "${APP_DIR}media/apac/SpatialRuntime.kt",
            "${APP_DIR}media/apac/WasapiOutput.kt",
            "${APP_DIR}network/PhysicalAdapters.kt",
            "${APP_DIR}network/WlanApi.kt",
            "${APP_DIR}session/AirPlayListener.kt",
            "${APP_DIR}session/Firewall.kt",
            "${APP_DIR}session/WindowsFirewallApi.kt",
            "${APP_DIR}ui/ChairPlayUi.kt",
            "${APP_DIR}ui/WebView2.kt",
            "${APP_DIR}update/WindowsUpdateDialog.kt",
        )

        /**
         * Windows code outside `platform/windows/` that still imports its COM, elevation and file-lock helpers, until
         * the task that moves it there; the list only shrinks.
         */
        val IMPORTERS_LEFT: Set<String> = sortedSetOf(
            "${APP_DIR}media/DefaultAudioDevices.kt",
            "${APP_DIR}session/Firewall.kt",
            "${APP_DIR}session/WindowsFirewallApi.kt",
        )
    }
}
