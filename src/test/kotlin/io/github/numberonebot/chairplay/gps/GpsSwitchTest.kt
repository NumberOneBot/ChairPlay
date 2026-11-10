package io.github.numberonebot.chairplay.gps

import io.github.numberonebot.chairplay.drive.GeoPoint
import io.github.numberonebot.chairplay.drive.GpsSettings
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** GpsCoordinator: changing the method (01 S-01…S-10, 03 §5). */
class GpsSwitchTest {
    private val usbOn = GpsSettings(enabled = true, method = GpsMethod.USB, usbDevice = "UDID-1")

    private fun runningUsb(located: ReturnPoint = ReturnPoint.Found(GpsHarness.HOME)) = GpsHarness(usbOn, located).apply {
        usb.state = ChannelState(null, device = UsbDeviceView("r", "iPhone"))
        usb.running = true
        journal.mark("UDID-1")
        reconcile()
        calls.clear()
    }

    // S-02, V07, V46: the old writer stops before the car moves; the car and the new writer start at Windows' place.
    @Test
    fun `switching stops the old writer, returns the car to Windows and starts the new one there`() {
        val h = runningUsb()

        assertEquals(CommandOutcome.ACCEPTED, h.gps.selectMethod(GpsMethod.CARPLAY, h.output().generation))
        h.idle()

        assertEquals(
            listOf(
                "save gps.method=carplay", "usb.quiesce", "locate", "car.place 49.84,24.03", "usb.release 49.84,24.03",
                "journal.clear", "carplay.start 49.84,24.03",
            ),
            h.calls,
        )
        val out = h.output()
        assertEquals(GpsMethod.CARPLAY, out.selected)
        assertTrue(out.enabledIntent)
        assertEquals(Cleanup.CLEAN, out.cleanup)
        assertNull(out.operation)
        assertEquals(0, h.hold.held)
        assertTrue(out.notices.any { it.code == NoticeCode.RESET_SENT && it.expiresAtMillis == h.now + 10_000 })
    }

    // S-03, V08: no Windows place: the switch completes from the car's own place, with a warning.
    @Test
    fun `without a Windows place the switch keeps the car where it is and warns`() {
        val h = runningUsb(ReturnPoint.Unavailable("denied"))

        h.gps.selectMethod(GpsMethod.CARPLAY, h.output().generation)
        h.idle()

        assertFalse(h.calls.any { it.startsWith("car.place") })
        assertTrue("usb.release -" in h.calls)
        assertTrue("carplay.start 40.7,-74.0" in h.calls)
        val warning = h.output().notices.single { it.code == NoticeCode.WINDOWS_UNAVAILABLE }
        assertEquals(mapOf("reason" to "denied"), warning.args)
        assertEquals(GpsMethod.CARPLAY, h.output().selected)
    }

    // S-04, S-05, V09, V11: a reset that could not go does not veto the switch; it stays unconfirmed, not claimed.
    @Test
    fun `a reset that could not go leaves the cleanup unconfirmed and the switch done`() {
        val h = runningUsb()
        h.usb.release = Release(StepOutcome.SKIPPED, StepOutcome.SKIPPED, StepOutcome.SKIPPED)

        h.gps.selectMethod(GpsMethod.CARPLAY, h.output().generation)
        h.idle()

        val out = h.output()
        assertEquals(GpsMethod.CARPLAY, out.selected)
        assertEquals(Cleanup.UNCONFIRMED, out.cleanup)
        assertEquals("UDID-1", h.journal.dirty())
        assertFalse(out.notices.any { it.code == NoticeCode.RESET_SENT })
        assertTrue(out.notices.any { it.code == NoticeCode.CLEANUP_UNCONFIRMED })
        assertTrue(h.carplay.running)
    }

    // V10: a failed return still clears; a clear without a closed channel claims no reset sent.
    @Test
    fun `a failed return still clears, and an unclosed channel claims no reset`() {
        val h = runningUsb()
        h.usb.release = Release(StepOutcome.FAILED, StepOutcome.DONE, StepOutcome.FAILED)

        h.gps.selectMethod(GpsMethod.CARPLAY, h.output().generation)
        h.idle()

        assertEquals(Cleanup.CLEAN, h.output().cleanup)
        assertFalse(h.output().notices.any { it.code == NoticeCode.RESET_SENT })
    }

    // S-08, V12: a USB not ready is still chosen; nothing falls back to CarPlay.
    @Test
    fun `a new method that is not ready stays chosen with its reason`() {
        val h = GpsHarness(GpsSettings(enabled = true, usbDevice = "UDID-1"))
        h.setup.readiness = UsbReadiness.NotReady(GpsReason.USB_NOT_CONNECTED, SetupStep.USB)

        h.gps.selectMethod(GpsMethod.USB, h.output().generation)
        h.idle()

        val out = h.output()
        assertEquals(GpsMethod.USB, out.selected)
        assertTrue(out.enabledIntent)
        assertFalse(out.running)
        assertEquals(GpsReason.USB_NOT_CONNECTED, out.reason)
        assertFalse(h.carplay.running)
    }

    // S-01, V13, V14: the same method again does nothing; a page that saw an older generation is refused.
    @Test
    fun `the same method again is no change, and a stale generation is refused`() {
        val h = GpsHarness()
        val seen = h.output().generation

        assertEquals(CommandOutcome.NO_CHANGE, h.gps.selectMethod(GpsMethod.CARPLAY, seen))
        assertEquals(CommandOutcome.ACCEPTED, h.gps.selectMethod(GpsMethod.USB, seen))
        h.idle()
        assertEquals(CommandOutcome.STALE, h.gps.selectMethod(GpsMethod.CARPLAY, seen))
        assertEquals(listOf("save gps.method=usb"), h.calls.filter { it.startsWith("save") })
    }

    // S-09, V14: one operation at a time; its input hold lasts as long as it does.
    @Test
    fun `a second command while switching is busy, and the input is held meanwhile`() {
        val h = runningUsb()
        val inside = java.util.concurrent.CountDownLatch(1)
        val letGo = java.util.concurrent.CountDownLatch(1)
        h.usb.onQuiesce = { inside.countDown(); letGo.await() }

        h.gps.selectMethod(GpsMethod.CARPLAY, h.output().generation)
        inside.await()
        assertEquals(1, h.hold.held)
        assertEquals(OperationKind.SWITCH, h.output().operation?.kind)
        assertEquals(Availability.PREPARING, h.output().availability)
        assertEquals(CommandOutcome.BUSY, h.gps.setEnabled(false))
        assertEquals(CommandOutcome.BUSY, h.gps.selectMethod(GpsMethod.USB, h.output().generation))
        letGo.countDown()
        h.idle()
        assertEquals(0, h.hold.held)
        assertNull(h.output().operation)
    }

    // V15: a switch whose body hangs ends at the deadline, the choice kept and the app let go.
    @Test
    fun `a switch that hangs ends at its deadline with the choice kept`() {
        val h = GpsHarness(usbOn, timing = GpsTiming(tickMillis = 60_000, operationMillis = 200)).apply {
            usb.running = true
            journal.mark("UDID-1")
        }
        val letGo = java.util.concurrent.CountDownLatch(1)
        h.usb.onQuiesce = { letGo.await() }

        h.gps.selectMethod(GpsMethod.CARPLAY, h.output().generation)
        Thread.sleep(600)

        assertNull(h.output().operation)
        assertEquals(0, h.hold.held)
        assertEquals(GpsMethod.CARPLAY, h.output().selected)
        assertEquals(GpsMethod.CARPLAY, h.store.current().method)
        letGo.countDown()
        h.idle()
        // The late body changes nothing more: the car stays, CarPlay waits for the next reconcile.
        assertFalse(h.calls.any { it.startsWith("car.place") })
    }

    // S-10: with the switch off, a switch starts no writer.
    @Test
    fun `a switch with the simulation off starts nothing`() {
        val h = GpsHarness(GpsSettings(enabled = false))

        h.gps.selectMethod(GpsMethod.USB, h.output().generation)
        h.idle()

        assertFalse(h.calls.any { it.contains(".start") || it.contains("release") })
        assertEquals(GeoPoint(49.84, 24.03), h.car.at)
    }
}
