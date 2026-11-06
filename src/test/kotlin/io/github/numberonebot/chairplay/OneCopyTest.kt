package io.github.numberonebot.chairplay

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.UUID

class OneCopyTest {
    private val name = "Local\\ChairPlay-test-${UUID.randomUUID()}"

    // The installed copy and a portable one lock their own folders: the system-wide name keeps the second from
    // running while the first does, and lets it once the first is gone.
    @Test fun `a second copy cannot claim the name while the first holds it`() {
        val first = OneCopy.claim(name, timeoutMillis = 0)
        assertTrue(first != null)
        assertTrue(OneCopy.claim(name, timeoutMillis = 300) == null)
        first!!.close()
        val second = OneCopy.claim(name, timeoutMillis = 0)
        assertTrue(second != null)
        second!!.close()
    }

    // A copy of the same folder that is still closing lets go of the name within the wait: the new one runs.
    @Test fun `the claim waits for a copy that is going`() {
        val going = OneCopy.claim(name, timeoutMillis = 0)!!
        Thread { Thread.sleep(200); going.close() }.start()
        val next = OneCopy.claim(name, timeoutMillis = 3000)
        assertTrue(next != null)
        next!!.close()
        assertFalse(OneCopy.claim(name, timeoutMillis = 0) == null)
    }
}
