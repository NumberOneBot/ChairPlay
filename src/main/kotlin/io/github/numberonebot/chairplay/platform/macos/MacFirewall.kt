package io.github.numberonebot.chairplay.platform.macos

import io.github.numberonebot.chairplay.log.AppLog
import io.github.numberonebot.chairplay.log.LogLevel
import io.github.numberonebot.chairplay.platform.Firewall
import io.github.numberonebot.chairplay.ui.FirewallStatus

internal data class CmdResult(val exitCode: Int, val stdout: String)

internal class MacFirewall(
    private val program: String = ProcessHandle.current().info().command().orElse("ChairPlay"),
    private val runCmd: (List<String>) -> CmdResult? = ::defaultRunCmd,
) : Firewall {

    override fun check(port: Int, uiPort: Int?, log: AppLog): FirewallStatus {
        val st = status(uiPort)
        when (st) {
            FirewallStatus.OFF -> {
                log.info("macOS Application Firewall is disabled; ChairPlay needs no rule")
            }
            FirewallStatus.BLOCKED -> {
                log.log(LogLevel.WARNING, "macOS Application Firewall blocks inbound connections to $program; run: ${hint(port, uiPort)}")
            }
            FirewallStatus.UNKNOWN -> {
                log.log(LogLevel.WARNING, "could not query macOS Application Firewall; if the iPhone cannot connect, run: ${hint(port, uiPort)}")
            }
            FirewallStatus.PRESENT -> {
                log.info("macOS Application Firewall allows incoming connections to $program")
            }
            else -> {}
        }
        return st
    }

    override fun status(uiPort: Int?): FirewallStatus {
        val globalRes = runCmd(listOf(SOCKETFILTERFW, "--getglobalstate")) ?: return FirewallStatus.UNKNOWN
        val globalOut = globalRes.stdout
        val appRes = runCmd(listOf(SOCKETFILTERFW, "--getappblocked", program))
        val appOut = appRes?.stdout
        return parseFirewallState(globalOut, appOut)
    }

    // macOS Application Firewall (ALF) controls incoming connections per-application binary, not per-port;
    // unblocking the program binary permits inbound traffic on CarPlay control port (7000) and settings uiPort.
    override fun hint(port: Int, uiPort: Int?): String =
        "sudo $SOCKETFILTERFW --add $program && sudo $SOCKETFILTERFW --unblockapp $program"
    override fun addRules(commands: String): Boolean {
        val escaped = commands.replace("\\", "\\\\").replace("\"", "\\\"")
        val osaScript = "do shell script \"$escaped\" with administrator privileges"
        val res = runCmd(listOf("/usr/bin/osascript", "-e", osaScript))
        return res != null && res.exitCode == 0
    }

    companion object {
        const val SOCKETFILTERFW = "/usr/libexec/ApplicationFirewall/socketfilterfw"
    }
}

internal fun parseFirewallState(globalOut: String, appOut: String?): FirewallStatus {
    val lowerGlobal = globalOut.lowercase()
    if ("state = 0" in lowerGlobal || "disabled" in lowerGlobal) {
        return FirewallStatus.OFF
    }
    if ("state = 1" in lowerGlobal || "enabled" in lowerGlobal) {
        if (appOut == null) return FirewallStatus.UNKNOWN
        val lowerApp = appOut.lowercase()
        return when {
            "is permitted" in lowerApp || "allowed" in lowerApp -> FirewallStatus.PRESENT
            "is blocked" in lowerApp || "blocked" in lowerApp -> FirewallStatus.BLOCKED
            else -> FirewallStatus.UNKNOWN
        }
    }
    return FirewallStatus.UNKNOWN
}

private fun defaultRunCmd(cmd: List<String>): CmdResult? = runCatching {
    val process = ProcessBuilder(cmd).redirectErrorStream(true).start()
    val stdout = process.inputStream.bufferedReader().readText().trim()
    val exit = process.waitFor()
    CmdResult(exit, stdout)
}.getOrNull()
