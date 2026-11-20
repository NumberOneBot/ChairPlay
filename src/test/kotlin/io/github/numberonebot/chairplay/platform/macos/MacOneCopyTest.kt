package io.github.numberonebot.chairplay.platform.macos

import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test
import java.nio.file.Files
import java.nio.file.Path

class MacOneCopyTest {
    @Test
    fun `first claim succeeds, second concurrent claim fails, third claim succeeds after closing first`() {
        val tempDir = Files.createTempDirectory("mac-one-copy-test")
        val oneCopy = MacOneCopy(lockDir = { tempDir })

        val first = oneCopy.claim("ChairPlay", 100)
        assertNotNull(first)

        val second = oneCopy.claim("ChairPlay", 50)
        assertNull(second)

        first!!.close()

        val third = oneCopy.claim("ChairPlay", 100)
        assertNotNull(third)
        third!!.close()
    }
}
