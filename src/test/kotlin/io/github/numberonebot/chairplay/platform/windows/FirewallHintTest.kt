package io.github.numberonebot.chairplay.platform.windows

import org.junit.Assert.assertEquals
import org.junit.Test

/** The commands that add the rule, pinned as today's code writes them (netsh syntax, not wording). */
class FirewallHintTest {
    private val program = "C:\\Apps\\ChairPlay\\ChairPlay.exe"
    private val rule = "netsh advfirewall firewall add rule name=\"ChairPlay CarPlay inbound\" dir=in action=allow " +
        "program=\"C:\\Apps\\ChairPlay\\ChairPlay.exe\""
    private val withoutUi = "$rule protocol=TCP localport=7000-7010,49152-65535\n" +
        "$rule protocol=UDP localport=49152-65535\n" +
        "$rule protocol=UDP localport=5353"

    @Test
    fun `the commands for port 7000 are today's`() {
        assertEquals(withoutUi, WindowsFirewall(program).hint(7000, null))
        assertEquals("$withoutUi\n$rule protocol=TCP localport=7529", WindowsFirewall(program).hint(7000, 7529))
        assertEquals(withoutUi.replace("netsh ", "").replace("\n", "\r\n"), netshScript(WindowsFirewall(program).hint(7000, null)))
    }
}
