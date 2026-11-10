package io.github.numberonebot.chairplay.gps.setup

import io.github.numberonebot.chairplay.gps.CommandOutcome
import io.github.numberonebot.chairplay.gps.DeveloperModeStatus
import io.github.numberonebot.chairplay.gps.SetupAction
import io.github.numberonebot.chairplay.gps.SetupStep
import io.github.numberonebot.chairplay.gps.StepStatus
import io.github.numberonebot.chairplay.gps.UsbErrorCode
import io.github.numberonebot.chairplay.gps.UsbResult
import io.github.numberonebot.chairplay.gps.setup.FakeUsbHelper.Companion.PHONE
import io.github.numberonebot.chairplay.gps.setup.FakeUsbHelper.Companion.probeOf
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class DeveloperModeStepTest {
    private val h = SetupHarness()

    private fun offPhone() {
        h.helper.probe = UsbResult.Ok(probeOf(developerMode = DeveloperModeStatus.OFF))
    }

    @Test fun `Developer Mode off offers Show Developer Mode with trust and USB done`() {
        offPhone()
        h.open()
        assertEquals(SetupStep.DEVELOPER_MODE, h.view().current)
        assertEquals(StepStatus.ACTION, h.status(SetupStep.DEVELOPER_MODE))
        assertEquals("hidden", h.detail(SetupStep.DEVELOPER_MODE))
        assertTrue(h.done(SetupStep.USB, SetupStep.TRUST))
    }

    @Test fun `reveal calls reveal only and stays on the step`() {
        offPhone()
        val id = h.open()
        assertEquals(CommandOutcome.ACCEPTED, h.controller.act(id, SetupAction.REVEAL))
        h.exec.drain()
        assertEquals(listOf("reveal:${PHONE.udid}"), h.helper.calls.filter { it.startsWith("reveal") })
        assertEquals(SetupStep.DEVELOPER_MODE, h.view().current)
        assertEquals(StepStatus.WAITING, h.status(SetupStep.DEVELOPER_MODE))
        assertEquals("revealed", h.detail(SetupStep.DEVELOPER_MODE))
        assertTrue(h.finishes.isEmpty())
    }

    @Test fun `a lost link after reveal keeps the step with link-lost and never says rebooting`() {
        offPhone()
        val id = h.open()
        h.controller.act(id, SetupAction.REVEAL)
        h.exec.drain()
        h.helper.devices = UsbResult.Ok(emptyList())
        h.exec.tick()
        assertEquals(SetupStep.DEVELOPER_MODE, h.view().current)
        assertEquals("link-lost", h.detail(SetupStep.DEVELOPER_MODE))
        assertEquals(StepStatus.WAITING, h.status(SetupStep.DEVELOPER_MODE))
        assertTrue(h.done(SetupStep.USB, SetupStep.TRUST))
    }

    @Test fun `the phone coming back is checked again and moves on without a new trust`() {
        offPhone()
        val id = h.open()
        h.controller.act(id, SetupAction.REVEAL)
        h.exec.drain()
        h.helper.devices = UsbResult.Ok(emptyList())
        h.exec.tick()
        h.helper.devices = UsbResult.Ok(listOf(PHONE))
        h.helper.probe = UsbResult.Ok(probeOf(developerMode = DeveloperModeStatus.ON))
        h.helper.calls.clear()
        h.exec.tick()
        assertTrue(h.helper.calls.contains("probe:${PHONE.udid}"))
        assertTrue(h.done(SetupStep.USB, SetupStep.TRUST, SetupStep.DEVELOPER_MODE, SetupStep.TOOLS))
        assertEquals(SetupStep.READY, h.view().current)
    }

    @Test fun `back from loss with the mode still off shows revealed again`() {
        offPhone()
        val id = h.open()
        h.controller.act(id, SetupAction.REVEAL)
        h.exec.drain()
        h.helper.devices = UsbResult.Ok(emptyList())
        h.exec.tick()
        h.helper.devices = UsbResult.Ok(listOf(PHONE))
        h.exec.tick()
        assertEquals("revealed", h.detail(SetupStep.DEVELOPER_MODE))
    }

    @Test fun `trust is asked again only when the phone says it was lost`() {
        offPhone()
        val id = h.open()
        h.controller.act(id, SetupAction.REVEAL)
        h.exec.drain()
        // locked after a restart: not a lost trust
        h.helper.probe = UsbResult.Failed(UsbErrorCode.LOCKED)
        h.exec.tick()
        assertTrue(h.done(SetupStep.TRUST))
        assertEquals(SetupStep.DEVELOPER_MODE, h.view().current)
        // the phone then really lost it
        h.helper.probe = UsbResult.Failed(UsbErrorCode.NOT_TRUSTED)
        h.exec.tick()
        assertEquals(SetupStep.TRUST, h.view().current)
        assertEquals("trust-prompt", h.detail(SetupStep.TRUST))
    }

    @Test fun `a failed reveal is an error on the same step`() {
        offPhone()
        h.helper.reveal = UsbResult.Failed(UsbErrorCode.SERVICE_UNAVAILABLE)
        val id = h.open()
        h.controller.act(id, SetupAction.REVEAL)
        h.exec.drain()
        assertEquals(StepStatus.ERROR, h.status(SetupStep.DEVELOPER_MODE))
        assertEquals("service-unavailable", h.step(SetupStep.DEVELOPER_MODE).error)
        assertEquals(SetupStep.DEVELOPER_MODE, h.view().current)
    }

    @Test fun `a passing disconnect keeps finished steps and Ready waits`() {
        h.open()
        h.helper.devices = UsbResult.Ok(emptyList())
        h.exec.tick()
        assertEquals(SetupStep.USB, h.view().current)
        assertEquals("no-device", h.detail(SetupStep.USB))
        assertTrue(h.done(SetupStep.TRUST, SetupStep.DEVELOPER_MODE, SetupStep.TOOLS))
        assertEquals(StepStatus.UNKNOWN, h.status(SetupStep.READY))
    }
}
