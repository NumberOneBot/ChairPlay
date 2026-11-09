package io.github.numberonebot.chairplay.input

import org.junit.Assert.*
import org.junit.Test
import org.lwjgl.glfw.GLFW.*

class GlobalKeyRouterTest {
    private val router = GlobalKeyRouter()
    private fun key(action: Int, background: Boolean = true, mods: Int = 0) =
        router.key(GLFW_KEY_F8, action, mods, background)

    @Test fun `local press cannot become a global repeat after focus changes`() {
        key(GLFW_PRESS, false)
        key(GLFW_REPEAT)
        key(GLFW_RELEASE)
        assertTrue(router.drain().isEmpty())
        key(GLFW_PRESS)
        assertEquals(GLFW_PRESS, router.drain().single().action)
    }

    @Test fun `release belongs to its original global press despite focus or modifiers`() {
        key(GLFW_PRESS, mods = GLFW_MOD_CONTROL)
        assertTrue(router.owns(GLFW_KEY_F8))
        key(GLFW_RELEASE, false)
        assertEquals(listOf(GLFW_PRESS, GLFW_RELEASE), router.drain().map { it.action })
        assertFalse(router.owns(GLFW_KEY_F8))
    }

    @Test fun `reset drops queued presses and releases only global holds`() {
        key(GLFW_PRESS)
        router.reset()
        assertTrue(router.owns(GLFW_KEY_F8))
        key(GLFW_REPEAT)
        assertEquals(listOf(GLFW_RELEASE), router.drain().map { it.action })
        key(GLFW_RELEASE)
        assertFalse(router.owns(GLFW_KEY_F8))
        key(GLFW_PRESS)
        assertEquals(GLFW_PRESS, router.drain().single().action)
    }

    @Test fun `queue overflow releases and cannot turn repeats into fresh presses`() {
        key(GLFW_PRESS)
        router.drain()
        repeat(2000) { key(GLFW_REPEAT) }
        val events = router.drain()
        assertTrue(events.size <= 128)
        assertEquals(GLFW_RELEASE, events.last().action)
        key(GLFW_REPEAT)
        assertTrue(router.drain().isEmpty())
    }

    @Test fun `release at a full queue and repeated resets cannot lose the final key up`() {
        key(GLFW_PRESS)
        router.drain()
        repeat(128) { key(GLFW_REPEAT) }
        key(GLFW_RELEASE)
        router.reset()
        router.reset()
        assertEquals(listOf(GLFW_RELEASE), router.drain().map { it.action })
    }

    @Test fun `Windows key and AltGr never activate ordinary shortcuts`() {
        key(GLFW_PRESS, mods = GLFW_MOD_SUPER)
        key(GLFW_RELEASE)
        key(GLFW_REPEAT)
        assertTrue(router.drain().isEmpty())
    }

    @Test fun `browser focus lease expires and cannot grow without bound`() {
        var time = 0L
        val lease = KeyboardFocusLease { time }
        lease.update("page", true)
        assertTrue(lease.active())
        time = 2_100_000_000L
        assertFalse(lease.active())
        assertTrue(lease.update("page", true))
        assertFalse(lease.update("page", true))
        lease.update("page", false)
        assertFalse(lease.active())
    }
}
