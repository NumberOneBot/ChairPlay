package io.github.numberonebot.chairplay

import io.github.numberonebot.chairplay.input.*
import io.github.numberonebot.chairplay.platform.FakePlatform
import org.junit.Assert.*
import org.junit.Test
import org.lwjgl.glfw.GLFW.*

class AppGlobalKeysTest {
    private var config = ControlsConfig.parse("global.enabled=true")
    private var session: Any? = Any()
    private val sent = mutableListOf<Control>()
    private val driven = mutableListOf<Pair<Int, Int>>()
    private lateinit var callbacks: GlobalKeyboardCallbacks
    private var starts = 0
    private var closes = 0
    private var driveMode = false
    private val app = AppGlobalKeys({ config }, { session }, { _, control, target -> if (target === session) control?.let(sent::add); Unit },
        { key, phase, _ -> driveMode.also { if (it) driven += key to phase } }, {}, { listener ->
            callbacks = listener
            object : GlobalKeyboardSource {
                override fun start() { starts++; callbacks.ready() }
                override fun close() { closes++; callbacks.reset() }
            }
        })
    private fun key(key: Int, phase: Int, mods: Int = 0, background: Boolean = true) = callbacks.key(key, phase, mods, background)

    @Test fun `existing bindings execute once and release across focus and modifier changes`() {
        app.pass()
        key(GLFW_KEY_KP_ENTER, GLFW_PRESS)
        app.pass()
        assertTrue((sent.single() as Control.Knob).input.select)
        key(GLFW_KEY_KP_ENTER, GLFW_REPEAT, GLFW_MOD_SHIFT, false)
        key(GLFW_KEY_KP_ENTER, GLFW_RELEASE, 0, false)
        app.pass()
        assertEquals(2, sent.size)
        assertFalse((sent.last() as Control.Knob).input.select)
    }

    // Enter and Escape, the keys pinned to Select and Back, act only in ChairPlay's windows (owner, 2026-11-11).
    @Test fun `the pinned Enter and Escape never act from another app`() {
        app.pass()
        for (pinned in listOf(GLFW_KEY_ENTER, GLFW_KEY_ESCAPE)) {
            for (mods in listOf(0, GLFW_MOD_SHIFT)) {
                key(pinned, GLFW_PRESS, mods)
                assertFalse(app.owns(pinned))
                key(pinned, GLFW_RELEASE, mods)
            }
        }
        app.pass()
        assertTrue(sent.isEmpty())
        key(GLFW_KEY_KP_ENTER, GLFW_PRESS)
        app.pass()
        assertTrue((sent.single() as Control.Knob).input.select)
    }

    @Test fun `switch off releases a hold and unregisters without another action`() {
        app.pass()
        key(GLFW_KEY_KP_ENTER, GLFW_PRESS)
        app.pass()
        config = ControlsConfig.DEFAULT
        assertNull(app.pass())
        assertEquals(1, starts)
        assertEquals(1, closes)
        assertFalse((sent.last() as Control.Knob).input.select)
        key(GLFW_KEY_F8, GLFW_PRESS)
        app.pass()
        assertEquals(2, sent.size)
    }

    @Test fun `binding edits while held release old action and wait for a fresh press`() {
        app.pass()
        key(GLFW_KEY_SPACE, GLFW_PRESS)
        app.pass()
        config = ControlsConfig.parse("global.enabled=true\nkey.select=Enter\nkey.siri=Space")
        app.pass()
        key(GLFW_KEY_SPACE, GLFW_REPEAT)
        app.pass()
        assertEquals(2, sent.size)
        key(GLFW_KEY_SPACE, GLFW_RELEASE)
        key(GLFW_KEY_SPACE, GLFW_PRESS)
        app.pass()
        assertEquals(Control.Siri, sent.last())
    }

    @Test fun `disconnect discards queued controls and releases cannot reach the next session`() {
        app.pass()
        key(GLFW_KEY_KP_ENTER, GLFW_PRESS)
        app.pass()
        key(GLFW_KEY_F8, GLFW_PRESS)
        session = Any()
        app.pass()
        key(GLFW_KEY_KP_ENTER, GLFW_RELEASE)
        app.pass()
        assertEquals(1, sent.size)
    }

    @Test fun `focused settings and capture inhibit background input and release current holds`() {
        app.pass()
        key(GLFW_KEY_KP_ENTER, GLFW_PRESS)
        app.pass()
        app.focus("browser", true)
        app.pass()
        key(GLFW_KEY_F8, GLFW_PRESS)
        app.pass()
        assertEquals(2, sent.size)
        app.focus("browser", false)
        key(GLFW_KEY_F8, GLFW_REPEAT)
        app.pass()
        assertEquals(2, sent.size)
    }

    // Each key and reset wakes the loop itself: a pass never asks for another on a timer.
    @Test fun `global shortcuts on ask for no periodic pass`() {
        assertNull(app.pass())
        key(GLFW_KEY_F8, GLFW_PRESS)
        assertNull(app.pass())
        assertEquals(1, sent.size)
    }

    @Test fun `a renewed focus lease does not wake the loop again`() {
        var wakes = 0
        app.wake = { wakes++ }
        app.pass()
        app.focus("browser", true)
        assertEquals(1, wakes)
        repeat(3) { app.focus("browser", true) }
        assertEquals(1, wakes)
        app.focus("browser", false)
        app.focus("browser", true)
        assertEquals(2, wakes)
    }

    @Test fun `global drive arrows release on suspend and close`() {
        driveMode = true
        app.pass()
        key(GLFW_KEY_UP, GLFW_PRESS)
        app.pass()
        callbacks.reset()
        app.pass()
        assertEquals(listOf(GLFW_KEY_UP to GLFW_PRESS, GLFW_KEY_UP to GLFW_RELEASE), driven)
        assertTrue(sent.isEmpty())
        app.close()
        assertEquals(1, closes)
    }

    @Test fun `native errors remain visible until the mode is disabled`() {
        app.pass()
        callbacks.failed("registration failed")
        assertEquals(false, app.state()["enabled"])
        assertNotNull(app.state()["error"])
        config = ControlsConfig.DEFAULT
        app.pass()
        assertNull(app.state()["error"])
    }

    @Test fun `an overflowing native source is stopped with its hold released and an error`() {
        app.pass()
        key(GLFW_KEY_KP_ENTER, GLFW_PRESS)
        app.pass()
        repeat(2000) { key(GLFW_KEY_KP_ENTER, GLFW_REPEAT) }
        app.pass()
        assertFalse((sent.last() as Control.Knob).input.select)
        assertEquals(1, closes)
        assertNotNull(app.state()["error"])
        assertEquals(false, app.state()["enabled"])
    }

    @Test fun `events from a retired native source cannot activate shortcuts after restart`() {
        app.pass()
        val retired = callbacks
        config = ControlsConfig.DEFAULT
        app.pass()
        config = ControlsConfig.parse("global.enabled=true")
        app.pass()
        retired.key(GLFW_KEY_F8, GLFW_PRESS, 0, true)
        retired.failed("old source")
        app.pass()
        assertTrue(sent.isEmpty())
        assertNull(app.state()["error"])
        key(GLFW_KEY_F8, GLFW_PRESS)
        app.pass()
        assertTrue(sent.single() is Control.Media)
    }

    @Test fun `the global keys start the platform's source`() {
        val platform = FakePlatform()
        val platformApp = AppGlobalKeys({ config }, { session }, { _, _, _ -> }, { _, _, _ -> false }, {}, platform::globalKeyboard)
        platformApp.pass()
        platformApp.pass()
        val source = platform.keyboard.single()
        assertEquals(1, source.starts)
        assertEquals(0, source.closes)
        platformApp.close()
        assertEquals(1, source.closes)
    }
}
