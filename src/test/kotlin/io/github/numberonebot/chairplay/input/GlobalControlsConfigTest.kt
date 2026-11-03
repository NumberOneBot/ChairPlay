package io.github.numberonebot.chairplay.input

import org.junit.Assert.*
import org.junit.Test
import java.nio.file.Files

class GlobalControlsConfigTest {
    @Test fun `global mode saves without replacing any existing bindings`() {
        val dir = Files.createTempDirectory("global-controls")
        val file = ControlsFile(dir)
        val before = file.read()
        for (enabled in listOf("true", "false")) {
            val result = file.save(mapOf("global.enabled" to enabled))
            assertTrue(result is ControlsSave.Saved)
            assertEquals(before.bindings, file.read().bindings)
            assertEquals(enabled, java.util.Properties().apply {
                Files.newBufferedReader(dir.resolve(ControlsConfig.FILE)).use(::load)
            }.getProperty("global.enabled"))
        }
    }

    @Test fun `invalid global mode is refused without writing the file`() {
        val dir = Files.createTempDirectory("global-controls-invalid")
        val file = ControlsFile(dir)
        file.read()
        val before = Files.readString(dir.resolve(ControlsConfig.FILE))
        assertTrue(file.save(mapOf("global.enabled" to "yes")) is ControlsSave.Refused)
        assertEquals(before, Files.readString(dir.resolve(ControlsConfig.FILE)))
    }
}
