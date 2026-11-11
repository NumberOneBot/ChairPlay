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
import io.github.numberonebot.chairplay.gps.ReturnLocator
import io.github.numberonebot.chairplay.gps.ReturnPoint
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
    /** Runs on each probe call, to let a test act in the middle of a check. */
    var onProbe: () -> Unit = {}

    fun count(name: String) = calls.count { it == name }

    override fun components(): AppleComponents = components.also { calls += "components" }
    override fun devices() = devices.also { calls += "devices" }
    override fun probe(udid: String) = probe.also { calls += "probe:$udid"; onProbe() }
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
    private val tickers = mutableListOf<() -> Unit>()
    val repeating get() = tickers.isNotEmpty()
    val activeTickers get() = tickers.size
    /** The next [call] gives up at once with its fallback; its task stays queued and runs on [drain]. */
    var callTimesOut = false

    override fun post(task: () -> Unit) { queue.addLast(task) }
    override fun repeat(periodMillis: Long, task: () -> Unit): AutoCloseable {
        tickers += task
        return AutoCloseable { tickers.remove(task) }
    }
    override fun <T> call(timeoutMillis: Long, fallback: T, task: () -> T): T {
        if (!callTimesOut) return task()
        callTimesOut = false
        queue.addLast { task() }
        return fallback
    }
    override fun <T> within(deadlineMillis: Long, task: () -> T): T? = if (late) null else task()
    override fun close() {}

    /** Runs the queued work, and what it queues, until none is left. */
    fun drain() { while (queue.isNotEmpty()) queue.removeFirst()() }

    /** One poll of the watcher, as the timer would make it, and its work. */
    fun tick() { tickers.toList().forEach { it() }; drain() }
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
    /** The deadlines the setup asked Windows' place with. */
    val locateDeadlines = mutableListOf<Long>()
    // The app's shared locator, which keeps its own deadline: a late answer is its "timeout".
    private val locator = ReturnLocator { deadline ->
        locateDeadlines += deadline
        if (exec.late) ReturnPoint.Unavailable("timeout") else when (val found = location()) {
            is LocationResult.Found -> ReturnPoint.Found(found.place)
            LocationResult.Denied -> ReturnPoint.Unavailable("denied")
            LocationResult.Unknown -> ReturnPoint.Unavailable("unknown")
        }
    }
    val controller = UsbSetupController(helper, locator, { position }, { prepared }, exec).also { c ->
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
