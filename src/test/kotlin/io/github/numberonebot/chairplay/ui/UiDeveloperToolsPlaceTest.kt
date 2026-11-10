package io.github.numberonebot.chairplay.ui

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.nio.file.Files

/** Experiments' Developer tools folder (A6, V33, V34) and the wizard's Apple Devices link. */
class UiDeveloperToolsPlaceTest : UiControllerFixture() {
    private val store get() = dir.resolve("cache").resolve("developer-tools")

    @Test
    fun `no published package, no Developer tools folder, and none made by asking`() {
        assertNull(controller().folderSizes()["developerTools"])
        assertFalse(Files.exists(store))

        // A download that never finished publishes nothing.
        Files.createDirectories(store.resolve("27.2-partial"))
        Files.write(store.resolve("27.2-partial").resolve("image.dmg"), ByteArray(700))
        assertNull(controller().folderSizes()["developerTools"])
    }

    @Test
    fun `a published package shows the store's real size`() {
        val ready = Files.createDirectories(store.resolve("ios-17-plus"))
        Files.write(ready.resolve("image.dmg"), ByteArray(1000))
        Files.writeString(ready.resolve("manifest.json"), "{}")
        Files.write(Files.createDirectories(store.resolve("other")).resolve("part"), ByteArray(24))

        assertEquals(1000L + 2 + 24, controller().folderSizes()["developerTools"])
    }

    @Test
    fun `the Developer tools folder opens in File Explorer`() {
        val ui = controller()

        assertTrue(ui.open("developer-tools"))

        assertEquals(listOf(listOf("explorer.exe", store.toString())), launched)
    }

    @Test
    fun `Get Apple Devices opens its Microsoft Store page`() {
        assertTrue(controller().open("apple-devices"))

        assertTrue(launched.single().last().endsWith("https://apps.microsoft.com/detail/9NP83LWLPZ9K"))
    }
}
