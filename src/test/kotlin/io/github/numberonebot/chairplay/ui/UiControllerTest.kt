package io.github.numberonebot.chairplay.ui

import io.github.numberonebot.chairplay.config.SetupStore
import io.github.numberonebot.chairplay.input.ControlAction
import io.github.numberonebot.chairplay.input.ControlsConfig
import io.github.numberonebot.chairplay.input.KeyBinding
import org.lwjgl.glfw.GLFW.GLFW_KEY_F6
import io.github.numberonebot.chairplay.phone.LinkStatus
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.nio.file.Files

/** UiController: the state the pages show: the session, Get started, the controls, the demos and the GPS. */
class UiControllerTest : UiControllerFixture() {
    // Get started shows until the first CarPlay session, which marks the setup done for the starts after.
    @Test
    fun `the first session marks the setup done`() {
        val store = SetupStore(dir)
        val ui = controller(setupStore = store)
        assertEquals(false, ui.state()["setupDone"])

        ui.model.sessionActive()

        assertEquals(true, ui.state()["setupDone"])
        assertTrue(store.done())
    }

    @Test
    fun `Forget this iPhone opens Get started again`() {
        val store = SetupStore(dir)
        store.save(true)
        val ui = controller(setupStore = store)
        val before = ui.version

        assertTrue(ui.forget())

        assertEquals(false, ui.state()["setupDone"])
        assertFalse(store.done())
        assertTrue(ui.version > before)
        assertFalse(controller().forget())
    }

    // The Experiments page plays the HUD's demos on the AltScreen, one at a time, and stops them.
    @Test
    fun `the Experiments page plays a HUD demo and stops it`() {
        val demo = FakeDemo()
        val ui = controller(demo = demo)
        assertEquals(mapOf("playing" to null, "available" to true), ui.state()["demo"])
        val before = ui.version

        assertTrue(ui.demo("lanes"))
        assertEquals("lanes", (ui.state()["demo"] as Map<*, *>)["playing"])
        assertTrue(ui.version > before)
        assertTrue(ui.demo(null))
        assertEquals(null, (ui.state()["demo"] as Map<*, *>)["playing"])

        assertFalse(ui.demo("tetris"))
        demo.available = false
        assertFalse(ui.demo("maneuvers"))
        assertFalse(controller().demo("maneuvers"))
        assertNull(controller().state()["demo"])
    }

    // The GPS page shows the car as it goes: the state carries it, and each change of it tells the page.
    @Test
    fun `the GPS page gets the car's state as it changes`() {
        var speed = 0
        val ui = controller(gps = { mapOf("speed" to speed) })
        assertEquals(mapOf("speed" to 0), ui.state()["gps"])
        val before = ui.version
        speed = 48
        ui.gpsChanged()
        assertTrue(ui.version > before)
        assertEquals(mapOf("speed" to 48), ui.state()["gps"])
        assertNull(controller().state()["gps"])
    }

    // The page shows the black waiting screen instead of the settings while the app connects on its own.
    @Test
    fun `the page hears whether the start waits for the connection`() {
        assertEquals(true, controller().state()["waiting"])
        assertEquals(false, controller(autoconnect = false).state()["waiting"])
    }

    // The pages colour themselves from it.
    @Test
    fun `the state carries the accent colour`() {
        assertEquals("#52c4ff", controller().state()["accent"])
    }

    // The About page's Version group.
    @Test
    fun `the state says which build this is`() {
        val about = controller().state()["about"] as Map<*, *>

        assertTrue(Regex("[0-9a-f]{8}").matches((about["app"] as Map<*, *>)["commit"] as String))
        assertTrue(Regex("[0-9a-f]{8}").matches((about["core"] as Map<*, *>)["commit"] as String))
    }

    // Forget during a session: later changes while the session goes on must not mark the setup done again.
    @Test
    fun `Forget during a session stays forgotten`() {
        val store = SetupStore(dir)
        store.save(true)
        val ui = controller(setupStore = store)
        ui.model.sessionActive()

        ui.forget()
        ui.model.carButton()
        ui.model.show(Screen.CARPLAY)

        assertEquals(false, ui.state()["setupDone"])
        assertFalse(store.done())
    }

    @Test
    fun `without a store the setup counts as done`() {
        assertEquals(true, controller().state()["setupDone"])
    }

    @Test
    fun `the controls carry each action's keys, defaults, and fixed keys, and the wheel and repeat`() {
        val controls = controller(bindings = keyboard()).controls()!!

        val actions = controls["actions"] as Map<*, *>
        assertEquals(
            mapOf("keys" to listOf("Escape", "Backspace"), "defaults" to listOf("Escape", "Backspace"), "fixed" to listOf("Escape")),
            actions["key.back"],
        )
        assertEquals(mapOf("keys" to listOf("Home"), "defaults" to listOf("Home"), "fixed" to emptyList<String>()), actions["key.home"])
        assertEquals(ControlAction.entries.map { it.key }, actions.keys.toList())
        assertEquals(
            mapOf("wheel.action" to "knob", "wheel.invert" to "false", "wheel.notches.per.step" to "1", "key.repeat.every" to "3",
                "key.zoom.repeat.every" to "10", "global.enabled" to "false"),
            controls["settings"],
        )
        assertNull(controller().controls())
    }

    // Changes apply at once: the app takes the new bindings, and the controls window's legends follow the version.
    // Get started and the Connection page follow the PC as it changes: a phone paired, the PC on a network, a rule added.
    @Test
    fun `the setup in the state is the live checks'`() {
        val checks = SetupChecks({ "Kate's iPhone" }, { "ChairPlay-5G" }, { FirewallStatus.PRESENT }, FirewallStatus.MISSING, { true })
        val ui = controller(checks = checks)
        val setup = { ui.state()["setup"] as Map<*, *> }
        assertEquals(null, setup()["pairedIphone"])
        assertEquals("missing", setup()["firewall"])

        checks.check()

        assertEquals("Kate's iPhone", setup()["pairedIphone"])
        assertEquals("ChairPlay-5G", setup()["pcWifi"])
    }

    // The controls window's page shows each button's key while Alt is held, or always with ui.controls.keys.
    @Test
    fun `the state says whether the controls window shows its keys`() {
        val ui = controller()
        assertEquals(false, ui.state()["controlsKeys"])

        ui.saveSettings(mapOf("ui.controls.keys" to "true"))

        assertEquals(true, ui.state()["controlsKeys"])
    }

    @Test
    fun `a binding saved is put in force at once`() {
        val ui = controller(bindings = keyboard())
        val before = ui.version

        val result = ui.saveControls(mapOf("key.home" to "F6"))

        assertEquals(emptyMap<String, String>(), result.errors)
        assertEquals(ControlAction.HOME, applied.single().bindings[KeyBinding(GLFW_KEY_F6, 0)])
        assertTrue(ui.version > before)
        assertEquals(listOf("F6"), ((ui.controls()!!["actions"] as Map<*, *>)["key.home"] as Map<*, *>)["keys"])
    }

    @Test
    fun `a binding the start would refuse is refused`() {
        val ui = controller(bindings = keyboard())

        val result = ui.saveControls(mapOf("key.home" to "Escape"))

        assertEquals(setOf("key.home"), result.errors.keys)
        assertEquals(emptyList<ControlsConfig>(), applied)
    }

    // The Audio page's slider sets the app's volume through the controls, as the volume keys turn it.
    @Test
    fun `the Audio page sets the volume`() {
        val controls = FakeControls()

        assertTrue(controller(controls = controls).volume(37))

        assertEquals(listOf("volume 37"), controls.calls)
        assertFalse(controller().volume(37))
    }

    @Test
    fun `the state tells the page what to show`() {
        val ui = controller()
        ui.model.bringUp(LinkStatus.Failed("RFCOMM connect failed", 5_000))

        val state = ui.state()

        assertEquals("retrying", state["phase"])
        assertEquals("start", state["screen"])
        assertEquals("RFCOMM connect failed", (state["error"] as Map<*, *>)["message"])
        assertEquals(false, state["canShowCarPlay"])
        assertEquals("Home", (state["wifi"] as Map<*, *>)["ssid"])
        val setup = state["setup"] as Map<*, *>
        assertEquals(false, setup["mfiPresent"])
        assertEquals("missing", setup["firewall"])
    }

    @Test
    fun `the MFi files are found once they are in place`() {
        val ui = controller()
        Files.createDirectories(dir.resolve("offline-mfi"))
        Files.writeString(dir.resolve("offline-mfi/identity.pk8"), "x")
        Files.writeString(dir.resolve("offline-mfi/certificate.p7b"), "x")

        assertEquals(true, (ui.state()["setup"] as Map<*, *>)["mfiPresent"])
    }

    @Test
    fun `screens change as the model allows`() {
        val ui = controller()

        assertFalse(ui.show("carplay"))
        assertTrue(ui.show("settings"))
        assertEquals(Screen.SETTINGS, ui.model.state.screen)
        ui.model.sessionActive()
        assertTrue(ui.show("carplay"))
        assertFalse(ui.show("elsewhere"))
    }

    // The controls window's page: its buttons reach the app, and the state carries what they show.
    @Test
    fun `the controls window's buttons reach the app and its state reaches the page`() {
        val controls = FakeControls()
        val ui = controller(controls = controls)

        assertTrue(ui.control("knob.select", down = true))
        assertFalse(ui.control("eject", down = true))
        assertTrue(ui.altscreenContent("hud"))

        assertEquals(listOf("knob.select true", "eject true", "altscreen hud"), controls.calls)
        assertEquals(mapOf("volume" to mapOf("level" to 60, "muted" to false)), ui.state()["controls"])
        val before = ui.version
        ui.controlsChanged()
        assertTrue(ui.version > before)
    }

    @Test
    fun `without controls the buttons reach nothing`() {
        val ui = controller()

        assertFalse(ui.control("knob.select", down = true))
        assertFalse(ui.altscreenContent("hud"))
        assertNull(ui.state()["controls"])
    }
}
