package io.github.numberonebot.chairplay.platform

import java.nio.file.Path

/** The desktop's own facts, as the app reads them once or on its theme watch. */
internal interface DesktopShell {
    /** The system's name and version for the details of a report, such as "Windows 11 24H2 (build 26300)". */
    fun systemName(): String

    /** Whether the system's apps use the dark theme, or null if it cannot be read. */
    fun appsDark(): Boolean?

    /** The app folder, where the settings, keys and pairings live. */
    fun dataDirectory(): Path
}
