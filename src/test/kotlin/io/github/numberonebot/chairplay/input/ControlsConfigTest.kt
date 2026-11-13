package io.github.numberonebot.chairplay.input

import io.github.numberonebot.chairplay.phone.KnobInput
import io.github.numberonebot.chairplay.phone.MediaKey
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.lwjgl.glfw.GLFW.*
import java.nio.file.Files
import java.nio.file.Path

class ControlsConfigTest {
    private fun dirWith(vararg lines: String): Path = Files.createTempDirectory("chairplay-controls").also {
        if (lines.isNotEmpty()) Files.writeString(it.resolve(ControlsConfig.FILE), lines.joinToString("") { line -> "$line\n" })
    }

    // The first start writes every action with its default keys, so the file documents itself.
    @Test
    fun `a missing file is written with the defaults and loads as them`() {
        val dir = dirWith()

        val loaded = ControlsConfig.load(dir)

        val text = Files.readString(dir.resolve(ControlsConfig.FILE))
        // key=value without spaces, as settings.properties writes it.
        for (action in ControlAction.entries) assertTrue("${action.key} missing", "\n${action.key}=" in text)
        assertTrue("no spaces around =", " = " !in text.lines().filterNot { it.startsWith("#") }.joinToString("\n"))
        assertEquals(ControlsConfig.DEFAULT.bindings, loaded.bindings)
        assertEquals(ControlsConfig.DEFAULT.bindings, ControlsConfig.load(dir).bindings)
        assertEquals(ControlAction.KNOB_PREVIOUS, loaded.bindings[KeyBinding(GLFW_KEY_TAB, GLFW_MOD_SHIFT)])
        assertEquals(ControlAction.KNOB_NEXT, loaded.bindings[KeyBinding(GLFW_KEY_TAB, 0)])
    }

    // The controls window labels each button with its first key, as the file names it.
    @Test
    fun `each action's first key is kept for the buttons' labels`() {
        assertEquals("Escape", ControlsConfig.DEFAULT.firstKey(ControlAction.BACK))
        assertEquals("Ctrl+Up", ControlsConfig.DEFAULT.firstKey(ControlAction.VOLUME_UP))
        val loaded = ControlsConfig.load(dirWith("key.back = Backspace, Escape", "key.siri ="))
        assertEquals("Backspace", loaded.firstKey(ControlAction.BACK))
        assertNull(loaded.firstKey(ControlAction.SIRI))
    }

    @Test
    fun `keys are named with modifiers, raw codes, or left empty to disable`() {
        val loaded = ControlsConfig.load(dirWith("key.siri = Ctrl+S, f6", "key.home =", "key.media.next = #290", "key.select = kp_enter"))

        assertEquals(ControlAction.SIRI, loaded.bindings[KeyBinding(GLFW_KEY_S, GLFW_MOD_CONTROL)])
        assertEquals(ControlAction.SIRI, loaded.bindings[KeyBinding(GLFW_KEY_F6, 0)])
        assertNull(loaded.bindings[KeyBinding(GLFW_KEY_S, 0)])
        assertTrue(ControlAction.HOME !in loaded.bindings.values)
        assertEquals(ControlAction.MEDIA_NEXT, loaded.bindings[KeyBinding(GLFW_KEY_F1, 0)])
        assertEquals(ControlAction.SELECT, loaded.bindings[KeyBinding(GLFW_KEY_KP_ENTER, 0)])
        assertNull("a listed action loses its other defaults", loaded.bindings[KeyBinding(GLFW_KEY_SPACE, 0)])
        assertEquals(ControlAction.BACK, loaded.bindings[KeyBinding(GLFW_KEY_ESCAPE, 0)])
    }

    // Whatever the file says, Escape goes back and Enter selects, so neither can leave the app without a way back.
    @Test
    fun `Escape always goes back and Enter always selects`() {
        val loaded = ControlsConfig.load(dirWith("key.back = Backspace", "key.select =", "key.home = Shift+Escape"))

        assertEquals(ControlAction.BACK, loaded.bindings[KeyBinding(GLFW_KEY_ESCAPE, 0)])
        assertEquals(ControlAction.SELECT, loaded.bindings[KeyBinding(GLFW_KEY_ENTER, 0)])
        assertEquals(ControlAction.HOME, loaded.bindings[KeyBinding(GLFW_KEY_ESCAPE, GLFW_MOD_SHIFT)])
        assertEquals(listOf("Escape", "Backspace"), loaded.keys(ControlAction.BACK))
        assertEquals(listOf("Enter"), loaded.keys(ControlAction.SELECT))
        assertEquals(listOf("Escape"), loaded.fixedKeys(ControlAction.BACK))
        assertEquals(listOf("Enter"), loaded.fixedKeys(ControlAction.SELECT))
        assertEquals(emptyList<String>(), loaded.fixedKeys(ControlAction.HOME))
    }

    // The keyboard's own media keys work whatever the file says, after the keys it names (owner, 2026-11-13).
    @Test
    fun `the media keys always play their actions`() {
        val loaded = ControlsConfig.load(dirWith("key.media.next = F5", "key.media.previous =", "key.media.playpause = MediaPlayPause, F2"))

        assertEquals(ControlAction.MEDIA_NEXT, loaded.bindings[KeyBinding(MediaKeys.NEXT, 0)])
        assertEquals(ControlAction.MEDIA_PREVIOUS, loaded.bindings[KeyBinding(MediaKeys.PREVIOUS, 0)])
        assertEquals(ControlAction.MEDIA_PLAY_PAUSE, loaded.bindings[KeyBinding(MediaKeys.PLAY_PAUSE, 0)])
        assertEquals(listOf("F5", "MediaNext"), loaded.keys(ControlAction.MEDIA_NEXT))
        assertEquals(listOf("MediaPrevious"), loaded.keys(ControlAction.MEDIA_PREVIOUS))
        assertEquals(listOf("MediaPlayPause", "F2"), loaded.keys(ControlAction.MEDIA_PLAY_PAUSE))
        assertEquals(listOf("MediaNext"), loaded.fixedKeys(ControlAction.MEDIA_NEXT))
        assertEquals("F9", ControlsConfig.DEFAULT.firstKey(ControlAction.MEDIA_NEXT))
        assertEquals(ControlsConfig.DEFAULT.keys(ControlAction.MEDIA_NEXT), listOf("F9", "MediaNext"))
        assertEquals(Control.Media(MediaKey.NEXT), KeyControls(loaded).key(MediaKeys.NEXT, GLFW_PRESS, 0))
    }

    @Test
    fun `a file that binds Escape or Enter to another action is refused, naming the setting`() {
        for (line in listOf("key.home = Escape", "key.siri = F2, enter", "key.siri = MediaNext")) {
            val message = runCatching { ControlsConfig.load(dirWith(line)) }.exceptionOrNull()?.message.orEmpty()
            assertTrue(message, line.substringBefore(" =") in message)
        }
    }

    @Test
    fun `mistakes stop the start with the setting named`() {
        val cases = mapOf(
            "key.siri = Hyper+S" to "key.siri",
            "key.siri = Banana" to "key.siri",
            "key.siri = F8" to "F8",
            "wheel.action = zoom" to "wheel.action",
            "wheel.invert = maybe" to "wheel.invert",
            "wheel.notches.per.step = 0" to "wheel.notches.per.step",
            "key.repeat.every = -1" to "key.repeat.every",
            "key.flyaway = Q" to "key.flyaway",
        )
        for ((line, named) in cases) {
            val error = runCatching { ControlsConfig.load(dirWith(line)) }.exceptionOrNull()
            assertTrue("$line was accepted", error is IllegalArgumentException)
            assertTrue("$line: ${error?.message}", named in error!!.message!!)
        }
    }

    @Test
    fun `the wheel can be inverted, slowed or turned off`() {
        val inverted = KeyControls(ControlsConfig.load(dirWith("wheel.invert = true")))
        assertEquals(Control.Knob(KnobInput(wheel = 1), true), inverted.scroll(1.0))

        val slow = KeyControls(ControlsConfig.load(dirWith("wheel.notches.per.step = 2")))
        assertNull(slow.scroll(-1.0))
        assertEquals(Control.Knob(KnobInput(wheel = 1), true), slow.scroll(-1.0))

        val off = KeyControls(ControlsConfig.load(dirWith("wheel.action = none")))
        assertNull(off.scroll(-3.0))
    }

    // A modifier let go before the key must not leave the knob tilted or the button held.
    @Test
    fun `a key released after its modifier still releases`() {
        val controls = KeyControls(ControlsConfig.load(dirWith("key.home = Shift+H")))

        assertEquals(Control.Knob(KnobInput(home = true), false), controls.key(GLFW_KEY_H, GLFW_PRESS, GLFW_MOD_SHIFT))
        assertEquals(Control.Knob(KnobInput(), false), controls.key(GLFW_KEY_H, GLFW_RELEASE, 0))
        assertNull(controls.key(GLFW_KEY_H, GLFW_PRESS, 0))
    }

    @Test
    fun `the repeat interval is configurable`() {
        val controls = KeyControls(ControlsConfig.load(dirWith("key.repeat.every = 1")))
        val sent = listOf(controls.key(GLFW_KEY_TAB, GLFW_PRESS, 0)) + (1..3).map { controls.key(GLFW_KEY_TAB, GLFW_REPEAT, 0) }
        assertEquals(listOf(true, true, true, true), sent.map { it != null })
    }

    // Zero gives one zoom step a press, as the zoom buttons on many steering wheels.
    @Test
    fun `the zoom repeat is configurable and zero turns it off`() {
        val controls = KeyControls(ControlsConfig.load(dirWith("key.zoom.repeat.every=0")))
        assertEquals(Control.MapZoom(1), controls.key(GLFW_KEY_KP_ADD, GLFW_PRESS, 0))
        assertTrue((1..40).all { controls.key(GLFW_KEY_KP_ADD, GLFW_REPEAT, 0) == null })
        assertEquals(10, ControlsConfig.DEFAULT.zoomRepeatEvery)
    }

    // A file written before an action existed may give its default key to another: the file's binding stays, and the
    // new action has no key until the file gives it one, rather than the start stopping.
    @Test
    fun `a default key the file gives to another action stays with it`() {
        val loaded = ControlsConfig.load(dirWith("key.siri = F10"))

        assertEquals(ControlAction.SIRI, loaded.bindings[KeyBinding(GLFW_KEY_F10, 0)])
        assertTrue(ControlAction.DRIVE !in loaded.bindings.values)
        assertEquals(emptyList<String>(), loaded.keys(ControlAction.DRIVE))
    }
}
