package io.github.numberonebot.chairplay.ui

import org.junit.Assert.assertEquals
import org.junit.Test
import java.nio.file.Files

/**
 * UiController: the state is built on every change for every open page, so it reads nothing from the disk or the
 * registry; what it shows comes from the start, from the last save, and from the setup checks.
 */
class UiControllerStateTest : UiControllerFixture() {
    @Test
    fun `the state shows the settings as last saved without reading the file`() {
        val ui = controller()
        Files.delete(file)

        assertEquals("Home", (ui.state()["wifi"] as Map<*, *>)["ssid"])
    }

    @Test
    fun `a save shows in the state`() {
        val ui = controller()
        Files.writeString(file, Files.readString(file).replace("wifi.ssid=Home", "wifi.ssid=Typed by hand"))

        ui.saveSettings(mapOf("ui.controls.keys" to "true"))

        assertEquals("Typed by hand", (ui.state()["wifi"] as Map<*, *>)["ssid"])
        assertEquals(true, ui.state()["controlsKeys"])
    }

    // ui.tint colours both pages at once: the state carries it as saved, and black for a value no palette has.
    @Test
    fun `the state carries the tint as last saved`() {
        assertEquals("black", controller().state()["tint"])

        val ui = controller()
        ui.saveSettings(mapOf("ui.tint" to "slate"))
        assertEquals("slate", ui.state()["tint"])

        assertEquals("black", controller(settings = "wifi.ssid=Home\nui.tint=pink\n").state()["tint"])
    }

    // The controls window keeps black unless ui.tint.controls, a hand edit only, lets the tint reach it (owner).
    @Test
    fun `the controls window takes the tint only when ui tint controls says so`() {
        assertEquals("black", controller(settings = "wifi.ssid=Home\nui.tint=slate\n").state()["controlsTint"])
        assertEquals("black",
            controller(settings = "wifi.ssid=Home\nui.tint=slate\nui.tint.controls=false\n").state()["controlsTint"])
        assertEquals("slate",
            controller(settings = "wifi.ssid=Home\nui.tint=slate\nui.tint.controls=true\n").state()["controlsTint"])
    }

    // The details for a report name Windows, which comes from the registry: read once, for the state and New issue alike.
    @Test
    fun `the Windows version is read once`() {
        var reads = 0
        val ui = controller(systemVersion = { reads++; "Windows 11 24H2 (build 26300)" })

        repeat(3) { ui.state() }
        ui.open("issues")

        assertEquals(1, reads)
    }

    // The MFi files are the setup checks' to look for, as the page asks: a state built does not look again.
    @Test
    fun `the state takes the MFi files from the setup checks`() {
        var reads = 0
        val checks = SetupChecks({ null }, { null }, { FirewallStatus.MISSING }, FirewallStatus.MISSING,
            mfiPresent = { reads++; mfiFilesPresent(dir.resolve("offline-mfi")) })
        val ui = controller(checks = checks)
        Files.createDirectories(dir.resolve("offline-mfi"))
        Files.writeString(dir.resolve("offline-mfi/identity.pk8"), "x")
        Files.writeString(dir.resolve("offline-mfi/certificate.p7b"), "x")
        val mfi = { (ui.state()["setup"] as Map<*, *>)["mfiPresent"] }

        assertEquals("not looked for yet", false, mfi())
        checks.check()
        assertEquals(true, mfi())
        assertEquals(true, mfi())
        assertEquals(1, reads)
    }
}
