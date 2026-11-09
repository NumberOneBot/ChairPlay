package io.github.numberonebot.chairplay.platform.macos

import io.github.numberonebot.chairplay.log.AppLog
import io.github.numberonebot.chairplay.platform.DesktopShell
import io.github.numberonebot.chairplay.platform.Firewall
import io.github.numberonebot.chairplay.platform.KeepAliveTuning
import io.github.numberonebot.chairplay.platform.LocationResult
import io.github.numberonebot.chairplay.platform.LocationSource
import io.github.numberonebot.chairplay.network.EthernetAdapter
import io.github.numberonebot.chairplay.network.WlanConnection
import io.github.numberonebot.chairplay.platform.OneCopyClaim
import io.github.numberonebot.chairplay.platform.PcNetwork
import io.github.numberonebot.chairplay.platform.Platform
import io.github.numberonebot.chairplay.platform.SystemPage
import io.github.numberonebot.chairplay.ui.FirewallStatus
import java.io.Closeable
import java.net.InetAddress
import java.net.Socket
import java.nio.file.Path

/** macOS, chosen for a Mac but not ready: every use of it throws [notReady]'s exception. */
internal class MacPlatform : Platform {
    override val shell: DesktopShell = MacShell
    override val oneCopy: OneCopyClaim = MacOneCopy
    override val keepAlive: KeepAliveTuning = MacKeepAlive
    override val location: LocationSource = MacLocation
    override val firewall: Firewall = MacFirewall
    override val network: PcNetwork = MacNetwork
}

private object MacFirewall : Firewall {
    override fun check(port: Int, uiPort: Int?, log: AppLog): FirewallStatus = notReady()

    override fun status(uiPort: Int?): FirewallStatus = notReady()

    override fun hint(port: Int, uiPort: Int?): String = notReady()

    override fun addRules(commands: String): Boolean = notReady()
}

private object MacNetwork : PcNetwork {
    override fun wifiConnections(): List<WlanConnection> = notReady()

    override fun ethernetAdapters(): List<EthernetAdapter> = notReady()

    override fun physicalAddresses(): List<InetAddress> = notReady()
}

private object MacShell : DesktopShell {
    override fun systemName(): String = notReady()

    override fun appsDark(): Boolean? = notReady()

    override fun dataDirectory(): Path = notReady()

    override fun showError(text: String) = notReady()

    override fun openInBrowser(url: String) = notReady()

    override fun openCommand(target: String): List<String> = notReady()

    override fun textFileCommand(file: Path): List<String> = notReady()

    override fun systemPage(page: SystemPage): String = notReady()
}

private object MacOneCopy : OneCopyClaim {
    override fun claim(name: String, timeoutMillis: Long): Closeable? = notReady()
}

private object MacKeepAlive : KeepAliveTuning {
    override fun tune(socket: Socket, idleSeconds: Int, intervalSeconds: Int, count: Int, deadlineSeconds: Int) = notReady()

    override fun timing(socket: Socket): List<Int> = notReady()
}

private object MacLocation : LocationSource {
    override fun read(): LocationResult = notReady()
}

/** Thrown by every member of [MacPlatform] until the Mac adapters exist. */
private fun notReady(): Nothing = throw UnsupportedOperationException("macOS support is not ready")
