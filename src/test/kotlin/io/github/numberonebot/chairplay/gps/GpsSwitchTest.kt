package io.github.numberonebot.chairplay.gps

import io.github.numberonebot.chairplay.drive.GpsSettings
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** GpsCoordinator: a method chosen is saved and goes in force with the next CarPlay connection (D8). */
class GpsSwitchTest {
    private val usbOn = GpsSettings(enabled = true, method = GpsMethod.USB, usbDevice = "UDID-1")
    private val attached = ChannelState(null, device = UsbDeviceView("r", "iPhone"))

    private fun runningUsb() = GpsHarness(usbOn).apply {
        usb.state = attached
        reconcile()
        assertTrue(usb.running)
        calls.clear()
    }

    @Test
    fun `choosing a method only saves it`() {
        val h = runningUsb()

        assertEquals(CommandOutcome.ACCEPTED, h.gps.selectMethod(GpsMethod.CARPLAY, h.output().generation))
        h.idle()

        assertEquals(listOf("save gps.method=carplay"), h.calls)
        val out = h.output()
        assertEquals(GpsMethod.CARPLAY, out.selected)
        assertEquals(GpsMethod.USB, out.applied)
        assertTrue(h.usb.running)
        assertNull(out.operation)
    }

    // L-01 at the connection: the old USB stops as an explicit stop does, then CarPlay starts; the car stays.
    @Test
    fun `the next connection stops the old method and starts the chosen one`() {
        val h = runningUsb()
        h.gps.selectMethod(GpsMethod.CARPLAY, h.output().generation)
        h.idle()
        h.calls.clear()

        h.gps.connected()
        h.idle()

        assertEquals(listOf("usb.quiesce", "usb.release", "carplay.start 40.7,-74.0"), h.calls)
        val out = h.output()
        assertEquals(GpsMethod.CARPLAY, out.applied)
        assertTrue(out.notices.any { it.code == NoticeCode.RESET_SENT })
        assertEquals(0, h.hold.held)
    }

    @Test
    fun `a connection with nothing chosen since changes nothing`() {
        val h = runningUsb()

        h.gps.connected()
        h.idle()

        assertEquals(emptyList<String>(), h.calls)
        assertTrue(h.usb.running)
    }

    // V13, V14: the same method again is no change; a page that saw an older generation is refused.
    @Test
    fun `the same method again is no change, and a stale generation is refused`() {
        val h = GpsHarness()
        val seen = h.output().generation

        assertEquals(CommandOutcome.NO_CHANGE, h.gps.selectMethod(GpsMethod.CARPLAY, seen))
        assertEquals(CommandOutcome.ACCEPTED, h.gps.selectMethod(GpsMethod.USB, seen))
        assertEquals(CommandOutcome.STALE, h.gps.selectMethod(GpsMethod.CARPLAY, seen))
        assertEquals(listOf("save gps.method=usb"), h.calls)
    }

    // With the simulation off nothing sends, whatever the method: a method chosen is in force at once, no reconnect.
    @Test
    fun `a method chosen with the simulation off is in force at once and starts nothing`() {
        val h = GpsHarness(GpsSettings(enabled = false))

        h.gps.selectMethod(GpsMethod.USB, h.output().generation)
        h.idle()

        assertEquals(GpsMethod.USB, h.output().applied)
        assertFalse(h.calls.any { it.contains(".start") || it.contains("release") })
    }

    // D9: while USB stops only the switch waits; the hold lasts as long as the operation.
    @Test
    fun `turning off while an operation runs is busy, and the car is held meanwhile`() {
        val h = runningUsb()
        val inside = java.util.concurrent.CountDownLatch(1)
        val letGo = java.util.concurrent.CountDownLatch(1)
        h.usb.onQuiesce = { inside.countDown(); letGo.await() }

        h.gps.setEnabled(false)
        inside.await()
        assertEquals(1, h.hold.held)
        assertEquals(OperationKind.DISABLE, h.output().operation?.kind)
        assertEquals(CommandOutcome.BUSY, h.gps.setEnabled(true))
        letGo.countDown()
        h.idle()
        assertEquals(0, h.hold.held)
        assertNull(h.output().operation)
    }

    // V15: an operation whose body hangs ends at its deadline.
    @Test
    fun `an operation that hangs ends at its deadline`() {
        val h = GpsHarness(usbOn, timing = GpsTiming(tickMillis = 60_000, operationMillis = 200)).apply {
            usb.state = attached
            reconcile()
        }
        val letGo = java.util.concurrent.CountDownLatch(1)
        h.usb.onQuiesce = { letGo.await() }

        h.gps.setEnabled(false)
        Thread.sleep(600)

        assertNull(h.output().operation)
        assertEquals(0, h.hold.held)
        assertFalse(h.store.current().enabled)
        letGo.countDown()
        h.idle()
    }
}
