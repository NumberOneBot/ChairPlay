package io.github.numberonebot.chairplay.platform.windows

import io.github.numberonebot.chairplay.platform.DesktopShell
import io.github.numberonebot.chairplay.platform.KeepAliveTuning
import io.github.numberonebot.chairplay.platform.OneCopyClaim
import io.github.numberonebot.chairplay.platform.Platform

/** Windows, through today's adapters; each is made the first time it is asked for, so none loads its DLLs before. */
internal class WindowsPlatform : Platform {
    override val shell: DesktopShell by lazy { WindowsShell() }
    override val oneCopy: OneCopyClaim by lazy { OneCopyClaim { name, timeoutMillis -> NamedMutex.claim(name, timeoutMillis) } }
    override val keepAlive: KeepAliveTuning by lazy { WinsockKeepAlive() }
}
