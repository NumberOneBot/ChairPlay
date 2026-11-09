package io.github.numberonebot.chairplay.platform.windows

import io.github.numberonebot.chairplay.log.AppLog
import io.github.numberonebot.chairplay.log.LogLevel
import io.github.numberonebot.chairplay.platform.Firewall
import io.github.numberonebot.chairplay.ui.FirewallStatus

/**
 * Windows Firewall for [program], the exe the rules name. [check] says what keeps the iPhone out, if anything, in the
 * log: as warnings, which a release's log level keeps, with the commands that add the rule. A block rule wins over any
 * allow rule, so one left by Windows' first-run prompt is reported even when ours exists. The start screen shows the
 * same. [read] reads the rules for a program and the extra port the rule must open.
 */
internal class WindowsFirewall(
    private val program: String = ProcessHandle.current().info().command().orElse("java.exe"),
    private val read: (String, Int?) -> FirewallState? = ::readFirewall,
) : Firewall {
    override fun check(port: Int, uiPort: Int?, log: AppLog): FirewallStatus {
        fun warn(message: String) = log.log(LogLevel.WARNING, message)
        val hint = "from an administrator prompt:\n" + hint(port, uiPort)
        val state = read(program, uiPort)
        if (state == null) {
            warn("could not read the Windows Firewall rules; if the iPhone cannot connect, add these $hint")
            return FirewallStatus.UNKNOWN
        }
        if (state.off) {
            log.info("Windows Firewall is off for the networks this PC is on; ChairPlay needs no rule")
            return FirewallStatus.OFF
        }
        if (state.uiPortMissing) {
            // Ours lets the iPhone in; only the settings' port, which ui.network needs, is not in it.
            warn("Windows Firewall rule \"$FIREWALL_RULE\" does not open TCP port $uiPort for the settings; to open " +
                "them from another device, add it again")
            warn(hint)
        } else if (!state.allowRule) {
            // Another rule (Windows' own, from its first-run prompt) may let the iPhone in: ours is only missing.
            warn("no Windows Firewall rule \"$FIREWALL_RULE\"; if the iPhone cannot connect, add it")
            warn(hint)
        }
        state.blocking.forEach {
            warn("Windows Firewall rule $it blocks inbound connections to $program; the iPhone cannot connect while " +
                "it applies. Delete it or set it to Allow in Windows Defender Firewall > Advanced settings > Inbound Rules")
        }
        return firewallStatus(state)
    }

    override fun status(uiPort: Int?): FirewallStatus = read(program, uiPort)?.let(::firewallStatus) ?: FirewallStatus.UNKNOWN

    override fun hint(port: Int, uiPort: Int?): String = firewallHint(port, program, uiPort)

    override fun addRules(commands: String): Boolean = addFirewallRules(commands)

    private fun firewallStatus(state: FirewallState): FirewallStatus = when {
        state.off -> FirewallStatus.OFF
        state.blocking.isNotEmpty() -> FirewallStatus.BLOCKED
        state.uiPortMissing -> FirewallStatus.PARTIAL
        !state.allowRule -> FirewallStatus.MISSING
        else -> FirewallStatus.PRESENT
    }
}
