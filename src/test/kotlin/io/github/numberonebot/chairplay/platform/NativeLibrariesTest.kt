package io.github.numberonebot.chairplay.platform

import java.io.File
import java.nio.file.Files
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class NativeLibrariesTest {
    @Test
    fun `searchDirs lists the library path, then the exe's folder`() {
        val exe = File("C:\\X\\ChairPlay.exe")
        val dirs = NativeLibraries.searchDirs("a${File.pathSeparator}${File.pathSeparator}b", exe.path)

        assertEquals(listOf(File("a"), File("b"), exe.parentFile), dirs)
    }

    @Test
    fun `searchDirs copes with no library path and no exe`() {
        assertEquals(emptyList<File>(), NativeLibraries.searchDirs(null, null))
    }

    @Test
    fun `find takes the first folder holding the file`() {
        val first = Files.createTempDirectory("native-first").toFile()
        val second = Files.createTempDirectory("native-second").toFile()
        val empty = Files.createTempDirectory("native-empty").toFile()
        val name = NativeLibraries.fileName("chairplay_probe")
        File(first, name).writeText("1")
        File(second, name).writeText("2")

        assertEquals(File(first, name), NativeLibraries.find("chairplay_probe", listOf(empty, first, second)))
        assertNull(NativeLibraries.find("chairplay_probe", listOf(empty)))
    }

    @Test
    fun `fileName is the system's`() {
        assertEquals(System.mapLibraryName("chairplay_audio"), NativeLibraries.fileName("chairplay_audio"))
    }
}
