package io.github.numberonebot.chairplay.platform.macos

import io.github.numberonebot.chairplay.ui.FirewallStatus
import org.junit.Assert.assertEquals
import org.junit.Test

class MacFirewallTest {
    @Test
    fun `parseFirewallState maps global and app outputs to FirewallStatus`() {
        assertEquals(
            FirewallStatus.PRESENT,
            parseFirewallState(
                globalOut = "Firewall is enabled. (State = 1)",
                appOut = "Incoming connections to the application are allowed"
            )
        )

        assertEquals(
            FirewallStatus.OFF,
            parseFirewallState(
                globalOut = "Firewall is disabled. (State = 0)",
                appOut = null
            )
        )

        assertEquals(
            FirewallStatus.BLOCKED,
            parseFirewallState(
                globalOut = "Firewall is enabled. (State = 1)",
                appOut = "Incoming connections to the application are blocked"
            )
        )

        assertEquals(
            FirewallStatus.UNKNOWN,
            parseFirewallState(
                globalOut = "unexpected",
                appOut = null
            )
        )
    }

    @Test
    fun `MacFirewall check handles command failure gracefully as UNKNOWN without throwing`() {
        val firewall = MacFirewall(program = "/Applications/ChairPlay.app", runCmd = { null })
        assertEquals(FirewallStatus.UNKNOWN, firewall.status(null))
    }
}
