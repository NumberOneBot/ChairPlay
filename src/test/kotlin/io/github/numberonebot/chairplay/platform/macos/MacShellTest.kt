package io.github.numberonebot.chairplay.platform.macos

import io.github.numberonebot.chairplay.platform.DesktopShell
import io.github.numberonebot.chairplay.platform.SystemPage
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.nio.file.Path

class MacShellTest {
    @Test
    fun `dataDirectory returns CHAIRPLAY_HOME when set, otherwise Library Application Support ChairPlay`() {
        val originalProp = System.getProperty("chairplay.home")
        try {
            System.clearProperty("chairplay.home")
            val shell = MacShell()
            val expectedDefault = Path.of(System.getProperty("user.home"), "Library", "Application Support", "ChairPlay")
            assertEquals(expectedDefault, shell.dataDirectory())

            val custom = "/tmp/chairplay-custom-home"
            System.setProperty("chairplay.home", custom)
            assertEquals(Path.of(custom), shell.dataDirectory())
        } finally {
            if (originalProp != null) {
                System.setProperty("chairplay.home", originalProp)
            } else {
                System.clearProperty("chairplay.home")
            }
        }
    }

    @Test
    fun `parseSystemName formats sw_vers and arch`() {
        val swVersOut = "ProductName:\tmacOS\nProductVersion:\t27.2.1\nBuildVersion:\t26B131"
        assertEquals("macOS 27.2.1 (build 26B131, aarch64)", parseSystemName(swVersOut, "aarch64"))
    }

    @Test
    fun `openCommand textFileCommand and systemPage return expected commands`() {
        val shell = MacShell()
        assertEquals(listOf("/usr/bin/open", "https://example.com"), shell.openCommand("https://example.com"))

        val path = Path.of("/tmp/test.txt")
        assertEquals(listOf("/usr/bin/open", "-t", path.toString()), shell.textFileCommand(path))

        assertEquals("x-apple.systempreferences:com.apple.BluetoothSettings", shell.systemPage(SystemPage.BLUETOOTH))
    }
}
