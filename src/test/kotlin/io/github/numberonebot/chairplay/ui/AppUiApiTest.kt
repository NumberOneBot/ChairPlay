package io.github.numberonebot.chairplay.ui

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.nio.file.Files

class AppUiApiTest : UiControllerFixture() {
    @Test fun `About requests the app update check and reports when it is busy`() {
        var calls = 0
        var accepted = true
        val api = AppUiApi(controller(), { calls++; accepted }) { null }
        assertEquals(200, postRoute(api, "check-updates", emptyMap<String, Any>()).first)
        accepted = false
        assertEquals(409, postRoute(api, "check-updates", emptyMap<String, Any>()).first)
        assertEquals(2, calls)
    }

    // A page that opens, or has the focus back, asks for the setup to be read; the pages hear of what changed.
    @Test
    fun `a page's setup check reads the PC and tells the pages of a change`() {
        var reads = 0
        val checks = SetupChecks({ reads++; "Kate's iPhone" }, { null }, { FirewallStatus.MISSING }, FirewallStatus.MISSING)
        val ui = controller(checks = checks)
        val api = AppUiApi(ui, checks = checks) { null }
        val before = ui.version

        assertEquals(200, postRoute(api, "setup-check", emptyMap<String, Any>()).first)
        assertEquals(1, reads)
        assertEquals("Kate's iPhone", (ui.state()["setup"] as Map<*, *>)["pairedIphone"])
        assertTrue(ui.version > before)

        val after = ui.version
        api.checkSetup()
        assertEquals("nothing changed", after, ui.version)
    }

    // The wrapper once lacked the forwarding method and answered the default, an empty map.
    @Test
    fun `the folder sizes come through the app's API from the real folders`() {
        Files.createDirectories(dir.resolve("logs"))
        Files.write(dir.resolve("logs").resolve("chairplay.log"), ByteArray(1500))

        assertEquals(1500L, AppUiApi(controller()) { null }.folderSizes()["logs"])
    }

    @Test
    fun `every call of the UI API is forwarded, not left to its default body`() {
        val missing = UiApi::class.java.methods.filter { !it.isSynthetic && !java.lang.reflect.Modifier.isStatic(it.modifiers) }.filter { method ->
            runCatching { AppUiApi::class.java.getDeclaredMethod(method.name, *method.parameterTypes) }.isFailure
        }

        assertTrue("AppUiApi does not forward: ${missing.joinToString { it.name }}", missing.isEmpty())
    }

    @Test
    fun `the car's calls go to the driving that is there when they are made, and are refused without one`() {
        var driving: UiDriving? = null
        val api = AppUiApi(controller()) { driving }

        assertFalse(api.driveMode(true))
        assertFalse(api.gpsDrive(1.0, 0.0, 0.0))
        assertFalse(api.screenshot())

        val calls = mutableListOf<String>()
        driving = object : UiDriving {
            override fun driveMode(on: Boolean) = true.also { calls += "mode $on" }
            override fun gpsDrive(throttle: Double, brake: Double, steer: Double) = true.also { calls += "drive $throttle" }
            override fun screenshot() = true.also { calls += "shot" }
        }

        assertTrue(api.driveMode(true) && api.gpsDrive(1.0, 0.0, 0.0) && api.screenshot())
        assertEquals(listOf("mode true", "drive 1.0", "shot"), calls)
    }
}
