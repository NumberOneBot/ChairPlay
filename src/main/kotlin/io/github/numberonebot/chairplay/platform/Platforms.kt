package io.github.numberonebot.chairplay.platform

import io.github.numberonebot.chairplay.platform.macos.MacPlatform
import io.github.numberonebot.chairplay.platform.windows.WindowsPlatform

/** The platform the app runs on, chosen once from `os.name`; the only file that names the adapters. */
internal object Platforms {
    val current: Platform by lazy { select(System.getProperty("os.name").orEmpty()) }

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
