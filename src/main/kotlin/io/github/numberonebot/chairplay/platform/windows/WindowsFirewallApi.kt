package io.github.numberonebot.chairplay.platform.windows

import com.sun.jna.platform.win32.Guid
import com.sun.jna.platform.win32.Kernel32Util

/** What [WindowsFirewallApi] read: the rules, the profiles with the firewall on, and those of the connected networks. */
internal data class FirewallSnapshot(val rules: List<FirewallRule>, val enabledProfiles: Int, val connectedProfiles: Int)

/**
 * Windows Firewall through its own API (HNetCfg.FwPolicy2), which any user may read, and the connected networks'
 * categories from the Network List Manager, as Settings shows them: no PowerShell, no WMI. FwPolicy2's own
 * CurrentProfileTypes also counts a virtual adapter's unidentified network as public, which would ask for a rule on a
 * PC whose Wi-Fi network has the firewall off. The rules are walked once, a few hundred on a usual PC; a rule's path is
 * expanded, so one written with %ProgramFiles% counts.
 */
internal object WindowsFirewallApi {
    private const val NET_FW_RULE_DIR_IN = 1
    private const val NET_FW_ACTION_ALLOW = 1
    private val NETWORK_LIST_MANAGER = Guid.CLSID("{DCB00C01-570F-4A9B-8D69-199FDBA5723B}")
    private const val NLM_ENUM_NETWORK_CONNECTED = 1
    /** NLM_NETWORK_CATEGORY: public, private, domain-authenticated. */
    private val PROFILE_OF_CATEGORY = mapOf(0 to PUBLIC, 1 to PRIVATE, 2 to DOMAIN)

    fun read(): FirewallSnapshot? = onComThread("firewall-read", 30_000) {
        FirewallSnapshot(rulesAndProfiles(), enabledProfiles(), connectedProfiles())
    }

    private fun enabledProfiles(): Int {
        val policy = Automation("HNetCfg.FwPolicy2")
        try {
            return listOf(DOMAIN, PRIVATE, PUBLIC).filter { policy.booleanAt("FirewallEnabled", it) }
                .fold(0) { bits, profile -> bits or profile }
        } finally {
            policy.release()
        }
    }

    private fun rulesAndProfiles(): List<FirewallRule> {
        val policy = Automation("HNetCfg.FwPolicy2")
        val rules = policy.child("Rules")
        try {
            val found = mutableListOf<FirewallRule>()
            rules.forEach { rule ->
                val stored = rule.string("ApplicationName")
                found += FirewallRule(
                    name = rule.string("Name").orEmpty(),
                    enabled = rule.boolean("Enabled"),
                    inbound = rule.int("Direction") == NET_FW_RULE_DIR_IN,
                    allow = rule.int("Action") == NET_FW_ACTION_ALLOW,
                    profiles = rule.int("Profiles"),
                    program = stored?.let(Kernel32Util::expandEnvironmentStrings),
                    storedProgram = stored,
                    // A rule that will not say its protocol counts as any, rather than failing the whole read.
                    protocol = runCatching { rule.int("Protocol") }.getOrDefault(ANY_PROTOCOL),
                    localPorts = rule.string("LocalPorts") ?: "*",
                )
            }
            return found
        } finally {
            rules.release()
            policy.release()
        }
    }

    private fun connectedProfiles(): Int {
        val manager = Automation(NETWORK_LIST_MANAGER)
        val networks = manager.callObject("GetNetworks", NLM_ENUM_NETWORK_CONNECTED)
        try {
            var profiles = 0
            networks.forEach { profiles = profiles or (PROFILE_OF_CATEGORY[it.callInt("GetCategory")] ?: 0) }
            return profiles
        } finally {
            networks.release()
            manager.release()
        }
    }
}
