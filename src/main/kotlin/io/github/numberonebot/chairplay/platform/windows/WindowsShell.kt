package io.github.numberonebot.chairplay.platform.windows

import com.sun.jna.platform.win32.Advapi32Util
import com.sun.jna.platform.win32.WinReg
import io.github.numberonebot.chairplay.config.DesktopConfig
import io.github.numberonebot.chairplay.platform.DesktopShell
import java.nio.file.Path

/** The Windows desktop's facts, from the registry, and the app folder beside the exe. */
internal class WindowsShell : DesktopShell {
    /**
     * "Windows 11 24H2 (build 26300)", from the registry: its ProductName says Windows 10 on Windows 11 too, so the name
     * comes from the build number (22000 and later are Windows 11).
     */
    override fun systemName(): String {
        val key = "SOFTWARE\\Microsoft\\Windows NT\\CurrentVersion"
        fun value(name: String) = runCatching { Advapi32Util.registryGetStringValue(WinReg.HKEY_LOCAL_MACHINE, key, name) }.getOrNull()
        val build = value("CurrentBuildNumber") ?: return System.getProperty("os.name")
        val name = if ((build.toIntOrNull() ?: 0) >= 22000) "Windows 11" else "Windows 10"
        return listOfNotNull(name, value("DisplayVersion")).joinToString(" ") + " (build $build)"
    }

    /** Whether Windows apps use the dark theme (Settings > Personalization > Colors), or null if it cannot be read. */
    override fun appsDark(): Boolean? = runCatching {
        Advapi32Util.registryGetIntValue(
            WinReg.HKEY_CURRENT_USER,
            "Software\\Microsoft\\Windows\\CurrentVersion\\Themes\\Personalize",
            "AppsUseLightTheme",
        ) == 0
    }.getOrNull()

    override fun dataDirectory(): Path = DesktopConfig.appDirectory()
}
