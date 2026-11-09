package io.github.numberonebot.chairplay.ui

import io.github.numberonebot.chairplay.network.EthernetAdapter
import io.github.numberonebot.chairplay.network.WifiHiddenException
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class SetupChecksTest {
    private var iphone: String? = null
    private var wifi: () -> String? = { null }
    private var firewall = FirewallStatus.MISSING
    private var mfi = false
    private val reads = mutableListOf<String>()

    private fun checks(
        start: FirewallStatus = FirewallStatus.MISSING,
        pairedIphone: () -> String? = { reads += "iphone"; iphone },
        ethernet: () -> List<EthernetAdapter> = { reads += "ethernet"; emptyList() },
        log: (String) -> Unit = {},
    ) = SetupChecks(
        pairedIphone = pairedIphone,
        pcWifi = { reads += "wifi"; wifi() },
        firewall = { reads += "firewall"; firewall },
        startFirewall = start,
        mfiPresent = { reads += "mfi"; mfi },
        ethernet = ethernet,
        log = log,
    )

    private fun firewallReads() = reads.count { it == "firewall" }

    // The checks read Windows only when a page asks (as it opens, as the window has the focus back, after Add the rule),
    // never on their own.
    @Test
    fun `nothing is read until a page asks, then everything once`() {
        val checks = checks()
        assertEquals(emptyList<String>(), reads)

        checks.check()

        assertEquals(listOf("firewall", "wifi", "ethernet", "iphone", "mfi").sorted(), reads.sorted())
    }

    // The start reads the firewall after the windows open, not before: its answer takes the place of Not checked at once,
    // without a read of its own.
    @Test
    fun `the firewall read after the start replaces the start's unknown at once`() {
        val checks = checks(start = FirewallStatus.UNKNOWN)

        assertTrue(checks.firewallChecked(FirewallStatus.MISSING))
        assertEquals(FirewallStatus.MISSING, checks.snapshot.firewall)
        assertFalse(checks.firewallChecked(FirewallStatus.MISSING))
        assertEquals(0, firewallReads())
    }

    // Walking the rules takes about a second: a page opened while the start's walk is under way leaves it to that one.
    @Test
    fun `a check leaves the firewall to the start's read while that one is under way`() {
        val checks = checks(start = FirewallStatus.UNKNOWN)

        checks.check()
        assertEquals(0, firewallReads())

        checks.firewallChecked(FirewallStatus.MISSING)
        checks.check()
        assertEquals(1, firewallReads())
    }

    // Get started says ChairPlay finds the MFi files on its own: a check must notice them arrive, as it does a pairing.
    @Test
    fun `a check notices the MFi files arrive and go`() {
        val checks = checks()
        checks.check()
        assertFalse(checks.snapshot.mfiPresent)

        mfi = true
        assertTrue(checks.check())
        assertTrue(checks.snapshot.mfiPresent)
        assertFalse(checks.check())

        mfi = false
        assertTrue(checks.check())
        assertFalse(checks.snapshot.mfiPresent)
    }

    @Test
    fun `a check reads the paired iPhone and the PC's Wi-Fi, and says whether anything changed`() {
        val checks = checks()
        assertEquals(SetupSnapshot(null, null, FirewallStatus.MISSING), checks.snapshot)

        iphone = "Kate's iPhone"
        wifi = { "ChairPlay-5G" }
        assertTrue(checks.check())
        assertEquals(SetupSnapshot("Kate's iPhone", "ChairPlay-5G", FirewallStatus.MISSING), checks.snapshot)

        assertFalse(checks.check())
    }

    // A rule added in the firewall's console is seen when the user comes back; once it is there, it is not read again.
    @Test
    fun `each check reads the firewall while it is wrong, and no more once it is right`() {
        val checks = checks()

        checks.check()
        assertEquals(1, firewallReads())

        firewall = FirewallStatus.PRESENT
        assertTrue(checks.check())
        assertEquals(2, firewallReads())
        assertEquals(FirewallStatus.PRESENT, checks.snapshot.firewall)

        checks.check()
        assertEquals("not once the rule is there", 2, firewallReads())
    }

    @Test
    fun `a firewall right at the start is not read again`() {
        for (start in listOf(FirewallStatus.PRESENT, FirewallStatus.OFF)) {
            reads.clear()
            checks(start = start).check()

            assertEquals(start.name, 0, firewallReads())
        }
    }

    // Windows hiding the PC's Wi-Fi network is told apart from no Wi-Fi, for the page to say how to show it; a cable is
    // still looked for, and the hiding is logged once.
    @Test
    fun `a Wi-Fi network Windows hides is told apart from none`() {
        var hidden = true
        val logged = mutableListOf<String>()
        wifi = { if (hidden) throw WifiHiddenException() else "Home" }
        val checks = checks(log = { logged += it })
        assertTrue(checks.check())
        assertTrue(checks.snapshot.wifiHidden)
        assertNull(checks.snapshot.pcWifi)
        assertEquals(1, reads.count { it == "ethernet" })
        assertEquals(true, networkSetupFields(checks.snapshot, "").toMap()["wifiHidden"])
        assertFalse(checks.check())
        assertEquals(1, logged.size)

        hidden = false
        assertTrue(checks.check())
        assertFalse(checks.snapshot.wifiHidden)
        assertEquals("Home", checks.snapshot.pcWifi)
    }

    @Test
    fun `a reader that fails reads as nothing found`() {
        val checks = checks(pairedIphone = { error("no Bluetooth radio") })
        wifi = { "Home" }

        assertTrue(checks.check())

        assertEquals(SetupSnapshot(null, "Home", FirewallStatus.MISSING), checks.snapshot)
    }
}
