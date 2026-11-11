package io.github.numberonebot.chairplay.platform.windows

import com.sun.jna.Library
import com.sun.jna.Native
import com.sun.jna.Pointer
import com.sun.jna.WString
import com.sun.jna.platform.win32.Advapi32Util
import com.sun.jna.platform.win32.Shell32
import com.sun.jna.platform.win32.WinReg
import com.sun.jna.platform.win32.WinUser
import io.github.numberonebot.chairplay.config.DesktopConfig
import io.github.numberonebot.chairplay.platform.DesktopShell
import io.github.numberonebot.chairplay.platform.SystemPage
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

    // The frozen helper (PyInstaller --onedir) the image ships in helper\ beside ChairPlay.exe.
    override fun usbGpsHelper(): Path? =
        DesktopConfig.appDirectory().resolve("helper").resolve("helper.exe").takeIf { java.nio.file.Files.isRegularFile(it) }

    /** Windows' own message box, as no window of the app is open to show the failure in (or none to go on with). */
    override fun showError(text: String) {
        Native.load("user32", MessageBoxes::class.java).MessageBoxW(null, WString(text), WString("ChairPlay"), MB_ICONERROR or MB_SETFOREGROUND)
    }

    /** The default browser's own start, through the shell. */
    override fun openInBrowser(url: String) {
        val result = Shell32.INSTANCE.ShellExecute(null, "open", url, null, null, WinUser.SW_SHOWNORMAL)
        // ShellExecute answers a number above 32 when it started something, an error code otherwise.
        val code = result?.toLong() ?: 0
        check(code > 32) { "ShellExecute failed ($code)" }
    }

    /** Through explorer.exe, which hands the target to the shell: a Settings address opens its page, a web address the browser. */
    override fun openCommand(target: String): List<String> = listOf("explorer.exe", target)

    override fun textFileCommand(file: Path): List<String> = listOf("notepad.exe", file.toString())

    override fun systemPage(page: SystemPage): String = when (page) {
        SystemPage.BLUETOOTH -> "ms-settings:bluetooth"
        SystemPage.WIFI -> "ms-settings:network-wifi"
        SystemPage.FIREWALL -> "windowsdefender://network/"
        SystemPage.FIREWALL_RULES -> Path.of(System.getenv("SystemRoot") ?: "C:/Windows", "System32", "wf.msc").toString()
        SystemPage.LOCATION -> "ms-settings:privacy-location"
        // Apple Devices from Microsoft Store, by its app ID: its usbmux runs while the app does.
        SystemPage.APPLE_DEVICES -> "shell:AppsFolder\\AppleInc.AppleDevices_nzyj5cx40ttqa!App"
    }

    private interface MessageBoxes : Library {
        fun MessageBoxW(window: Pointer?, text: WString, caption: WString, type: Int): Int
    }

    private companion object {
        const val MB_ICONERROR = 0x10
        const val MB_SETFOREGROUND = 0x10000
    }
}
