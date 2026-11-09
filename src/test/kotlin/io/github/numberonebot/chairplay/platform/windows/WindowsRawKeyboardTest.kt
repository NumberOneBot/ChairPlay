package io.github.numberonebot.chairplay.platform.windows

import com.sun.jna.Platform
import io.github.numberonebot.chairplay.input.GlobalKeyboardCallbacks
import org.junit.Assert.*
import org.junit.Assume.assumeTrue
import org.junit.Test
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicReference

class WindowsRawKeyboardTest {
    @Test fun `real Windows registration starts stops and can start again without a hook`() {
        assumeTrue(Platform.isWindows())
        assertNull(WindowsRawApi.keyboardTarget())
        repeat(2) {
            val started = CountDownLatch(1)
            val failure = AtomicReference<String?>()
            val source = WindowsRawKeyboard(GlobalKeyboardCallbacks({ _, _, _, _ -> }, {}, { started.countDown() }, {
                failure.set(it); started.countDown()
            }))
            try {
                source.start()
                assertTrue(started.await(5, TimeUnit.SECONDS))
                assertNull(failure.get(), failure.get())
                assertNotNull(WindowsRawApi.keyboardTarget())
            } finally { source.close() }
            assertNull(WindowsRawApi.keyboardTarget())
        }
    }
}
