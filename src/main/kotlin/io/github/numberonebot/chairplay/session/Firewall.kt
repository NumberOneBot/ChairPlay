package io.github.numberonebot.chairplay.session

import io.github.numberonebot.chairplay.windows.runElevated
import java.io.File
import java.nio.file.Files
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

/**
 * One rule as the API reports it: [name] is what Windows shows, [program] the path it filters on, if any, with its
 * variables expanded; [storedProgram] is that path as written in the rule, which netsh matches.
 */
internal data class FirewallRule(
    val name: String,
    val enabled: Boolean,
    val inbound: Boolean,
    val allow: Boolean,
    val profiles: Int,
    val program: String?,
    val storedProgram: String? = program,
)

/**
 * Reads the rules only; changing them needs an administrator ([addFirewallRules] asks for the consent). Returns null
 * when they could not be read. Windows Firewall's own API answers in numbers on every display language, where netsh's
 * listing is translated.
 */
internal fun readFirewall(program: String): FirewallState? =
    WindowsFirewallApi.read()?.let { firewallState(it.rules, program, it.enabledProfiles, it.connectedProfiles) }

/**
 * What [rules] mean for [program]: a profile in [enabledProfiles] has the firewall on, and [connectedProfiles] are those
 * of the networks the PC is on (none: every profile applies). Our rule counts only for this exe: one for a folder moved
 * or installed elsewhere lets nothing in here. Paths compare in any case, as Windows does.
 */
internal fun firewallState(rules: List<FirewallRule>, program: String, enabledProfiles: Int, connectedProfiles: Int): FirewallState {
    val forProgram = { rule: FirewallRule -> rule.program.equals(program, ignoreCase = true) }
    val allow = rules.any { it.name == FIREWALL_RULE && it.enabled && it.inbound && it.allow && forProgram(it) }
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
 * a prompt left open would.
 */
fun addFirewallRules(commands: String): Boolean {
    val script = Files.createTempFile("chairplay-firewall", ".txt")
    return try {
        val stale = WindowsFirewallApi.read()?.rules.orEmpty().let { rules -> staleRuleCommands(rules) { File(it).isFile } }
        // netsh reads its script as UTF-8, with no byte-order mark: a path with any user name survives.
        Files.writeString(script, netshScript((listOf(commands) + stale).joinToString("\n")))
        val netsh = System.getenv("SystemRoot").orEmpty().ifEmpty { "C:\\Windows" } + "\\System32\\netsh.exe"
        runElevated(netsh, "-f \"$script\"", TimeUnit.MINUTES.toMillis(5)) == 0
    } catch (_: Exception) {
        false
    } finally {
        Files.deleteIfExists(script)
    }
}

/** [commands] as a netsh script: the same commands without the "netsh" that starts each. */
internal fun netshScript(commands: String): String =
    commands.lines().map(String::trim).filter(String::isNotEmpty)
        .joinToString("\r\n") { it.removePrefix("netsh").trim() }
