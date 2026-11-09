package io.github.numberonebot.chairplay.platform

import io.github.numberonebot.chairplay.platform.macos.MacPlatform
import io.github.numberonebot.chairplay.platform.windows.WindowsPlatform

/** The platform the app runs on, chosen once from `os.name`; the only file that names the adapters. */
internal object Platforms {
    val current: Platform by lazy { select(System.getProperty("os.name").orEmpty()) }

    /**
     * Why the app cannot start on the platform [choose] gives, or null if it can: the system is unknown, or the platform
     * is a stub. Uses the platform once (its system name), so a stub refuses here and not in the middle of the start.
     */
    fun refusal(choose: () -> Platform = { current }): String? = try {
        choose().shell.systemName()
        null
    } catch (e: UnsupportedPlatform) {
        e.message
    } catch (e: UnsupportedOperationException) {
        e.message
    }

    /** The platform for a system that calls itself [osName], or [UnsupportedPlatform] for one the app does not know. */
    fun select(osName: String): Platform = when {
        osName.startsWith("Windows") -> WindowsPlatform()
        osName.startsWith("Mac") -> MacPlatform()
        else -> throw UnsupportedPlatform(osName)
    }
}

/** The app was started on a system it has no platform for, named [osName]. */
internal class UnsupportedPlatform(osName: String) :
    IllegalStateException("ChairPlay does not run on $osName. Run it on Windows 10 or 11.")
