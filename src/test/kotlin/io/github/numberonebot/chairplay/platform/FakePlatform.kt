package io.github.numberonebot.chairplay.platform

import io.github.numberonebot.chairplay.log.AppLog
import io.github.numberonebot.chairplay.ui.FirewallStatus
import java.io.Closeable
import java.net.Socket
import java.nio.file.Path

/** A platform for tests: each answer is a field the test sets, and nothing reaches the real system. */
internal class FakePlatform(
    override val shell: FakeShell = FakeShell(),
    override val oneCopy: OneCopyClaim = OneCopyClaim { _, _ -> Closeable { } },
    override val keepAlive: KeepAliveTuning = FakeKeepAlive(),
    override val location: LocationSource = LocationSource { LocationResult.Unknown },
    override val firewall: FakeFirewall = FakeFirewall(),
) : Platform

/** A firewall that answers [state] and records the rules it was asked to add. */
internal class FakeFirewall(var state: FirewallStatus = FirewallStatus.UNKNOWN, var consent: Boolean = true) : Firewall {
    val added = mutableListOf<String>()

    override fun check(port: Int, uiPort: Int?, log: AppLog): FirewallStatus = state

    override fun status(uiPort: Int?): FirewallStatus = state

    override fun hint(port: Int, uiPort: Int?): String = "add rule for $port,$uiPort"

    override fun addRules(commands: String): Boolean {
        added += commands
        return consent
    }
}

/** A desktop shell that answers what the test set, and records what it was asked to show or open. */
internal class FakeShell(
    var name: String = "Windows 11 24H2 (build 26300)",
    var dark: Boolean? = null,
    var directory: Path = Path.of(System.getProperty("java.io.tmpdir")),
) : DesktopShell {
    val errors = mutableListOf<String>()
    val browsed = mutableListOf<String>()

    override fun systemName(): String = name

    override fun appsDark(): Boolean? = dark

    override fun dataDirectory(): Path = directory

    override fun showError(text: String) {
        errors += text
    }

    override fun openInBrowser(url: String) {
        browsed += url
    }

    override fun openCommand(target: String): List<String> = listOf("open", target)

    override fun textFileCommand(file: Path): List<String> = listOf("edit", file.toString())

    override fun systemPage(page: SystemPage): String = "page:${page.name}"
}

/** Keep-alive tuning that remembers the last values it was given. */
internal class FakeKeepAlive : KeepAliveTuning {
    var tuned: List<Int> = emptyList()

    override fun tune(socket: Socket, idleSeconds: Int, intervalSeconds: Int, count: Int, deadlineSeconds: Int) {
        tuned = listOf(idleSeconds, intervalSeconds, count, deadlineSeconds)
    }

    override fun timing(socket: Socket): List<Int> = tuned
}
