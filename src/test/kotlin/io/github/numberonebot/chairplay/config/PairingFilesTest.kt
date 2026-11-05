package io.github.numberonebot.chairplay.config

import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.nio.file.Files
import java.util.HexFormat

class PairingFilesTest {
    @get:Rule val temporary = TemporaryFolder()
    private val hex = HexFormat.of()
    private val key = ByteArray(32) { (it + 1).toByte() }

    @Test
    fun `damaged pairing does not block valid pairings or startup`() {
        val dir = temporary.root.toPath()
        val pairings = Files.createDirectory(dir.resolve("pairings"))
        val good = pairings.resolve(hex.formatHex("good".toByteArray()))
        Files.writeString(good, hex.formatHex(key))
        val broken = pairings.resolve(hex.formatHex("broken".toByteArray()))
        val damaged = ByteArray(64)
        Files.write(broken, damaged)

        val config = DesktopConfig(dir)

        assertArrayEquals(key, config.pairings.get("good"))
        assertNull(config.pairings.get("broken"))
        assertTrue(config.iphoneKnown())
        assertFalse(Files.exists(broken))
        val preserved = Files.walk(dir.resolve("logs")).use { paths ->
            paths.filter { Files.isRegularFile(it) }.toList()
        }
        assertEquals(1, preserved.size)
        assertArrayEquals(damaged, Files.readAllBytes(preserved.single()))
    }

    @Test
    fun `invalid records do not count as a known iphone`() {
        for (contents in listOf("", "0", "gg", "00", "00".repeat(33), "\u0000".repeat(64))) {
            val dir = temporary.newFolder().toPath()
            val pairings = Files.createDirectory(dir.resolve("pairings"))
            Files.writeString(pairings.resolve(hex.formatHex("phone".toByteArray())), contents)
            val config = DesktopConfig(dir)
            assertNull(config.pairings.get("phone"))
            assertFalse(config.iphoneKnown())
        }
    }

    @Test
    fun `bad filenames and non utf8 records do not block loading`() {
        val dir = temporary.root.toPath()
        val pairings = Files.createDirectory(dir.resolve("pairings"))
        Files.writeString(pairings.resolve("not-hex"), hex.formatHex(key))
        Files.write(pairings.resolve("70686f6e65"), byteArrayOf(0xff.toByte()))
        assertNull(loadPairings(dir).get("phone"))
        assertEquals(0L, Files.list(pairings).use { it.count() })
    }

    @Test
    fun `saving and replacing a pairing persists a complete key`() {
        val dir = temporary.root.toPath()
        val store = loadPairings(dir)
        store.save("phone", key)
        assertArrayEquals(key, loadPairings(dir).get("phone"))
        val replacement = key.reversedArray()
        store.save("phone", replacement)
        assertArrayEquals(replacement, loadPairings(dir).get("phone"))
        assertEquals(1L, Files.list(dir.resolve("pairings")).use { it.count() })
    }
}
