package io.github.numberonebot.chairplay.gps.usb

import io.github.numberonebot.chairplay.drive.GeoPoint
import io.github.numberonebot.chairplay.gps.AppleComponents
import io.github.numberonebot.chairplay.gps.DeveloperToolsStore
import io.github.numberonebot.chairplay.gps.UsbResult
import java.nio.file.Files
import java.nio.file.Path
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Test

/**
 * The Kotlin client against the real Python helper, run as a process in its dry-run mode (--fake: a fake phone and
 * package server, no USB device). Needs the helper's Python: CHAIRPLAY_USB_GPS_PYTHON, or tools/usb-gps/.venv;
 * skipped without one.
 */
class HelperProcessTest {
    private val tools: Path = findTools()
    private val python: Path? = (System.getenv("CHAIRPLAY_USB_GPS_PYTHON")?.let { Path.of(it) }
        ?: tools.resolve(".venv").resolve("Scripts").resolve("python.exe")).takeIf { Files.isRegularFile(it) }
    private val data: Path = Files.createTempDirectory("chairplay-helper-test")
    private val udid = "00008030-001A2D3E0C38802E"
    private var helper: ProcessUsbHelper? = null

    @After
    fun cleanUp() {
        helper?.close()
        data.toFile().deleteRecursively()
    }

    /** A frozen helper (helper.spec's output) to run instead of the package, as the app image does. */
    private val frozen: Path? = System.getenv("CHAIRPLAY_USB_GPS_HELPER")?.let { Path.of(it) }

    private fun start(): ProcessUsbHelper {
        assumeTrue("no helper Python here", python != null || frozen != null)
        val scenario = data.resolve("scenario.json")
        Files.writeString(scenario, """{"devices":[{"udid":"$udid","name":"Test phone"}],"components":"ready"}""")
        val store = DeveloperToolsStore.root(data)
        val fake = listOf("--fake", scenario.toString())
        val launcher = if (frozen != null) ProcessHelperLauncher(listOf(frozen.toString()), store, frozen.parent, fake)
        else ProcessHelperLauncher(listOf(python.toString(), "-s", "-m", "usb_gps_helper"), store, tools, fake)
        return ProcessUsbHelper(launcher, log = { println(it) }).also { helper = it }
    }

    @Test
    fun `the client and the helper speak the same protocol`() {
        val helper = start()
        assertEquals(AppleComponents.READY, helper.components())
        val devices = helper.devices()
        assertEquals(listOf(udid), (devices as UsbResult.Ok).value.map { it.udid })
        val probe = (helper.probe(udid) as UsbResult.Ok).value
        assertEquals(false, probe.imageMounted)
        val bytes = mutableListOf<Long>()
        assertEquals(UsbResult.Ok(Unit), helper.prepareTools(udid, { bytes += it.receivedBytes }, { false }))
        assertTrue(Files.exists(DeveloperToolsStore.root(data)))
        assertTrue((helper.toolsStatus(udid) as UsbResult.Ok).value.serviceReady)

        val session = (helper.openSession(udid) as UsbResult.Ok).value
        for (step in 0 until 20) {
            session.offer(GeoPoint(40.758 + step * 1e-4, -73.9855))
            Thread.sleep(20)
        }
        eventually(5_000) { session.acknowledged > 0 }
        session.close()
        assertTrue(helper.openSession(udid) is UsbResult.Ok)
    }

    private fun findTools(): Path {
        var dir: Path? = Path.of(System.getProperty("user.dir")).toAbsolutePath()
        while (dir != null) {
            val candidate = dir.resolve("tools").resolve("usb-gps")
            if (Files.isDirectory(candidate.resolve("usb_gps_helper"))) return candidate
            dir = dir.parent
        }
        return Path.of("tools", "usb-gps")
    }
}
