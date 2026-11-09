package io.github.numberonebot.chairplay.platform

import io.github.numberonebot.chairplay.platform.macos.MacPlatform
import io.github.numberonebot.chairplay.platform.windows.WindowsPlatform
import java.lang.reflect.Modifier
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

class PlatformsTest {
    @Test
    fun `Windows names select the Windows platform`() {
        assertTrue(Platforms.select("Windows 11") is WindowsPlatform)
        assertTrue(Platforms.select("Windows 10") is WindowsPlatform)
    }

    @Test
    fun `Mac names select the Mac platform`() {
        assertTrue(Platforms.select("Mac OS X") is MacPlatform)
    }

    // The Mac platform compiles and is chosen, but nothing on it works yet: each use says so.
    @Test
    fun `the Mac platform refuses every use`() {
        val shell = MacPlatform().shell
        val uses = listOf<() -> Any?>(shell::systemName, shell::appsDark, shell::dataDirectory)
        for (use in uses) {
            val refused = assertThrows(UnsupportedOperationException::class.java) { use() }
            assertEquals("macOS support is not ready", refused.message)
        }
    }

    // main prints this and stops before any window or native load; a platform that works gives no refusal.
    @Test
    fun `an unknown system and a Mac are refused at start`() {
        assertTrue(Platforms.refusal { Platforms.select("Linux") }.orEmpty().contains("Linux"))
        assertEquals("macOS support is not ready", Platforms.refusal { Platforms.select("Mac OS X") })
        assertEquals(null, Platforms.refusal { FakePlatform() })
    }

    @Test
    fun `other systems are refused by name`() {
        val refused = assertThrows(UnsupportedPlatform::class.java) { Platforms.select("Linux") }
        assertTrue(refused.message, refused.message.orEmpty().contains("Linux"))
    }

    // Each adapter loads its DLLs as it is made: made at start, every one would load in every test and crash a Mac.
    @Test
    fun `the Windows platform loads nothing until asked`() {
        val platform = WindowsPlatform()
        val fields = WindowsPlatform::class.java.declaredFields.filter { !Modifier.isStatic(it.modifiers) }
        assertTrue("WindowsPlatform has no members", fields.isNotEmpty())
        for (field in fields) {
            field.isAccessible = true
            val value = field.get(platform)
            assertTrue("${field.name} is not lazy", value is Lazy<*>)
            assertFalse("${field.name} is made before it is asked for", (value as Lazy<*>).isInitialized())
        }
    }
}
