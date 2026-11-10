package io.github.numberonebot.chairplay.gps.usb

import io.github.numberonebot.chairplay.gps.AppleComponents
import io.github.numberonebot.chairplay.gps.DeveloperModeStatus
import io.github.numberonebot.chairplay.gps.DownloadProgress
import io.github.numberonebot.chairplay.gps.ToolsStatus
import io.github.numberonebot.chairplay.gps.UsbDevice
import io.github.numberonebot.chairplay.gps.UsbErrorCode
import io.github.numberonebot.chairplay.gps.UsbProbe
import io.github.numberonebot.chairplay.gps.UsbResult
import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ProcessUsbHelperTest {
    private val udid = "00008030-001A2D3E0C38802E"
    private val launcher = FakeLauncher()
    private val timeouts = HelperTimeouts(helloMillis = 2_000, discoverMillis = 500, probeMillis = 1_000,
        prepareIdleMillis = 1_000, cancelGraceMillis = 500, restartBackoffMillis = 300, exitGraceMillis = 300)
    private val helper = ProcessUsbHelper(launcher, timeouts = timeouts)

    @After
    fun close() = helper.close()

    private fun controlAnswers(answer: FakeHelper.(Map<String, Any?>) -> Unit) {
        launcher.control = { if (!standard(it)) answer(it) }
    }

    /** [call]'s answer once the control helper has started: devices() does not wait for the start. */
    private fun <T> soon(call: () -> UsbResult<T>): UsbResult<T> {
        repeat(300) {
            val result = call()
            if (result !is UsbResult.Failed || result.detail?.contains("is starting") != true) return result
            Thread.sleep(10)
        }
        return call()
    }

    private fun phone() = mapOf("udid" to udid, "name" to "Anna's iPhone", "ios" to "27.2")

    @Test
    fun `devices are the helper's USB phones`() {
        controlAnswers { reply(it, mapOf("devices" to listOf(phone()))) }
        assertEquals(UsbResult.Ok(listOf(UsbDevice(udid, "Anna's iPhone", "27.2"))), soon { helper.devices() })
        assertEquals(listOf(HelperRole.CONTROL), launcher.launched.map { it.first })
    }

    @Test
    fun `a failure comes back as its code`() {
        controlAnswers { fail(it, "locked") }
        assertEquals(UsbErrorCode.LOCKED, (soon { helper.devices() } as UsbResult.Failed).code)
    }

    @Test
    fun `a helper that cannot start fails fast and is not started again at once`() {
        launcher.failing = true
        assertEquals(UsbErrorCode.HELPER_FAILED, (soon { helper.devices() } as UsbResult.Failed).code)
        launcher.failing = false
        assertEquals(UsbErrorCode.HELPER_FAILED, (soon { helper.devices() } as UsbResult.Failed).code)
        assertEquals(0, launcher.launched.size)
        Thread.sleep(350)
        controlAnswers { reply(it, mapOf("devices" to emptyList<Any>())) }
        eventually { helper.devices() is UsbResult.Ok }
    }

    @Test
    fun `devices does not wait for a starting helper`() {
        launcher.helloDelayMillis = 600
        controlAnswers { reply(it, mapOf("devices" to emptyList<Any>())) }
        val started = System.nanoTime()
        assertTrue(helper.devices() is UsbResult.Failed)
        assertTrue(System.nanoTime() - started < TimeUnit.MILLISECONDS.toNanos(300))
        eventually { helper.devices() is UsbResult.Ok }
        assertEquals(1, launcher.launched.size)
    }

    @Test
    fun `a helper that ended is started again for the next call`() {
        controlAnswers { reply(it, mapOf("devices" to emptyList<Any>())) }
        assertTrue(soon { helper.devices() } is UsbResult.Ok)
        launcher.of(HelperRole.CONTROL)[0].exit()
        eventually { helper.devices() is UsbResult.Ok && launcher.launched.size == 2 }
    }

    @Test
    fun `components map to their states and unknown on failure`() {
        controlAnswers { reply(it, mapOf("state" to "not-running")) }
        assertEquals(AppleComponents.NOT_RUNNING, helper.components())
        controlAnswers { fail(it, "timeout") }
        assertEquals(AppleComponents.UNKNOWN, helper.components())
    }

    @Test
    fun `probe keeps what the helper could not tell as null`() {
        controlAnswers {
            assertEquals(udid, it["udid"])
            reply(it, mapOf("udid" to udid, "trusted" to true, "locked" to null, "developerMode" to "on", "ios" to "27.2",
                "imageMounted" to true, "serviceReady" to null))
        }
        assertEquals(UsbResult.Ok(UsbProbe(udid, true, null, DeveloperModeStatus.ON, "27.2", true, null)), helper.probe(udid))
    }

    @Test
    fun `tools status and reveal pass the phone and its answer`() {
        controlAnswers {
            when (it["op"]) {
                "tools-status" -> reply(it, mapOf("packageCached" to true, "imageMounted" to false, "serviceReady" to false))
                "reveal" -> reply(it)
            }
        }
        assertEquals(UsbResult.Ok(ToolsStatus(true, false, false)), helper.toolsStatus(udid))
        assertEquals(UsbResult.Ok(Unit), helper.revealDeveloperMode(udid))
        assertEquals(listOf("tools-status", "reveal"), launcher.of(HelperRole.CONTROL)[0].ops().drop(1))
    }

    @Test
    fun `a malformed result is a protocol failure`() {
        controlAnswers { reply(it, mapOf("devices" to "lots")) }
        assertEquals(UsbErrorCode.PROTOCOL, (soon { helper.devices() } as UsbResult.Failed).code)
    }

    @Test
    fun `prepare reports its download on the caller's thread and devices still answer meanwhile`() {
        lateinit var prepare: Map<String, Any?>
        controlAnswers {
            when (it["op"]) {
                "prepare" -> {
                    prepare = it
                    event("progress", mapOf("id" to it["id"], "stage" to "download", "receivedBytes" to 10, "totalBytes" to 40))
                }
                "discover" -> reply(it, mapOf("devices" to listOf(phone())))
            }
        }
        val seen = CopyOnWriteArrayList<Pair<DownloadProgress, String>>()
        var result: UsbResult<Unit>? = null
        val worker = Thread {
            result = helper.prepareTools(udid, { seen += it to Thread.currentThread().name }, { false })
        }.apply { name = "setup-worker"; start() }
        eventually { seen.isNotEmpty() }
        assertTrue(helper.devices() is UsbResult.Ok)
        launcher.of(HelperRole.CONTROL)[0].reply(prepare, mapOf("packageId" to "cryptex-x", "downloaded" to true))
        worker.join(2_000)
        assertEquals(UsbResult.Ok(Unit), result)
        assertEquals(DownloadProgress(10, 40), seen[0].first)
        assertTrue(seen.all { it.second == "setup-worker" })
    }

    @Test
    fun `a cancelled prepare cancels in the helper and ends cancelled`() {
        controlAnswers {
            when (it["op"]) {
                "cancel" -> {
                    reply(it, mapOf("cancelled" to true))
                    val target = requests.first { r -> r["id"] == it["target"] }
                    fail(target, "cancelled")
                }
            }
        }
        val cancel = AtomicBoolean()
        Thread { Thread.sleep(100); cancel.set(true) }.start()
        val result = helper.prepareTools(udid, {}, { cancel.get() })
        assertEquals(UsbErrorCode.CANCELLED, (result as UsbResult.Failed).code)
        val ops = launcher.of(HelperRole.CONTROL)[0].requests
        val target = ops.first { it["op"] == "cancel" }["target"]
        assertEquals(ops.first { it["op"] == "prepare" }["id"], target)
    }

    @Test
    fun `a prepare that shows no progress for too long times out`() {
        controlAnswers { }
        val result = helper.prepareTools(udid, {}, { false })
        assertEquals(UsbErrorCode.TIMEOUT, (result as UsbResult.Failed).code)
        assertTrue(launcher.of(HelperRole.CONTROL)[0].ops().contains("cancel"))
    }

    @Test
    fun `a session runs in a helper of its own, one at a time`() {
        val first = (helper.openSession(udid) as UsbResult.Ok).value
        val open = launcher.of(HelperRole.SESSION)[0].requests.first { it["op"] == "open" }
        assertEquals(listOf(udid, first.id), listOf(open["udid"], open["session"]))
        assertEquals(UsbErrorCode.PROTOCOL, (helper.openSession(udid) as UsbResult.Failed).code)
        first.close()
        val second = (helper.openSession(udid) as UsbResult.Ok).value
        val reopened = launcher.of(HelperRole.SESSION)[1].requests.first { it["op"] == "open" }
        assertTrue((reopened["generation"] as Long) > (open["generation"] as Long))
        assertTrue(second.id != first.id)
        assertTrue(!launcher.of(HelperRole.SESSION)[0].alive())
        assertTrue(launcher.of(HelperRole.CONTROL).isEmpty())
    }

    @Test
    fun `a session that does not open ends its helper`() {
        launcher.session = { if (!standard(it)) fail(it, "image-missing") }
        assertEquals(UsbErrorCode.IMAGE_MISSING, (helper.openSession(udid) as UsbResult.Failed).code)
        val sessionHelper = launcher.of(HelperRole.SESSION)[0]
        eventually { !sessionHelper.alive() }
        launcher.session = { sessionStandard(it) }
        assertTrue(helper.openSession(udid) is UsbResult.Ok)
    }

    @Test
    fun `a lost session's helper is ended before the next one starts`() {
        lateinit var lostHelper: FakeHelper
        val first = (helper.openSession(udid) as UsbResult.Ok).value
        lostHelper = launcher.of(HelperRole.SESSION)[0]
        lostHelper.onInputEnd = {}
        lostHelper.event("lost", mapOf("session" to first.id, "generation" to lostHelper.requests.first { it["op"] == "open" }["generation"], "code" to "link-lost"))
        eventually { helper.openSession(udid) is UsbResult.Ok }
        assertTrue(!lostHelper.alive())
    }
}
