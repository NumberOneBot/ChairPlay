package io.github.numberonebot.chairplay.platform.macos

import io.github.numberonebot.chairplay.platform.DesktopShell
import io.github.numberonebot.chairplay.platform.Platform
import java.nio.file.Path

/** macOS, chosen for a Mac but not ready: every use of it throws [notReady]'s exception. */
internal class MacPlatform : Platform {
    override val shell: DesktopShell = MacShell
}

private object MacShell : DesktopShell {
    override fun systemName(): String = notReady()

    override fun appsDark(): Boolean? = notReady()

    override fun dataDirectory(): Path = notReady()
}

/** Thrown by every member of [MacPlatform] until the Mac adapters exist. */
private fun notReady(): Nothing = throw UnsupportedOperationException("macOS support is not ready")
