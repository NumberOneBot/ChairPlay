package io.github.numberonebot.chairplay.gps.setup

import io.github.numberonebot.chairplay.gps.CommandOutcome
import io.github.numberonebot.chairplay.gps.DownloadProgress
import io.github.numberonebot.chairplay.gps.SetupAction
import io.github.numberonebot.chairplay.gps.SetupStep
import io.github.numberonebot.chairplay.gps.StepStatus
import io.github.numberonebot.chairplay.gps.ToolsStatus
import io.github.numberonebot.chairplay.gps.UsbErrorCode
import io.github.numberonebot.chairplay.gps.UsbResult
import io.github.numberonebot.chairplay.gps.setup.FakeUsbHelper.Companion.PHONE
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ToolsStepTest {
    private val h = SetupHarness()

    private fun tools(cached: Boolean, mounted: Boolean, service: Boolean) {
        h.helper.tools = UsbResult.Ok(ToolsStatus(cached, mounted, service))
    }

    /** What the phone reports after a successful prepare. */
    private fun readyAfterPrepare() = h.helper.apply {
        prepare = { _, _, _ -> tools = UsbResult.Ok(ToolsStatus(true, true, true)); UsbResult.Ok(Unit) }
    }

    @Test fun `no package offers Download`() {
        tools(cached = false, mounted = false, service = false)
        h.open()
        assertEquals(SetupStep.TOOLS, h.view().current)
        assertEquals(StepStatus.ACTION, h.status(SetupStep.TOOLS))
        assertEquals("missing", h.detail(SetupStep.TOOLS))
    }

    @Test fun `a cached package with no image offers Connect and the first steps are not repeated`() {
        tools(cached = true, mounted = false, service = false)
        val id = h.open()
        assertEquals("cached", h.detail(SetupStep.TOOLS))
        assertTrue(h.done(SetupStep.USB, SetupStep.TRUST, SetupStep.DEVELOPER_MODE))
        readyAfterPrepare()
        h.controller.act(id, SetupAction.TOOLS)
        h.exec.drain()
        assertEquals(1, h.helper.count("prepare:${PHONE.udid}"))
        assertTrue(h.done(SetupStep.TOOLS))
        assertEquals(1, h.helper.count("probe:${PHONE.udid}"))
    }

    @Test fun `a mounted image whose service does not answer is not ready`() {
        tools(cached = true, mounted = true, service = false)
        h.open()
        assertEquals(StepStatus.ACTION, h.status(SetupStep.TOOLS))
        assertEquals("cached", h.detail(SetupStep.TOOLS))
    }

    @Test fun `downloading shows progress and then connecting`() {
        tools(cached = false, mounted = false, service = false)
        val id = h.open()
        var seen: Triple<String?, DownloadProgress?, StepStatus>? = null
        h.helper.prepare = { _, progress, _ ->
            progress(DownloadProgress(10, 100))
            seen = Triple(h.detail(SetupStep.TOOLS), h.view().download, h.status(SetupStep.TOOLS))
            progress(DownloadProgress(100, 100))
            assertEquals("connecting", h.detail(SetupStep.TOOLS))
            tools = UsbResult.Ok(ToolsStatus(true, true, true))
            UsbResult.Ok(Unit)
        }
        h.controller.act(id, SetupAction.TOOLS)
        h.exec.drain()
        assertEquals(Triple("downloading", DownloadProgress(10, 100), StepStatus.WORKING), seen)
        assertNull(h.view().download)
        assertTrue(h.done(SetupStep.TOOLS))
    }

    @Test fun `a failed download keeps the step and every earlier check`() {
        tools(cached = false, mounted = false, service = false)
        val id = h.open()
        h.helper.prepare = { _, _, _ -> UsbResult.Failed(UsbErrorCode.DISK_FULL) }
        h.controller.act(id, SetupAction.TOOLS)
        h.exec.drain()
        assertEquals(StepStatus.ERROR, h.status(SetupStep.TOOLS))
        assertEquals("disk-full", h.step(SetupStep.TOOLS).error)
        assertEquals("missing", h.detail(SetupStep.TOOLS))
        assertTrue(h.done(SetupStep.USB, SetupStep.TRUST, SetupStep.DEVELOPER_MODE))
        assertEquals(SetupStep.TOOLS, h.view().current)
    }

    @Test fun `Retry re-checks only the tools step`() {
        tools(cached = false, mounted = false, service = false)
        val id = h.open()
        h.helper.prepare = { _, _, _ -> UsbResult.Failed(UsbErrorCode.DOWNLOAD_FAILED) }
        h.controller.act(id, SetupAction.TOOLS)
        h.exec.drain()
        h.helper.calls.clear()
        tools(cached = true, mounted = true, service = true)
        h.controller.act(id, SetupAction.RETRY, SetupStep.TOOLS)
        h.exec.drain()
        assertEquals(listOf("tools:${PHONE.udid}"), h.helper.calls)
        assertTrue(h.done(SetupStep.TOOLS))
    }

    @Test fun `an unsupported iOS stays on the tools step`() {
        tools(cached = false, mounted = false, service = false)
        val id = h.open()
        h.helper.prepare = { _, _, _ -> UsbResult.Failed(UsbErrorCode.UNSUPPORTED_IOS) }
        h.controller.act(id, SetupAction.TOOLS)
        h.exec.drain()
        assertEquals("unsupported-ios", h.detail(SetupStep.TOOLS))
        assertEquals(StepStatus.ERROR, h.status(SetupStep.TOOLS))
    }

    @Test fun `a second tools request while working changes nothing`() {
        tools(cached = false, mounted = false, service = false)
        val id = h.open()
        var again: CommandOutcome? = null
        h.helper.prepare = { _, _, _ -> again = h.controller.act(id, SetupAction.TOOLS); UsbResult.Failed(UsbErrorCode.DOWNLOAD_FAILED) }
        h.controller.act(id, SetupAction.TOOLS)
        h.exec.drain()
        assertEquals(CommandOutcome.NO_CHANGE, again)
        assertEquals(1, h.helper.count("prepare:${PHONE.udid}"))
    }

    @Test fun `a cable pulled during the download never reports Ready and waits for the cable`() {
        tools(cached = false, mounted = false, service = false)
        val id = h.open()
        h.helper.prepare = { _, _, _ -> UsbResult.Failed(UsbErrorCode.LINK_LOST) }
        h.controller.act(id, SetupAction.TOOLS)
        h.exec.drain()
        assertEquals(SetupStep.USB, h.view().current)
        assertEquals("no-device", h.detail(SetupStep.USB))
        assertNull(h.view().download)
    }

    @Test fun `Cancel during the download cancels it, closes the wizard and later answers reopen nothing`() {
        tools(cached = false, mounted = false, service = false)
        val id = h.open()
        var cancelSeen: Boolean? = null
        h.helper.prepare = { _, progress, cancel ->
            assertEquals(CommandOutcome.ACCEPTED, h.controller.act(id, SetupAction.CANCEL))
            cancelSeen = cancel.cancelled()
            progress(DownloadProgress(50, 100))
            UsbResult.Failed(UsbErrorCode.CANCELLED)
        }
        h.controller.act(id, SetupAction.TOOLS)
        h.exec.drain()
        assertEquals(true, cancelSeen)
        assertEquals(false, h.view().open)
        assertNull(h.view().download)
        assertEquals(CommandOutcome.STALE, h.controller.act(id, SetupAction.TOOLS))
    }
}
