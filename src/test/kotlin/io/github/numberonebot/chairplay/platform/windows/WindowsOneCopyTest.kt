package io.github.numberonebot.chairplay.platform.windows

import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assume.assumeTrue
import org.junit.Test
import java.util.UUID

class WindowsOneCopyTest {
    // The shared code names the claim "ChairPlay"; on Windows it is the kernel object in the user's session, Local\<name>,
    // which a running 1.0.9 also takes.
    @Test
    fun `a claim by name holds the kernel object in the session namespace`() {
        assumeTrue("Windows only: WindowsPlatform oneCopy uses Win32 named mutex", System.getProperty("os.name").startsWith("Windows"))
        val name = "ChairPlay-test-${UUID.randomUUID()}"
        val held = WindowsPlatform().oneCopy.claim(name, 0)
        try {
            assertNotNull(held)
            assertNull(NamedMutex.claim("Local\\$name", 0))
        } finally {
            held?.close()
        }
    }
}
