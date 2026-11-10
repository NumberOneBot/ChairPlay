package io.github.numberonebot.chairplay.gps

import io.github.numberonebot.chairplay.drive.GpsSettings
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** GpsCoordinator: the switch, the start without the iPhone, its return, and the cleanup it owes (01 §3–§5). */
class GpsLifecycleTest {
    private val usbOn = GpsSettings(enabled = true, method = GpsMethod.USB, usbDevice = "UDID-1")
    private val attached = ChannelState(null, device = UsbDeviceView("r", "iPhone"))

    // L-04, V02: started without the iPhone, USB stays chosen and on, sending nothing, with the warning.
    @Test
    fun `a start without the iPhone keeps USB chosen and on, with the warning`() {
        val h = GpsHarness(usbOn)
        h.usb.state = ChannelState(GpsReason.USB_NOT_CONNECTED)

        h.reconcile()

        val out = h.output()
        assertEquals(GpsMethod.USB, out.selected)
        assertTrue(out.enabledIntent)
        assertFalse(out.running)
        assertEquals(GpsReason.USB_NOT_CONNECTED, out.reason)
        assertTrue(out.notices.any { it.code == NoticeCode.USB_UNAVAILABLE })
        assertFalse(h.carplay.running)
    }

    // L-05, V03: prepared but off, no cable is no warning.
    @Test
    fun `USB off without the cable shows no warning`() {
        val h = GpsHarness(usbOn.copy(enabled = false))
        h.usb.state = ChannelState(GpsReason.USB_NOT_CONNECTED)

        h.reconcile()

        assertTrue(h.output().notices.isEmpty())
    }

    // L-06, V39, L-10, V51: the iPhone back resumes the saved simulation once, the old clear first.
    @Test
    fun `the iPhone back resumes USB once, after the old session's clear`() {
        val h = GpsHarness(usbOn, journal = "UDID-1")
        h.usb.state = ChannelState(GpsReason.USB_NOT_CONNECTED)
        h.reconcile()
        h.usb.state = attached

        h.reconcile()
        h.reconcile()

        assertEquals(listOf("usb.release -", "journal.clear", "setup.readiness", "journal.mark UDID-1", "usb.start 40.7,-74.0 UDID-1"),
            h.calls.filter { it.startsWith("usb.") || it.startsWith("journal") || it.startsWith("setup") })
        assertTrue(h.output().running)
        assertTrue(h.output().notices.none { it.code == NoticeCode.USB_UNAVAILABLE })
    }

    // V40, V50: with the switch off or CarPlay chosen, the iPhone back starts no USB; the owned clear still goes.
    @Test
    fun `the iPhone back with CarPlay chosen clears what this app left and starts no USB`() {
        val h = GpsHarness(GpsSettings(enabled = true), journal = "UDID-1")
        h.usb.state = attached

        h.reconcile()

        assertTrue("usb.release -" in h.calls)
        assertFalse(h.calls.any { it.startsWith("usb.start") })
        assertEquals(GpsMethod.CARPLAY, h.output().selected)
        assertNull(h.journal.dirty())
    }

    // L-03: the cable out keeps the switch and the method; the cleanup is unconfirmed.
    @Test
    fun `a lost USB simulation keeps the switch on and its cleanup unconfirmed`() {
        val h = GpsHarness(usbOn)
        h.usb.state = attached
        h.reconcile()
        assertTrue(h.usb.running)

        h.usb.state = ChannelState(GpsReason.USB_NOT_CONNECTED)
        h.usb.lose()
        h.idle()

        val out = h.output()
        assertTrue(out.enabledIntent)
        assertEquals(GpsMethod.USB, out.selected)
        assertEquals(Cleanup.UNCONFIRMED, out.cleanup)
        assertEquals("UDID-1", h.journal.dirty())
    }

    // F-14, V56: Enable with USB never prepared opens the wizard and saves nothing; Cancel leaves it off.
    @Test
    fun `enabling a USB never prepared opens the wizard and saves nothing`() {
        val h = GpsHarness(GpsSettings(enabled = false, method = GpsMethod.USB))

        assertEquals(CommandOutcome.SETUP_OPENED, h.gps.setEnabled(true))
        h.idle()

        assertEquals(listOf("setup.open enable"), h.calls)
        assertFalse(h.output().enabledIntent)
        assertFalse(h.store.current().enabled)
    }

    // V44, V16: the wizard's Ready starts nothing; its final Enable saves the iPhone and the switch and starts one writer.
    @Test
    fun `only the wizard's final Enable starts USB`() {
        val h = GpsHarness(GpsSettings(enabled = true, method = GpsMethod.USB))
        h.usb.state = attached
        h.setup.open(SetupIntent.PREPARE)
        h.reconcile()
        assertFalse(h.calls.any { it.startsWith("usb.start") })

        assertEquals(CommandOutcome.ACCEPTED, h.setup.finish(enable = true))
        h.idle()

        assertEquals(1, h.calls.count { it.startsWith("usb.start") })
        assertEquals("UDID-1", h.store.current().usbDevice)
        assertTrue(h.store.current().enabled)
    }

    // F-12: Done prepares only: the iPhone is saved, the switch stays off, nothing starts.
    @Test
    fun `the wizard's Done saves the iPhone and starts nothing`() {
        val h = GpsHarness(GpsSettings(enabled = false, method = GpsMethod.USB))

        h.setup.finish(enable = false)
        h.idle()

        assertEquals("UDID-1", h.store.current().usbDevice)
        assertFalse(h.store.current().enabled)
        assertFalse(h.calls.any { it.startsWith("usb.start") })
    }

    // D2, V41, V42: Enable starts from the car's place; with the car nowhere it waits, sending no 0,0.
    @Test
    fun `USB starts from the car's place and waits while the car is nowhere`() {
        val h = GpsHarness(usbOn.copy(enabled = false))
        h.usb.state = attached
        h.car.at = null

        h.gps.setEnabled(true)
        h.idle()

        assertTrue(h.output().enabledIntent)
        assertEquals(GpsReason.NO_POSITION, h.output().reason)
        assertFalse(h.calls.any { it.startsWith("usb.start") })

        // L-09: once the user places the car, USB starts from there without the switch again.
        h.car.at = GpsHarness.HOME
        h.reconcile()
        h.reconcile()
        assertEquals(1, h.calls.count { it.startsWith("usb.start 49.84") })
    }

    // The same through the wizard's final Enable, whose closing does not arm a resume.
    @Test
    fun `USB enabled by the wizard with the car nowhere starts once the car is placed`() {
        val h = GpsHarness(GpsSettings(enabled = false, method = GpsMethod.USB))
        h.usb.state = attached
        h.car.at = null
        h.setup.open(SetupIntent.ENABLE)
        h.reconcile()
        h.setup.finish(enable = true)
        h.idle()
        h.reconcile()
        assertEquals(GpsReason.NO_POSITION, h.output().reason)

        h.car.at = GpsHarness.HOME
        h.reconcile()
        h.reconcile()

        assertEquals(1, h.calls.count { it.startsWith("usb.start 49.84") })
    }

    // L-01, L-02: off stops the stream, returns the iPhone to Windows, clears and closes; the car stays.
    @Test
    fun `turning USB off returns the iPhone and leaves the car where it is`() {
        val h = GpsHarness(usbOn)
        h.usb.state = attached
        h.reconcile()
        h.calls.clear()

        h.gps.setEnabled(false)
        h.idle()

        assertEquals(listOf("save gps.enabled=false", "usb.quiesce", "locate", "usb.release 49.84,24.03", "journal.clear"), h.calls)
        assertFalse(h.output().enabledIntent)
        assertTrue(h.output().notices.any { it.code == NoticeCode.RESET_SENT })
    }

    // D4: no reconnect by the app; a session that identified otherwise than the method needs says so.
    @Test
    fun `a session that identified without the GPS asks for a reconnect`() {
        val h = GpsHarness(GpsSettings(enabled = true))
        h.carplay.state = ChannelState(GpsReason.CARPLAY_RECONNECT_NEEDED, sessionGps = false)

        h.reconcile()

        assertEquals(GpsReason.CARPLAY_RECONNECT_NEEDED, h.output().reason)
        assertTrue(h.output().notices.any { it.code == NoticeCode.RECONNECT_NEEDED })
        h.carplay.state = ChannelState(null, sessionGps = true)
        h.reconcile()
        assertTrue(h.output().running)
        assertTrue(h.output().notices.none { it.code == NoticeCode.RECONNECT_NEEDED })
    }

    // P-06: USB on without its iPhone does not drive the car; CarPlay's method drives it as before.
    @Test
    fun `the car drives only while its channel can send`() {
        val h = GpsHarness(usbOn)
        h.usb.state = ChannelState(GpsReason.USB_NOT_CONNECTED)
        h.reconcile()
        assertFalse(h.gps.carLive())
        h.usb.state = attached
        h.reconcile()
        assertTrue(h.gps.carLive())
        assertTrue(GpsHarness(GpsSettings(enabled = true)).gps.carLive())
        assertFalse(GpsHarness(GpsSettings(enabled = false)).gps.carLive())
    }

    // UI-21: the reset notice goes after 10 s.
    @Test
    fun `the reset notice goes after ten seconds`() {
        val h = GpsHarness(usbOn)
        h.usb.state = attached
        h.reconcile()
        h.gps.setEnabled(false)
        h.idle()

        h.now += 10_000

        assertTrue(h.output().notices.none { it.code == NoticeCode.RESET_SENT })
    }
}
