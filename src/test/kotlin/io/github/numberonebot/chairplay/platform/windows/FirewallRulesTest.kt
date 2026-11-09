package io.github.numberonebot.chairplay.platform.windows

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

class FirewallTest {
    private val program = "C:\\Users\\kate\\AppData\\Local\\Programs\\ChairPlay\\ChairPlay.exe"
    private val everyProfile = DOMAIN or PRIVATE or PUBLIC

    private fun rule(
        name: String,
        allow: Boolean,
        app: String? = program,
        profiles: Int = ALL_PROFILES,
        enabled: Boolean = true,
        inbound: Boolean = true,
        protocol: Int = ANY_PROTOCOL,
        ports: String = "*",
    ) = FirewallRule(name, enabled, inbound, allow, profiles, app, protocol = protocol, localPorts = ports)

    private fun state(vararg rules: FirewallRule, enabled: Int = everyProfile, connected: Int = PRIVATE, uiPort: Int? = null) =
        firewallState(rules.toList(), program, enabled, connected, uiPort)

    @Test
    fun `a rule's ports cover a port by number, range, or any`() {
        for (ports in listOf("7529", "7000-7010,7529", "7500-7600", "*", " 7000-7010, 7529 "))
            assertTrue(ports, portsCover(ports, 7529))
        for (ports in listOf("7000-7010,49152-65535", "RPC", "", "75290", "7530-7600"))
            assertFalse(ports, portsCover(ports, 7529))
    }

    // With ui.network on, a rule added before the switch must not hide the UI's port missing from it.
    @Test
    fun `with the network on, our rule counts once one of ours opens the UI's port over TCP`() {
        val carplay = rule(FIREWALL_RULE, allow = true, protocol = TCP, ports = "7000-7010,49152-65535")

        assertFalse(state(carplay, uiPort = 7529).allowRule)
        assertTrue(state(carplay).allowRule)
        assertTrue(state(carplay, rule(FIREWALL_RULE, allow = true, protocol = TCP, ports = "7529"), uiPort = 7529).allowRule)
        assertFalse(state(carplay, rule(FIREWALL_RULE, allow = true, protocol = 17, ports = "7529"), uiPort = 7529).allowRule)
        assertTrue(state(carplay, rule(FIREWALL_RULE, allow = true), uiPort = 7529).allowRule)
        assertFalse(state(carplay, rule("Someone else's", allow = true, protocol = TCP, ports = "7529"), uiPort = 7529).allowRule)
        assertFalse(state(carplay, rule(FIREWALL_RULE, allow = true, protocol = TCP, ports = "7529", enabled = false), uiPort = 7529).allowRule)
    }

    // The iPhone gets in through it: what is missing is only the settings' port, which the messages must not misname.
    @Test
    fun `our rule without the UI's port is told apart from no rule at all`() {
        val carplay = rule(FIREWALL_RULE, allow = true, protocol = TCP, ports = "7000-7010,49152-65535")

        assertTrue(state(carplay, uiPort = 7529).uiPortMissing)
        assertFalse(state(uiPort = 7529).uiPortMissing)
        assertFalse(state(carplay).uiPortMissing)
        assertFalse(state(carplay, rule(FIREWALL_RULE, allow = true, protocol = TCP, ports = "7529"), uiPort = 7529).uiPortMissing)
    }

    @Test
    fun `the hint opens the UI's port while the network is on`() {
        val lines = firewallHint(7000, program, uiPort = 7529).lines()

        assertEquals(4, lines.size)
        assertTrue(lines.last(), lines.last().endsWith("protocol=TCP localport=7529"))
        assertEquals(3, firewallHint(7000, program).lines().size)
        assertEquals(4, netshScript(firewallHint(7000, program, uiPort = 7529)).lines().size)
    }

    @Test
    fun `our rule and a block rule from the first-run prompt`() {
        val state = state(
            rule(FIREWALL_RULE, allow = true),
            rule("ChairPlay", allow = false, profiles = PUBLIC),
            rule("Заблоковано ChairPlay", allow = false, profiles = PRIVATE or PUBLIC),
        )

        assertTrue(state.allowRule)
        assertEquals(
            listOf("\"ChairPlay\" (profiles Public)", "\"Заблоковано ChairPlay\" (profiles Private, Public)"),
            state.blocking,
        )
    }

    @Test
    fun `no rule of ours and nothing blocking`() {
        assertEquals(FirewallState(allowRule = false, blocking = emptyList()), state())
    }

    // Only an enabled inbound allow rule of that name lets the iPhone in.
    @Test
    fun `our rule counts only enabled, inbound, and allowing`() {
        assertFalse(state(rule(FIREWALL_RULE, allow = true, enabled = false)).allowRule)
        assertFalse(state(rule(FIREWALL_RULE, allow = true, inbound = false)).allowRule)
        assertFalse(state(rule(FIREWALL_RULE, allow = false)).allowRule)
    }

    // Our rule lets in the exe it names: one left for a folder moved or installed elsewhere lets this one nowhere.
    @Test
    fun `our rule for another exe does not count`() {
        assertFalse(state(rule(FIREWALL_RULE, allow = true, app = "D:\\Old place\\ChairPlay.exe")).allowRule)
        assertFalse(state(rule(FIREWALL_RULE, allow = true, app = null)).allowRule)
        assertTrue(state(rule(FIREWALL_RULE, allow = true, app = program.lowercase())).allowRule)
    }

    // Adding the rule clears ours for an exe that is gone, once per exe; another copy's rule, whose exe is there, stays.
    @Test
    fun `adding the rule deletes ours for an exe that no longer exists`() {
        val gone = "D:\\Old place\\ChairPlay.exe"
        val other = "E:\\Portable\\ChairPlay.exe"
        val rules = listOf(
            rule(FIREWALL_RULE, allow = true, app = gone),
            rule(FIREWALL_RULE, allow = true, app = gone),
            rule(FIREWALL_RULE, allow = true, app = other),
            rule("Someone else's rule", allow = true, app = "D:\\Gone\\tool.exe"),
            rule(FIREWALL_RULE, allow = true, app = null),
        )

        val commands = staleRuleCommands(rules) { it == other }

        assertEquals(listOf("netsh advfirewall firewall delete rule name=\"$FIREWALL_RULE\" program=\"$gone\""), commands)
    }

    // A path stored with a variable (%ProgramFiles%) is matched expanded but written as stored: netsh needs the stored one.
    @Test
    fun `a rule stored with a variable is deleted by the path as stored`() {
        val stored = FirewallRule(FIREWALL_RULE, true, true, true, ALL_PROFILES, "C:\\Gone\\ChairPlay.exe", "%SystemDrive%\\Gone\\ChairPlay.exe")

        assertEquals(
            listOf("netsh advfirewall firewall delete rule name=\"$FIREWALL_RULE\" program=\"%SystemDrive%\\Gone\\ChairPlay.exe\""),
            staleRuleCommands(listOf(stored)) { false },
        )
    }

    // Windows stores the path as it was typed: any case, or with an environment variable expanded before the call.
    @Test
    fun `a block rule counts for this program only, in any case, enabled and inbound`() {
        assertEquals(1, state(rule("ChairPlay", allow = false, app = program.uppercase())).blocking.size)
        assertEquals(0, state(rule("Other", allow = false, app = "C:\\Other\\other.exe")).blocking.size)
        assertEquals(0, state(rule("Every program", allow = false, app = null)).blocking.size)
        assertEquals(0, state(rule("ChairPlay", allow = false, enabled = false)).blocking.size)
        assertEquals(0, state(rule("ChairPlay", allow = false, inbound = false)).blocking.size)
        assertEquals(listOf("\"ChairPlay\" (profiles Any)"), state(rule("ChairPlay", allow = false)).blocking)
    }

    // A firewall turned off for the network the PC is on lets the iPhone in without any rule.
    @Test
    fun `a firewall off for the connected network needs no rule`() {
        val privateOff = DOMAIN or PUBLIC

        assertTrue(state(enabled = privateOff, connected = PRIVATE).off)
        assertFalse(state(enabled = privateOff, connected = PUBLIC).off)
        assertFalse("every connected network counts", state(enabled = privateOff, connected = PRIVATE or PUBLIC).off)
        assertFalse(state(enabled = privateOff, connected = DOMAIN).off)
    }

    @Test
    fun `without a connected network the firewall is off only when every profile is`() {
        assertTrue(state(enabled = 0, connected = 0).off)
        assertFalse(state(enabled = PRIVATE, connected = 0).off)
    }

    // The rule's commands run in one netsh started with the administrator's consent: its script has no "netsh".
    @Test
    fun `the rule's commands become one netsh script`() {
        val script = netshScript(firewallHint(7000, program))

        assertEquals(3, script.lines().size)
        assertTrue(script, script.lines().all { it.startsWith("advfirewall firewall add rule ") })
    }

    // The script runs as the administrator: anything but a firewall rule command stops it from being written.
    @Test
    fun `a netsh script takes only firewall rule commands`() {
        assertThrows(IllegalArgumentException::class.java) { netshScript("netsh interface set interface Wi-Fi disable") }
        assertThrows(IllegalArgumentException::class.java) { netshScript("netsh advfirewall reset") }
    }

    /** Reads this machine's rules (never changes them): the API must at least answer. */
    @Test
    fun `the query runs against the real firewall`() {
        val state = readFirewall("C:\\no such folder\\DiPlay test.exe")

        assertNotNull(state)
        assertFalse(state!!.blocking.any())
    }
}
