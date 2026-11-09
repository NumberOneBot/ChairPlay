package io.github.numberonebot.chairplay.platform.windows

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertThrows
import org.junit.Test
import java.io.FileOutputStream
import java.io.IOException
import java.nio.file.Files
import java.nio.file.StandardCopyOption
import java.util.concurrent.TimeUnit

class LockedFileTest {
    // An elevated netsh reads the script: nothing else of the user's may change, replace, or rename it meanwhile.
    @Test
    fun `the file cannot be changed, replaced, or moved while it is held`() {
        val held = withLockedFile("advfirewall firewall show rule name=none\r\n") { path ->
            assertThrows(IOException::class.java) { FileOutputStream(path.toFile()).close() }
            assertThrows(IOException::class.java) { Files.delete(path) }
            assertThrows(IOException::class.java) { Files.move(path, path.resolveSibling(path.fileName.toString() + ".moved"), StandardCopyOption.ATOMIC_MOVE) }
            path
        }

        assertFalse("the file is gone after", Files.exists(held))
    }

    /** Runs this PC's netsh on a held script (reading only, never changing a rule): it must still read it. */
    @Test
    fun `netsh reads the file while it is held`() {
        val netsh = System.getenv("SystemRoot").orEmpty().ifEmpty { "C:\\Windows" } + "\\System32\\netsh.exe"
        val exit = withLockedFile("interface show interface\r\n") { path ->
            val process = ProcessBuilder(netsh, "-f", path.toString()).redirectErrorStream(true)
                .redirectOutput(ProcessBuilder.Redirect.DISCARD).start()
            process.outputStream.close()
            check(process.waitFor(30, TimeUnit.SECONDS)) { "netsh did not end" }
            process.exitValue()
        }

        assertEquals(0, exit)
    }
}
