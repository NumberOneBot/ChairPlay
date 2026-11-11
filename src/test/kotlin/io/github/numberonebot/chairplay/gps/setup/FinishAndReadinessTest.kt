package io.github.numberonebot.chairplay.gps.setup

import io.github.numberonebot.chairplay.gps.CommandOutcome
import io.github.numberonebot.chairplay.gps.DeveloperModeStatus
import io.github.numberonebot.chairplay.gps.GpsReason
import io.github.numberonebot.chairplay.gps.SetupAction
import io.github.numberonebot.chairplay.gps.SetupIntent
import io.github.numberonebot.chairplay.gps.SetupStep
import io.github.numberonebot.chairplay.gps.ToolsStatus
import io.github.numberonebot.chairplay.gps.UsbErrorCode
import io.github.numberonebot.chairplay.gps.UsbReadiness
import io.github.numberonebot.chairplay.gps.UsbResult
import io.github.numberonebot.chairplay.gps.setup.FakeUsbHelper.Companion.OTHER
import io.github.numberonebot.chairplay.gps.setup.FakeUsbHelper.Companion.PHONE
import io.github.numberonebot.chairplay.gps.setup.FakeUsbHelper.Companion.probeOf
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class FinishAndReadinessTest {
    private val h = SetupHarness()

    @Test fun `no valid position shows in the view and the coordinator decides on Enable`() {
        h.position = false
        val id = h.open()
        assertFalse(h.view().positionValid)
        assertEquals(CommandOutcome.ACCEPTED, h.controller.act(id, SetupAction.ENABLE))
        assertEquals(true, h.finishes.single().third)
    }

    @Test fun `setting a position while open turns Enable on at the next poll`() {
        h.position = false
        h.open()
        h.position = true
        h.exec.tick()
        assertTrue(h.view().positionValid)
    }

    @Test fun `Enable re-checks first and hands the coordinator the phone, once`() {
        val id = h.open(SetupIntent.ENABLE)
        assertTrue(h.finishes.isEmpty())
        h.helper.calls.clear()
        assertEquals(CommandOutcome.ACCEPTED, h.controller.act(id, SetupAction.ENABLE))
        assertTrue(h.helper.calls.contains("probe:${PHONE.udid}"))
        val (opId, device, enable) = h.finishes.single()
        assertEquals(id, opId)
        assertEquals(PHONE, device)
        assertTrue(enable)
        assertFalse(h.view().open)
    }

    @Test fun `Done hands over without enabling`() {
        val id = h.open()
        h.controller.act(id, SetupAction.DONE)
        assertEquals(false, h.finishes.single().third)
    }

    @Test fun `Enable after a prerequisite went does not finish`() {
        val id = h.open()
        h.helper.probe = UsbResult.Ok(probeOf(developerMode = DeveloperModeStatus.OFF))
        assertEquals(CommandOutcome.INVALID, h.controller.act(id, SetupAction.ENABLE))
        assertTrue(h.finishes.isEmpty())
        assertTrue(h.view().open)
        assertEquals(SetupStep.DEVELOPER_MODE, h.view().current)
    }

    @Test fun `a refused finish keeps the wizard open`() {
        h.finishOutcome = CommandOutcome.BUSY
        val id = h.open()
        assertEquals(CommandOutcome.BUSY, h.controller.act(id, SetupAction.ENABLE))
        assertTrue(h.view().open)
    }

    @Test fun `Enable before Ready is invalid and a wrong operation id is stale`() {
        h.helper.devices = UsbResult.Ok(emptyList())
        val id = h.open()
        assertEquals(CommandOutcome.INVALID, h.controller.act(id, SetupAction.ENABLE))
        assertEquals(CommandOutcome.STALE, h.controller.act(id + 1, SetupAction.ENABLE))
    }

    @Test fun `polls do not run work during a download and never retry an error`() {
        h.helper.tools = UsbResult.Ok(ToolsStatus(false, false, false))
        val id = h.open()
        h.helper.prepare = { _, _, _ ->
            h.helper.calls.clear()
            h.exec.tick()
            assertTrue(h.helper.calls.isEmpty())
            UsbResult.Failed(UsbErrorCode.HELPER_FAILED)
        }
        h.controller.act(id, SetupAction.TOOLS)
        h.exec.drain()
        h.helper.calls.clear()
        repeat(3) { h.exec.tick() }
        assertEquals(0, h.helper.count("prepare:${PHONE.udid}"))
        assertEquals(0, h.helper.count("tools:${PHONE.udid}"))
        assertEquals(3, h.helper.count("devices"))
    }

    @Test fun `after Cancel polling stops and a tick never reopens the wizard`() {
        val id = h.open()
        h.controller.act(id, SetupAction.CANCEL)
        assertFalse(h.exec.repeating)
        h.exec.tick()
        assertFalse(h.view().open)
    }

    @Test fun `setup with a running sender never touches a session`() {
        // The fake fails the test if openSession is called; open, retry and cancel go through the lot.
        val id = h.open()
        h.controller.act(id, SetupAction.RETRY, SetupStep.USB)
        h.exec.drain()
        h.controller.act(id, SetupAction.CANCEL)
        assertTrue(h.finishes.isEmpty())
    }

    // ---- readiness() ----

    private fun notReady() = h.controller.readiness() as UsbReadiness.NotReady

    @Test fun `readiness of the prepared phone`() {
        assertEquals(UsbReadiness.Ready(PHONE), h.controller.readiness())
    }

    @Test fun `readiness without a prepared phone needs setup`() {
        h.prepared = null
        assertEquals(GpsReason.USB_SETUP_NEEDED, notReady().reason)
    }

    @Test fun `readiness with no cable or another phone`() {
        h.helper.devices = UsbResult.Ok(emptyList())
        assertEquals(GpsReason.USB_NOT_CONNECTED, notReady().reason)
        h.helper.devices = UsbResult.Ok(listOf(OTHER))
        assertEquals(GpsReason.USB_NOT_CONNECTED, notReady().reason)
    }

    @Test fun `readiness names the step that went`() {
        h.helper.probe = UsbResult.Ok(probeOf(trusted = false))
        assertEquals(SetupStep.TRUST, notReady().step)
        h.helper.probe = UsbResult.Ok(probeOf(developerMode = DeveloperModeStatus.OFF))
        assertEquals(SetupStep.DEVELOPER_MODE, notReady().step)
        h.helper.probe = UsbResult.Ok(probeOf().copy(serviceReady = false))
        assertEquals(SetupStep.TOOLS, notReady().step)
        assertEquals(GpsReason.USB_NOT_READY, notReady().reason)
    }

    @Test fun `readiness neither opens the wizard nor sends anything`() {
        h.controller.readiness()
        assertFalse(h.view().open)
        assertEquals(0, h.helper.count("prepare:${PHONE.udid}"))
    }
}
