package io.github.numberonebot.chairplay.session

import io.github.numberonebot.chairplay.windows.runElevated
import io.github.numberonebot.chairplay.windows.withLockedFile
import java.io.File
import java.util.concurrent.TimeUnit

/**
 * The name of the rules [firewallHint] adds. Windows names the rules it creates from its own first-run
 * prompt after the program, "ChairPlay" for the packaged exe; answering Cancel there creates a block
 * rule, and allowing only private networks does nothing on a public one. A check by that name would find
 * Windows' rule and stay silent exactly when the iPhone cannot connect.
 */
const val FIREWALL_RULE = "ChairPlay CarPlay inbound"

/**
 * Whether our allow rule exists, the enabled inbound block rules for the program with their profiles, and whether the
 * firewall is off for the networks the PC is on, which lets the iPhone in without any rule.
 */
internal data class FirewallState(val allowRule: Boolean, val blocking: List<String>, val off: Boolean = false)

/** The firewall profiles as Windows Firewall's API numbers them (NET_FW_PROFILE_TYPE2), each a bit. */
internal const val DOMAIN = 1
internal const val PRIVATE = 2
internal const val PUBLIC = 4
/** A rule for every profile (NET_FW_PROFILE2_ALL). */
internal const val ALL_PROFILES = 0x7FFFFFFF
private const val EVERY_PROFILE = DOMAIN or PRIVATE or PUBLIC

/** A rule's protocol as Windows Firewall's API numbers it (NET_FW_IP_PROTOCOL): TCP, or any. */
internal const val TCP = 6
internal const val ANY_PROTOCOL = 256

/**
 * One rule as the API reports it: [name] is what Windows shows, [program] the path it filters on, if any, with its
 * variables expanded; [storedProgram] is that path as written in the rule, which netsh matches. [localPorts] is as
 * Windows writes it: `*`, or numbers and ranges such as `7000-7010,49152-65535`.
 */
internal data class FirewallRule(
    val name: String,
    val enabled: Boolean,
    val inbound: Boolean,
    val allow: Boolean,
    val profiles: Int,
    val program: String?,
    val storedProgram: String? = program,
    val protocol: Int = ANY_PROTOCOL,
    val localPorts: String = "*",
)

/** [localPorts] (as [FirewallRule] has them) let in [port]: `*`, the number, or a range holding it. */
internal fun portsCover(localPorts: String, port: Int): Boolean = localPorts.split(',').map(String::trim).any { part ->
    val range = part.split('-').map { it.toIntOrNull() }
    part == "*" || when (range.size) {
        1 -> range[0] == port
        2 -> range[0] != null && range[1] != null && port in range[0]!!..range[1]!!
        else -> false
    }
}

/**
 * Reads the rules only; changing them needs an administrator ([addFirewallRules] asks for the consent). Returns null
 * when they could not be read. Windows Firewall's own API answers in numbers on every display language, where netsh's
 * listing is translated.
 */
internal fun readFirewall(program: String, uiPort: Int? = null): FirewallState? =
    WindowsFirewallApi.read()?.let { firewallState(it.rules, program, it.enabledProfiles, it.connectedProfiles, uiPort) }

/**
 * What [rules] mean for [program]: a profile in [enabledProfiles] has the firewall on, and [connectedProfiles] are those
 * of the networks the PC is on (none: every profile applies). Our rule counts only for this exe: one for a folder moved
 * or installed elsewhere lets nothing in here. Paths compare in any case, as Windows does. With [uiPort] (ui.network
 * on), ours counts only once one of ours also opens that TCP port: a rule added before the switch does not.
 */
internal fun firewallState(
    rules: List<FirewallRule>,
    program: String,
    enabledProfiles: Int,
    connectedProfiles: Int,
    uiPort: Int? = null,
): FirewallState {
    val forProgram = { rule: FirewallRule -> rule.program.equals(program, ignoreCase = true) }
    val ours = rules.filter { it.name == FIREWALL_RULE && it.enabled && it.inbound && it.allow && forProgram(it) }
    val opensUi = uiPort == null ||
        ours.any { (it.protocol == TCP || it.protocol == ANY_PROTOCOL) && portsCover(it.localPorts, uiPort) }
    val allow = ours.isNotEmpty() && opensUi
    val blocking = rules.filter { it.enabled && it.inbound && !it.allow && forProgram(it) }
        .map { "\"${it.name}\" (profiles ${profileNames(it.profiles)})" }
    val applying = (connectedProfiles and EVERY_PROFILE).takeIf { it != 0 } ?: EVERY_PROFILE
    return FirewallState(allow, blocking, off = applying and enabledProfiles == 0)
}

private fun profileNames(profiles: Int): String =
    if (profiles and EVERY_PROFILE == EVERY_PROFILE) "Any"
    else listOf(DOMAIN to "Domain", PRIVATE to "Private", PUBLIC to "Public")
        .filter { (bit, _) -> profiles and bit != 0 }.joinToString(", ") { it.second }

/**
 * Our rules for an exe that is gone (a folder moved, or an install elsewhere removed), as netsh commands deleting them,
 * one per exe. A rule for an exe that [exists], another copy's, stays.
 */
internal fun staleRuleCommands(rules: List<FirewallRule>, exists: (String) -> Boolean): List<String> =
    rules.filter { it.name == FIREWALL_RULE && it.program != null && it.storedProgram != null && !exists(it.program) }
        .distinctBy { it.storedProgram!!.lowercase() }
        .map { "netsh advfirewall firewall delete rule name=\"$FIREWALL_RULE\" program=\"${it.storedProgram}\"" }

/**
 * Runs [commands] (one per line, as [firewallHint] writes them) in one netsh started with the administrator's consent,
 * so Windows asks once, then deletes our rules for an exe that is gone ([staleRuleCommands]). They come last: netsh
 * stops at the first command that fails, and the rule for this exe matters more. True when the consent was given and
 * every command succeeded; false when it was refused, a command failed, or nothing came back within five minutes, as
 * a prompt left open would. The script is held locked while netsh runs ([withLockedFile]): what the administrator
 * consented to is what runs.
 */
fun addFirewallRules(commands: String): Boolean = try {
    val stale = WindowsFirewallApi.read()?.rules.orEmpty().let { rules -> staleRuleCommands(rules) { File(it).isFile } }
    // netsh reads its script as UTF-8, with no byte-order mark: a path with any user name survives.
    withLockedFile(netshScript((listOf(commands) + stale).joinToString("\n"))) { script ->
        val netsh = System.getenv("SystemRoot").orEmpty().ifEmpty { "C:\\Windows" } + "\\System32\\netsh.exe"
        runElevated(netsh, "-f \"$script\"", TimeUnit.MINUTES.toMillis(5)) == 0
    }
} catch (_: Exception) {
    false
}

/**
 * [commands] as a netsh script: the same commands without the "netsh" that starts each. Only firewall rule commands
 * pass, as it runs as the administrator.
 */
internal fun netshScript(commands: String): String =
    commands.lines().map(String::trim).filter(String::isNotEmpty)
        .map { it.removePrefix("netsh").trim() }
        .onEach { require(it.startsWith("advfirewall firewall add rule ") || it.startsWith("advfirewall firewall delete rule ")) { it } }
        .joinToString("\r\n")
