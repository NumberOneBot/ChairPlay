package io.github.numberonebot.chairplay.platform.windows

import io.github.numberonebot.chairplay.platform.NativeLibraries
import java.io.File
import java.nio.file.Files
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class WindowsWebViewTest {
    @Test
    fun `the web view's library is looked up where it was`() {
        val onPath = Files.createTempDirectory("webview-path").toFile()
        val exeDir = Files.createTempDirectory("webview-exe").toFile()
        val libraryPath = listOf(File("missing-folder"), onPath).joinToString(File.pathSeparator)
        val appPath = File(exeDir, "ChairPlay.exe").path
        val name = NativeLibraries.fileName("chairplay_webview")

        assertNull(WindowsWebView.library(libraryPath, appPath))

        File(exeDir, name).writeText("dll")
        assertEquals(NativeLibraries.find("chairplay_webview", NativeLibraries.searchDirs(libraryPath, appPath)),
            WindowsWebView.library(libraryPath, appPath))
        assertEquals(File(exeDir, name), WindowsWebView.library(libraryPath, appPath))

        File(onPath, name).writeText("dll")
        assertEquals(File(onPath, name), WindowsWebView.library(libraryPath, appPath))
    }

    @Test
    fun `the loader is taken from the library's folder`() {
        val dir = Files.createTempDirectory("webview-loader").toFile()
        val dll = File(dir, "chairplay_webview.dll")
        assertNull(WindowsWebView.loader(dll))

        File(dir, "WebView2Loader.dll").writeText("loader")
        assertEquals(File(dir, "WebView2Loader.dll"), WindowsWebView.loader(dll))
    }
}
