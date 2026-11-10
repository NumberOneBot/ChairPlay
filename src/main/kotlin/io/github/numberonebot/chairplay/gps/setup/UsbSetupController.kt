package io.github.numberonebot.chairplay.gps.setup

import io.github.numberonebot.chairplay.gps.Cancellation
import io.github.numberonebot.chairplay.gps.CommandOutcome
import io.github.numberonebot.chairplay.gps.DownloadProgress
import io.github.numberonebot.chairplay.gps.GpsReason
import io.github.numberonebot.chairplay.gps.SetupAction
import io.github.numberonebot.chairplay.gps.UsbErrorCode
import io.github.numberonebot.chairplay.gps.SetupIntent
import io.github.numberonebot.chairplay.gps.SetupStep
import io.github.numberonebot.chairplay.gps.StepState
import io.github.numberonebot.chairplay.gps.StepStatus
import io.github.numberonebot.chairplay.gps.UsbDevice
import io.github.numberonebot.chairplay.gps.UsbHelper
import io.github.numberonebot.chairplay.gps.UsbReadiness
import io.github.numberonebot.chairplay.gps.UsbResult
import io.github.numberonebot.chairplay.gps.UsbSetupPort
import io.github.numberonebot.chairplay.gps.UsbSetupView
import io.github.numberonebot.chairplay.platform.LocationResult

/**
 * The USB setup wizard over a [UsbHelper]. Everything the helper is asked runs on [executor]'s one worker; [view] is
 * safe from any thread. It never opens a session or sends a position: the final Enable or Done goes to [onFinish],
 * where the coordinator saves and starts.
 *
 * @param locate Windows' location, only to know whether a return point exists; asked with a deadline.
 * @param positionValid whether the car has a valid place now.
 * @param preparedDevice UDID saved as gps.usb.device, for [readiness].
 */
class UsbSetupController(
    private val helper: UsbHelper,
    locate: () -> LocationResult,
    private val positionValid: () -> Boolean,
    private val preparedDevice: () -> String?,
    private val executor: SetupExecutor = ThreadSetupExecutor(),
    private val pollMillis: Long = 2_000,
    locateDeadlineMillis: Long = 3_000,
    private val finishWaitMillis: Long = 30_000,
) : UsbSetupPort, AutoCloseable {
    override var onFinish: (operationId: Long, device: UsbDevice, enable: Boolean) -> CommandOutcome = { _, _, _ -> CommandOutcome.INVALID }
    override var onChanged: () -> Unit = {}

    private val store = SetupStore { onChanged() }
    private val flow = SetupFlow(helper, store, executor, locate, positionValid, locateDeadlineMillis)
    private val quick = QuickReadiness(helper)

    override fun view(): UsbSetupView = store.view

    override fun open(intent: SetupIntent): CommandOutcome {
        val s = store.begin(intent, positionValid()) { run -> executor.repeat(pollMillis) { tick(run) } }
            ?: return CommandOutcome.NO_CHANGE
        executor.post { flow.run(s, Stage.USB) }
        return CommandOutcome.ACCEPTED
    }

    override fun act(operationId: Long, action: SetupAction, step: SetupStep?): CommandOutcome {
        val s = store.find(operationId) ?: return CommandOutcome.STALE
        return when (action) {
            SetupAction.CANCEL -> cancel(s)
            SetupAction.REVEAL -> async(s, requiresDevice = true) { reveal(s) }
            SetupAction.TOOLS -> tools(s)
            SetupAction.RETRY -> if (step == null) CommandOutcome.INVALID else {
                async(s) {
                    store.mutate(s) { if (!isDone(step) && step != SetupStep.READY) steps[step] = StepState(StepStatus.CHECKING) }
                    flow.run(s, flow.stageOf(step))
                }
            }
            SetupAction.ENABLE, SetupAction.DONE -> complete(s, enable = action == SetupAction.ENABLE)
        }
    }

    override fun readiness(): UsbReadiness =
        // A slow helper is a helper problem, not a missing prerequisite: it must not open the wizard.
        executor.call(READINESS_WAIT_MILLIS, UsbReadiness.NotReady(GpsReason.USB_HELPER_FAILED, null)) {
            quick.check(preparedDevice())
        }

    /** Stops the worker and every timer; call at shutdown. */
    override fun close() = executor.close()

    /** Cancel wins over a finish that is still re-checking and is refused once the coordinator has the hand-over. */
    private fun cancel(s: SetupSession): CommandOutcome {
        val attempt = store.read(s) { finish }
        if (attempt != null && attempt.state.get() == FinishAttempt.COMMITTED) return CommandOutcome.BUSY
        attempt?.state?.compareAndSet(FinishAttempt.FINISHING, FinishAttempt.OVER)
        if (attempt?.state?.get() == FinishAttempt.COMMITTED) return CommandOutcome.BUSY
        store.end(s)
        return CommandOutcome.ACCEPTED
    }

    private fun async(s: SetupSession, requiresDevice: Boolean = false, work: () -> Unit): CommandOutcome {
        if (requiresDevice && store.read(s) { device } == null) return CommandOutcome.INVALID
        executor.post { if (!s.cancelled) work() }
        return CommandOutcome.ACCEPTED
    }

    // ---- Developer Mode: reveal only ----

    private fun reveal(s: SetupSession) {
        val udid = store.read(s) { device }?.udid ?: return
        when (val r = helper.revealDeveloperMode(udid)) {
            is UsbResult.Ok -> store.mutate(s, udid) { revealed = true; steps[SetupStep.DEVELOPER_MODE] = StepState(StepStatus.WAITING, "revealed") }
            is UsbResult.Failed -> when (r.code) {
                UsbErrorCode.NO_DEVICE, UsbErrorCode.LINK_LOST -> flow.lost(s)
                else -> store.mutate(s, udid) { steps[SetupStep.DEVELOPER_MODE] = StepState(StepStatus.ERROR, state(SetupStep.DEVELOPER_MODE).detail, r.code.key) }
            }
        }
    }

    // ---- Developer tools: download or connect ----

    private fun tools(s: SetupSession): CommandOutcome {
        val state = store.read(s) { state(SetupStep.TOOLS) }
        if (state.status == StepStatus.WORKING) return CommandOutcome.NO_CHANGE
        if (store.read(s) { device } == null || state.status != StepStatus.ACTION) return CommandOutcome.INVALID
        return async(s) { prepare(s) }
    }

    private fun prepare(s: SetupSession) {
        val udid = store.read(s) { device }?.udid ?: return
        // A double click queued two runs: only the first still finds the step waiting for the button.
        val before = store.read(s) { state(SetupStep.TOOLS) }
        if (before.status != StepStatus.ACTION) return
        val connecting = before.detail == "cached"
        if (!store.mutate(s, udid) { steps[SetupStep.TOOLS] = StepState(StepStatus.WORKING, if (connecting) "connecting" else "downloading") }) return
        val returned = java.util.concurrent.atomic.AtomicBoolean(false)
        val progress = { p: DownloadProgress ->
            if (!returned.get()) store.mutate(s, udid) {
                download = p
                if (p.totalBytes != null && p.receivedBytes >= p.totalBytes) steps[SetupStep.TOOLS] = StepState(StepStatus.WORKING, "connecting")
            }
            Unit
        }
        val result = helper.prepareTools(udid, progress, Cancellation { s.cancelled })
        returned.set(true)
        when (val r = result) {
            is UsbResult.Ok -> {
                store.mutate(s, udid) { download = null }
                flow.run(s, Stage.TOOLS)
            }
            is UsbResult.Failed -> {
                store.mutate(s, udid) { download = null }
                if (!s.cancelled) flow.toolsFailed(s, udid, r.code, if (connecting) "cached" else "missing")
            }
        }
    }

    // ---- Enable and Done ----

    /**
     * Re-checks everything, then hands over. A missing car place is not checked here: the coordinator records it and
     * starts later. Cancel and a caller that gave up waiting both stop the hand-over through [FinishAttempt].
     */
    private fun complete(s: SetupSession, enable: Boolean): CommandOutcome {
        val attempt = FinishAttempt()
        val taken = store.read(s) { if (finish?.active() == true) false else { finish = attempt; true } }
        if (!taken) return CommandOutcome.BUSY
        val outcome = executor.call<CommandOutcome?>(finishWaitMillis, null) {
            if (s.cancelled) return@call CommandOutcome.STALE
            val reached = flow.run(s, Stage.USB)
            val device = store.read(s) { device }
            val ready = reached && store.read(s) { SetupStep.entries.take(4).all { isDone(it) } }
            if (s.cancelled) return@call CommandOutcome.STALE
            if (!ready || device == null) { attempt.state.set(FinishAttempt.OVER); return@call CommandOutcome.INVALID }
            if (!attempt.state.compareAndSet(FinishAttempt.FINISHING, FinishAttempt.COMMITTED)) return@call CommandOutcome.STALE
            val result = onFinish(s.id, device, enable)
            attempt.state.set(FinishAttempt.OVER)
            if (result == CommandOutcome.ACCEPTED) store.end(s)
            result
        }
        if (outcome != null) return outcome
        // Gave up waiting: a task that has not handed over yet never will.
        attempt.state.compareAndSet(FinishAttempt.FINISHING, FinishAttempt.OVER)
        return CommandOutcome.BUSY
    }

    // ---- Watching the phone while the wizard is open ----

    /** One poll. Never overlaps work (one worker), and never retries a failure: only waiting steps are asked again. */
    private fun tick(s: SetupSession) {
        if (s.cancelled) return
        flow.refreshPosition(s)
        val (current, status) = store.read(s) { current().let { it to state(it).status } }
        when {
            status == StepStatus.WORKING -> {}
            current == SetupStep.USB && status != StepStatus.ERROR -> flow.run(s, Stage.USB)
            status == StepStatus.WAITING -> flow.run(s, Stage.USB)
            else -> flow.watch(s)
        }
    }
}

private const val READINESS_WAIT_MILLIS = 8_000L
