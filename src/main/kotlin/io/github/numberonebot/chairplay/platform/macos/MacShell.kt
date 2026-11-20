package io.github.numberonebot.chairplay.platform.macos

import io.github.numberonebot.chairplay.platform.DesktopShell
import io.github.numberonebot.chairplay.platform.SystemPage
import java.io.File
import java.nio.file.Path

internal class MacShell(
    private val showAlert: (String) -> Unit = ::defaultShowAlert,
    private val readDark: () -> Boolean? = ::defaultReadDark,
) : DesktopShell {

    override fun systemName(): String {
        val swVersOut = runCatching {
            val p = ProcessBuilder("/usr/bin/sw_vers").start()
            p.inputStream.bufferedReader().readText().also { p.waitFor() }
        }.getOrNull()

        val arch = System.getProperty("os.arch").orEmpty()
        if (swVersOut != null) {
            val parsed = parseSystemName(swVersOut, arch)
            if (parsed.isNotEmpty()) return parsed
        }
        val osName = System.getProperty("os.name") ?: "macOS"
        val osVersion = System.getProperty("os.version") ?: ""
        return "$osName $osVersion ($arch)".trim()
    }

    override fun appsDark(): Boolean? = readDark()

    override fun dataDirectory(): Path {
        val override = System.getProperty("chairplay.home") ?: System.getenv("CHAIRPLAY_HOME")
        if (!override.isNullOrBlank()) {
            return Path.of(override)
        }
        return Path.of(System.getProperty("user.home"), "Library", "Application Support", "ChairPlay")
    }

    override fun showError(text: String) {
        showAlert(text)
    }

    override fun openInBrowser(url: String) {
        val process = ProcessBuilder("/usr/bin/open", url).start()
        val exit = process.waitFor()
        check(exit == 0) { "open failed with exit code $exit" }
    }

    override fun openCommand(target: String): List<String> = listOf("/usr/bin/open", target)

    override fun textFileCommand(file: Path): List<String> = listOf("/usr/bin/open", "-t", file.toString())

    override fun systemPage(page: SystemPage): String = when (page) {
        SystemPage.BLUETOOTH -> "x-apple.systempreferences:com.apple.BluetoothSettings"
        SystemPage.WIFI -> "x-apple.systempreferences:com.apple.Network-Settings.extension"
        SystemPage.FIREWALL -> "x-apple.systempreferences:com.apple.Network-Settings.extension"
        SystemPage.FIREWALL_RULES -> "x-apple.systempreferences:com.apple.Network-Settings.extension"
        SystemPage.LOCATION -> "x-apple.systempreferences:com.apple.preference.security?Privacy_LocationServices"
        SystemPage.APPLE_DEVICES -> "x-apple.systempreferences:com.apple.preferences.AppleIDPrefPane"
    }

    override fun usbGpsHelper(): Path? = null
}

internal fun parseSystemName(swVersOutput: String, arch: String): String {
    val map = mutableMapOf<String, String>()
    swVersOutput.lines().forEach { line ->
        val parts = line.split(":", limit = 2)
        if (parts.size == 2) {
            map[parts[0].trim()] = parts[1].trim()
        }
    }
    val name = map["ProductName"] ?: "macOS"
    val version = map["ProductVersion"] ?: return ""
    val build = map["BuildVersion"] ?: return ""
    return "$name $version (build $build, $arch)"
}

private fun defaultShowAlert(text: String) {
    runCatching {
        val escaped = text.replace("\\", "\\\\").replace("\"", "\\\"")
        ProcessBuilder("/usr/bin/osascript", "-e", "display alert \"ChairPlay\" message \"$escaped\" as critical").start().waitFor()
    }
}

private fun defaultReadDark(): Boolean? = runCatching {
    val p = ProcessBuilder("/usr/bin/defaults", "read", "-g", "AppleInterfaceStyle").start()
    val out = p.inputStream.bufferedReader().readText().trim()
    val exit = p.waitFor()
    exit == 0 && out.equals("Dark", ignoreCase = true)
}.getOrNull()
