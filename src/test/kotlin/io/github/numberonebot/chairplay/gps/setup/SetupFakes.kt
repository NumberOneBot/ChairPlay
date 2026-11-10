package io.github.numberonebot.chairplay.gps.setup

import io.github.numberonebot.chairplay.drive.GeoPoint
import io.github.numberonebot.chairplay.gps.AppleComponents
import io.github.numberonebot.chairplay.gps.Cancellation
import io.github.numberonebot.chairplay.gps.CommandOutcome
import io.github.numberonebot.chairplay.gps.DeveloperModeStatus
import io.github.numberonebot.chairplay.gps.DownloadProgress
import io.github.numberonebot.chairplay.gps.SetupIntent
import io.github.numberonebot.chairplay.gps.SetupStep
import io.github.numberonebot.chairplay.gps.StepState
import io.github.numberonebot.chairplay.gps.StepStatus
import io.github.numberonebot.chairplay.gps.ToolsStatus
import io.github.numberonebot.chairplay.gps.UsbDevice
import io.github.numberonebot.chairplay.gps.UsbErrorCode
import io.github.numberonebot.chairplay.gps.UsbHelper
import io.github.numberonebot.chairplay.gps.UsbProbe
import io.github.numberonebot.chairplay.gps.UsbResult
import io.github.numberonebot.chairplay.gps.UsbSession
import io.github.numberonebot.chairplay.platform.LocationResult

/** A scripted helper: every answer is a field the test sets; every call is counted. Opening a session fails the test. */
class FakeUsbHelper : UsbHelper {
    var components = AppleComponents.READY
    var devices: UsbResult<List<UsbDevice>> = UsbResult.Ok(listOf(PHONE))
    var probe: UsbResult<UsbProbe> = UsbResult.Ok(probeOf())
    var tools: UsbResult<ToolsStatus> = UsbResult.Ok(ToolsStatus(packageCached = true, imageMounted = true, serviceReady = true))
    var reveal: UsbResult<Unit> = UsbResult.Ok(Unit)
    var prepare: (FakeUsbHelper.(String, (DownloadProgress) -> Unit, Cancellation) -> UsbResult<Unit>) = { _, _, _ -> UsbResult.Ok(Unit) }
    val calls = mutableListOf<String>()

    fun count(name: String) = calls.count { it == name }

    override fun components(): AppleComponents = components.also { calls += "components" }
    override fun devices() = devices.also { calls += "devices" }
    override fun probe(udid: String) = probe.also { calls += "probe:$udid" }
    override fun revealDeveloperMode(udid: String) = reveal.also { calls += "reveal:$udid" }
    override fun toolsStatus(udid: String) = tools.also { calls += "tools:$udid" }
    override fun prepareTools(udid: String, progress: (DownloadProgress) -> Unit, cancel: Cancellation): UsbResult<Unit> {
        calls += "prepare:$udid"
        return prepare(udid, progress, cancel)
    }

    override fun openSession(udid: String): UsbResult<UsbSession> = throw AssertionError("setup never opens a session")
    override fun close() {}

    companion object {
        val PHONE = UsbDevice("00008110-AAAA", "Test iPhone", "18.1")
        val OTHER = UsbDevice("00008110-BBBB", "Other iPhone", "18.1")

        fun probeOf(
            udid: String = PHONE.udid, trusted: Boolean? = true, locked: Boolean? = false,
            developerMode: DeveloperModeStatus = DeveloperModeStatus.ON,
        ) = UsbProbe(udid, trusted, locked, developerMode, "18.1", imageMounted = true, serviceReady = true)
    }
}

/** Runs everything when the test says so; [call] and [within] answer at once. [late] makes [within] miss its deadline. */
class ManualExecutor : SetupExecutor {
    private val queue = ArrayDeque<() -> Unit>()
    var late = false
    var repeating = false
    private var tickTask: (() -> Unit)? = null

    override fun post(task: () -> Unit) { queue.addLast(task) }
    override fun repeat(periodMillis: Long, task: () -> Unit): AutoCloseable {
        repeating = true
        tickTask = task
        return AutoCloseable { repeating = false; tickTask = null }
    }
    override fun <T> call(timeoutMillis: Long, fallback: T, task: () -> T): T = task()
    override fun <T> within(deadlineMillis: Long, task: () -> T): T? = if (late) null else task()
    override fun close() {}

    /** Runs the queued work, and what it queues, until none is left. */
    fun drain() { while (queue.isNotEmpty()) queue.removeFirst()() }

    /** One poll of the watcher, as the timer would make it, and its work. */
    fun tick() { tickTask?.invoke(); drain() }
}

class SetupHarness(
    val helper: FakeUsbHelper = FakeUsbHelper(),
    var location: () -> LocationResult = { LocationResult.Found(GeoPoint(50.4501, 30.5234)) },
    var position: Boolean = true,
    var prepared: String? = FakeUsbHelper.PHONE.udid,
) {
    val exec = ManualExecutor()
    val finishes = mutableListOf<Triple<Long, UsbDevice, Boolean>>()
    var finishOutcome = CommandOutcome.ACCEPTED
    var changes = 0
    val controller = UsbSetupController(helper, { location() }, { position }, { prepared }, exec).also { c ->
        c.onFinish = { id, device, enable -> finishes += Triple(id, device, enable); finishOutcome }
        c.onChanged = { changes++ }
    }

    fun open(intent: SetupIntent = SetupIntent.PREPARE): Long {
        controller.open(intent)
        exec.drain()
        return controller.view().operationId!!
    }

    fun view() = controller.view()
    fun step(step: SetupStep): StepState = view().steps.getValue(step)
    fun status(step: SetupStep) = step(step).status
    fun detail(step: SetupStep) = step(step).detail
    fun done(vararg steps: SetupStep) = steps.all { status(it) == StepStatus.DONE }
    fun errorCode(code: UsbErrorCode) = code.key
}
