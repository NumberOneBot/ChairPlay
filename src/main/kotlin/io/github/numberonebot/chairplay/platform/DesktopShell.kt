package io.github.numberonebot.chairplay.platform

import java.nio.file.Path

/** The system pages the app links to. */
internal enum class SystemPage { BLUETOOTH, WIFI, FIREWALL, FIREWALL_RULES, LOCATION, APPLE_DEVICES }

/** The desktop's own facts, as the app reads them once or on its theme watch, and what it asks the desktop to show. */
internal interface DesktopShell {
    /** The system's name and version for the details of a report, such as "Windows 11 24H2 (build 26300)". */
    fun systemName(): String

    /** Whether the system's apps use the dark theme, or null if it cannot be read. */
    fun appsDark(): Boolean?

    /** The app folder, where the settings, keys and pairings live. */
    fun dataDirectory(): Path

    /** Shows [text] in the system's own message box, where no window of the app is open to show it in. */
    fun showError(text: String)

    /** Opens [url] in the default browser; throws when nothing opened. */
    fun openInBrowser(url: String)

    /** The command that has the system open [target]: a folder, a web address, or a system page. */
    fun openCommand(target: String): List<String>

    /** The command that opens [file] in a text editor. */
    fun textFileCommand(file: Path): List<String>

    /** What [openCommand] takes to open [page]. */
    fun systemPage(page: SystemPage): String

    /** The USB GPS helper program the app's image ships (helper/ beside the app), or null when it has none. */
    fun usbGpsHelper(): Path? = null
}
