package io.github.numberonebot.chairplay.config

import io.github.numberonebot.chairplay.input.ControlsConfig
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
import org.junit.Test
import java.nio.file.Files
import java.nio.file.Path

/**
 * A file of the app's own that cannot be read stops the start with that file named: the message box said only
 * "Malformed \uxxxx encoding" or a NullPointerException's text, which named no file. window.properties, the app's own
 * record of where the windows were, is passed over instead.
 */
class DamagedFilesTest {
    private val dir: Path = Files.createTempDirectory("chairplay-damaged")

    /** A Windows path typed with single backslashes: \u then not four hexadecimal digits. */
    private val typedPath = "C:\\users\\me\\icon.png"

    @Test
    fun `a backslash u in the settings names the settings file`() {
        Files.writeString(dir.resolve("settings.properties"), "car.icon=$typedPath\n")

        val error = assertThrows(DamagedFileException::class.java) { DesktopConfig(dir) }

        assertEquals(dir.resolve("settings.properties"), error.file)
    }

    @Test
    fun `a backslash u in the controls names the controls file`() {
        Files.writeString(dir.resolve(ControlsConfig.FILE), "key.back=$typedPath\n")

        val error = assertThrows(DamagedFileException::class.java) { ControlsConfig.load(dir) }

        assertEquals(dir.resolve(ControlsConfig.FILE), error.file)
    }

    @Test
    fun `an identity with a key missing or not hexadecimal names the identity file`() {
        val file = dir.resolve("identity.properties")
        Files.writeString(file, "publicKey=00ff\npairingId=ChairPlay\n")
        assertEquals(file, assertThrows(DamagedFileException::class.java) { loadIdentity(dir) }.file)

        Files.writeString(file, "privateKey=not hex\npublicKey=00ff\npairingId=ChairPlay\n")
        assertEquals(file, assertThrows(DamagedFileException::class.java) { loadIdentity(dir) }.file)
    }

    @Test
    fun `window state that cannot be read leaves the windows at their defaults`() {
        val store = WindowPlacementStore(dir)
        store.save(WindowPlacement(x = 10, y = 20, width = 1600, height = 900, maximized = false), WindowSize(1280, 720))
        Files.writeString(dir.resolve("window.properties"), "note=$typedPath\n", java.nio.file.StandardOpenOption.APPEND)

        assertNull(store.load(WindowSize(1280, 720)))
    }
}
