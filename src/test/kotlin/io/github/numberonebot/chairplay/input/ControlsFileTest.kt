package io.github.numberonebot.chairplay.input

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.lwjgl.glfw.GLFW.*
import java.nio.file.Files
import java.nio.file.Path

class ControlsFileTest {
    private val dir: Path = Files.createTempDirectory("chairplay-controls-file")
    private val path: Path = dir.resolve(ControlsConfig.FILE)

    /** The file as the first start writes it, unless [text] is given. */
    private fun file(text: String? = null): ControlsFile {
        if (text == null) ControlsConfig.load(dir) else Files.writeString(path, text)
        return ControlsFile(dir)
    }

    private fun saved(result: ControlsSave) = result as? ControlsSave.Saved ?: error("refused: ${(result as ControlsSave.Refused).message}")

    @Test
    fun `a change edits its own line and leaves the comments and the rest alone`() {
        val controls = file()
        val before = Files.readString(path)

        val result = saved(controls.save(mapOf("key.home" to "Home, F6")))

        val after = Files.readString(path)
        assertEquals(before.replace("key.home=Home\n", "key.home=Home, F6\n"), after)
        assertEquals(ControlAction.HOME, result.config.bindings[KeyBinding(GLFW_KEY_F6, 0)])
        assertEquals(mapOf("key.home" to "Home, F6"), result.changes)
    }

    // A key bound elsewhere moves at once: the action it came from loses it in the same write.
    @Test
    fun `a key taken from another action leaves it`() {
        val controls = file()

        val result = saved(controls.save(mapOf("key.home" to "F3")))

        assertEquals(mapOf("key.home" to "F3", "key.call.answer" to ""), result.changes)
        assertEquals(ControlAction.HOME, result.config.bindings[KeyBinding(GLFW_KEY_F3, 0)])
        assertTrue("\nkey.call.answer=\n" in Files.readString(path))
    }

    @Test
    fun `an action keeps its other keys when one is taken`() {
        val controls = file()

        val result = saved(controls.save(mapOf("key.siri" to "Space")))

        assertEquals("Enter, KP_Enter", result.changes["key.select"])
        assertEquals(listOf("Enter", "KP_Enter"), result.config.keys(ControlAction.SELECT))
    }

    @Test
    fun `a setting the file lacks is added at its end`() {
        val controls = file("# mine\nkey.home=Home\n")

        saved(controls.save(mapOf("key.siri" to "S, F2")))

        assertEquals("# mine\nkey.home=Home\nkey.siri=S, F2\n", Files.readString(path))
    }

    @Test
    fun `a change the start would refuse is refused, and the file stays`() {
        val controls = file()
        val before = Files.readString(path)

        for ((key, value) in listOf("key.home" to "Escape", "key.home" to "Nope", "wheel.action" to "sideways")) {
            val refused = controls.save(mapOf(key to value)) as ControlsSave.Refused
            assertEquals(key, refused.key)
        }
        assertEquals(before, Files.readString(path))
    }

    @Test
    fun `the wheel and the repeat are saved as the others`() {
        val controls = file()

        val result = saved(controls.save(mapOf("wheel.action" to "none", "key.repeat.every" to "5")))

        assertEquals(WheelAction.NONE, result.config.wheel)
        assertEquals(5, result.config.repeatEvery)
    }
}
