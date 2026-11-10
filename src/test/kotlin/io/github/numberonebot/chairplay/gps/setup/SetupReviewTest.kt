package io.github.numberonebot.chairplay.gps.setup

import io.github.numberonebot.chairplay.gps.AppleComponents
import io.github.numberonebot.chairplay.gps.CommandOutcome
import io.github.numberonebot.chairplay.gps.DeveloperModeStatus
import io.github.numberonebot.chairplay.gps.DownloadProgress
import io.github.numberonebot.chairplay.gps.GpsReason
import io.github.numberonebot.chairplay.gps.SetupAction
import io.github.numberonebot.chairplay.gps.SetupIntent
import io.github.numberonebot.chairplay.gps.SetupStep
import io.github.numberonebot.chairplay.gps.StepStatus
import io.github.numberonebot.chairplay.gps.ToolsStatus
import io.github.numberonebot.chairplay.gps.UsbErrorCode
import io.github.numberonebot.chairplay.gps.UsbReadiness
import io.github.numberonebot.chairplay.gps.UsbResult
import io.github.numberonebot.chairplay.gps.setup.FakeUsbHelper.Companion.PHONE
import io.github.numberonebot.chairplay.gps.setup.FakeUsbHelper.Companion.probeOf
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** Fixes from the review of the first version. */
class SetupReviewTest {
    private val h = SetupHarness()

    private fun notReady() = h.controller.readiness() as UsbReadiness.NotReady

    // ---- readiness ----

    @Test fun `readiness never reads unknown as ready`() {
        h.helper.probe = UsbResult.Ok(probeOf(trusted = null))
        assertEquals(SetupStep.TRUST, notReady().step)
        h.helper.probe = UsbResult.Ok(probeOf().copy(serviceReady = null))
        assertEquals(SetupStep.TOOLS, notReady().step)
        h.helper.probe = UsbResult.Ok(probeOf().copy(imageMounted = null))
        assertEquals(SetupStep.TOOLS, notReady().step)
    }

    @Test fun `a locked trusted phone whose service answers is ready`() {
        h.helper.probe = UsbResult.Ok(probeOf(trusted = true, locked = true, developerMode = DeveloperModeStatus.UNKNOWN))
        assertEquals(UsbReadiness.Ready(PHONE), h.controller.readiness())
    }

    @Test fun `a slow helper is a helper failure, not a missing prerequisite`() {
        h.exec.callTimesOut = true
        val r = notReady()
        assertEquals(GpsReason.USB_HELPER_FAILED, r.reason)
        assertEquals(null, r.step)
    }

    @Test fun `Apple Devices closed is not ready with the cable in, not not-connected`() {
        h.helper.devices = UsbResult.Failed(UsbErrorCode.USBMUX_UNAVAILABLE)
        assertEquals(GpsReason.USB_NOT_READY, notReady().reason)
        h.helper.devices = UsbResult.Ok(listOf(PHONE))
        h.helper.components = AppleComponents.NOT_RUNNING
        assertEquals(GpsReason.USB_NOT_READY, notReady().reason)
        assertEquals(SetupStep.USB, notReady().step)
    }

    // ---- steps ----

    @Test fun `several phones are an error with the code`() {
        h.helper.devices = UsbResult.Ok(listOf(PHONE, FakeUsbHelper.OTHER))
        h.open()
        assertEquals(StepStatus.ERROR, h.status(SetupStep.USB))
        assertEquals("several-devices", h.detail(SetupStep.USB))
        assertEquals("several-devices", h.step(SetupStep.USB).error)
    }

    @Test fun `a mounted image without a service is an error that Try again re-checks`() {
        h.helper.tools = UsbResult.Ok(ToolsStatus(packageCached = true, imageMounted = true, serviceReady = false))
        val id = h.open()
        assertEquals(StepStatus.ERROR, h.status(SetupStep.TOOLS))
        assertEquals("service-unavailable", h.step(SetupStep.TOOLS).error)
        assertEquals("cached", h.detail(SetupStep.TOOLS))
        h.helper.tools = UsbResult.Ok(ToolsStatus(true, true, true))
        h.controller.act(id, SetupAction.RETRY, SetupStep.TOOLS)
        h.exec.drain()
        assertTrue(h.done(SetupStep.TOOLS))
    }

    // ---- download ----

    @Test fun `progress after the call returned changes nothing`() {
        h.helper.tools = UsbResult.Ok(ToolsStatus(false, false, false))
        val id = h.open()
        var late: ((DownloadProgress) -> Unit)? = null
        h.helper.prepare = { _, progress, _ ->
            late = progress
            tools = UsbResult.Ok(ToolsStatus(true, true, true))
            UsbResult.Ok(Unit)
        }
        h.controller.act(id, SetupAction.TOOLS)
        h.exec.drain()
        late!!(DownloadProgress(100, 100))
        assertTrue(h.done(SetupStep.TOOLS))
        assertEquals(null, h.view().download)
    }

    @Test fun `a double click runs the download once`() {
        h.helper.tools = UsbResult.Ok(ToolsStatus(false, false, false))
        val id = h.open()
        h.helper.prepare = { _, _, _ -> UsbResult.Failed(UsbErrorCode.DOWNLOAD_FAILED) }
        h.controller.act(id, SetupAction.TOOLS)
        h.controller.act(id, SetupAction.TOOLS)
        h.exec.drain()
        assertEquals(1, h.helper.count("prepare:${PHONE.udid}"))
    }

    // ---- passing failures during the re-check ----

    @Test fun `a timeout during the Enable re-check keeps done steps and does not finish`() {
        val id = h.open()
        h.helper.probe = UsbResult.Failed(UsbErrorCode.TIMEOUT)
        assertEquals(CommandOutcome.INVALID, h.controller.act(id, SetupAction.ENABLE))
        assertTrue(h.done(SetupStep.USB, SetupStep.TRUST, SetupStep.DEVELOPER_MODE, SetupStep.TOOLS))
        h.helper.probe = UsbResult.Ok(probeOf())
        h.helper.tools = UsbResult.Failed(UsbErrorCode.HELPER_FAILED)
        assertEquals(CommandOutcome.INVALID, h.controller.act(id, SetupAction.ENABLE))
        assertTrue(h.done(SetupStep.TOOLS))
        h.helper.devices = UsbResult.Failed(UsbErrorCode.LOCKED)
        assertEquals(CommandOutcome.INVALID, h.controller.act(id, SetupAction.ENABLE))
        assertTrue(h.done(SetupStep.USB))
        assertTrue(h.finishes.isEmpty())
    }

    // ---- finish and cancel compete ----

    @Test fun `Cancel during the re-check wins and onFinish is not called`() {
        val id = h.open()
        h.helper.probe = UsbResult.Ok(probeOf())
        h.helper.onProbe = { h.controller.act(id, SetupAction.CANCEL) }
        h.controller.act(id, SetupAction.ENABLE)
        assertTrue(h.finishes.isEmpty())
        assertEquals(false, h.view().open)
    }

    @Test fun `Cancel while onFinish runs is refused`() {
        val id = h.open()
        var cancel: CommandOutcome? = null
        h.controller.onFinish = { _, _, _ -> cancel = h.controller.act(id, SetupAction.CANCEL); CommandOutcome.ACCEPTED }
        h.controller.act(id, SetupAction.ENABLE)
        assertEquals(CommandOutcome.BUSY, cancel)
        assertEquals(false, h.view().open)
    }

    @Test fun `a finish that gave up never calls onFinish later`() {
        val id = h.open()
        h.exec.callTimesOut = true
        assertEquals(CommandOutcome.BUSY, h.controller.act(id, SetupAction.ENABLE))
        h.exec.drain()
        assertTrue(h.finishes.isEmpty())
        assertTrue(h.view().open)
        // and the wizard can be finished again
        assertEquals(CommandOutcome.ACCEPTED, h.controller.act(id, SetupAction.ENABLE))
        assertEquals(1, h.finishes.size)
    }

    // ---- the poll timer belongs to its run ----

    @Test fun `opening again from the end notification keeps the new timer`() {
        val id = h.open()
        var reopened = false
        h.controller.onChanged = {
            if (!reopened && !h.view().open) { reopened = true; h.controller.open(SetupIntent.PREPARE) }
        }
        h.controller.act(id, SetupAction.CANCEL)
        h.exec.drain()
        assertTrue(h.view().open)
        assertEquals(1, h.exec.activeTickers)
        h.helper.calls.clear()
        h.exec.tick()
        assertTrue(h.helper.calls.isNotEmpty())
    }
}
