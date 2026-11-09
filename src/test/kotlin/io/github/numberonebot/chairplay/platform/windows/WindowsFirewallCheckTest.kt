package io.github.numberonebot.chairplay.platform.windows

import io.github.numberonebot.chairplay.log.AppLog
import io.github.numberonebot.chairplay.log.LogLevel
import io.github.numberonebot.chairplay.ui.FirewallStatus
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.nio.file.Files
import java.nio.file.Path
import java.time.LocalDateTime
import kotlin.io.path.listDirectoryEntries
import kotlin.io.path.readText

class FirewallCheckTest {
    private val dir: Path = Files.createTempDirectory("chairplay-firewall")
    private val program = "C:\\Apps\\ChairPlay\\ChairPlay.exe"

    // A release logs at warning: what keeps the iPhone out must still reach its log.
    private fun loggedAtWarning(state: FirewallState?, uiPort: Int? = null): Pair<FirewallStatus, String> {
        val log = AppLog(clock = { LocalDateTime.of(2026, 10, 25, 12, 0, 0) }).apply { level = LogLevel.WARNING }
        log.open(dir)
        val status = WindowsFirewall(program, read = { _, _ -> state }).check(7000, uiPort, log)
        log.close()
        return status to dir.listDirectoryEntries("chairplay-*.log").single().readText()
    }

    @Test
    fun `a missing rule logs the commands that add it at a release's level`() {
        val (status, text) = loggedAtWarning(FirewallState(allowRule = false, blocking = emptyList()))

        assertEquals(FirewallStatus.MISSING, status)
        assertTrue(text, "netsh advfirewall firewall add rule" in text && program in text)
    }

    @Test
    fun `a block rule is logged at a release's level`() {
        val (status, text) = loggedAtWarning(FirewallState(allowRule = true, blocking = listOf("ChairPlay (Domain)")))

        assertEquals(FirewallStatus.BLOCKED, status)
        assertTrue(text, "ChairPlay (Domain)" in text)
    }

    @Test
    fun `rules that cannot be read log the commands at a release's level`() {
        val (status, text) = loggedAtWarning(null)

        assertEquals(FirewallStatus.UNKNOWN, status)
        assertTrue(text, "netsh advfirewall firewall add rule" in text)
    }

    @Test
    fun `with the network on, the logged commands open the UI's port`() {
        val (_, text) = loggedAtWarning(FirewallState(allowRule = false, blocking = emptyList()), uiPort = 7529)

        assertTrue(text, "protocol=TCP localport=7529" in text)
    }

    @Test
    fun `our rule without the UI's port is reported apart, with the commands that add it again`() {
        val state = FirewallState(allowRule = false, blocking = emptyList(), uiPortMissing = true)
        val (status, text) = loggedAtWarning(state, uiPort = 7529)

        assertEquals(FirewallStatus.PARTIAL, status)
        assertTrue(text, "protocol=TCP localport=7529" in text)
    }
}
