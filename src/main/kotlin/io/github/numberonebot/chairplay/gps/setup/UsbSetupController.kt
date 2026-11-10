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
    private var ticker: AutoCloseable? = null

    override fun view(): UsbSetupView = store.view

    override fun open(intent: SetupIntent): CommandOutcome {
        val s = store.begin(intent, positionValid = positionValid()) ?: return CommandOutcome.NO_CHANGE
        ticker = executor.repeat(pollMillis) { tick(s) }
        executor.post { flow.run(s, Stage.USB) }
        return CommandOutcome.ACCEPTED
    }

    override fun act(operationId: Long, action: SetupAction, step: SetupStep?): CommandOutcome {
        val s = store.find(operationId) ?: return CommandOutcome.STALE
        return when (action) {
            SetupAction.CANCEL -> { finish(s); CommandOutcome.ACCEPTED }
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
        executor.call(READINESS_WAIT_MILLIS, UsbReadiness.NotReady(GpsReason.USB_NOT_READY, SetupStep.TOOLS)) {
            quick.check(preparedDevice())
        }

    /** Ends the wizard and the timer; call at shutdown. */
    override fun close() {
        ticker?.close()
        executor.close()
    }

    private fun finish(s: SetupSession) {
        if (store.end(s)) { ticker?.close(); ticker = null }
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
        return async(s) { prepare(s, connecting = state.detail == "cached") }
    }

    private fun prepare(s: SetupSession, connecting: Boolean) {
        val udid = store.read(s) { device }?.udid ?: return
        if (!store.mutate(s, udid) { steps[SetupStep.TOOLS] = StepState(StepStatus.WORKING, if (connecting) "connecting" else "downloading") }) return
        val progress = { p: DownloadProgress ->
            store.mutate(s, udid) {
                download = p
                if (p.totalBytes != null && p.receivedBytes >= p.totalBytes) steps[SetupStep.TOOLS] = StepState(StepStatus.WORKING, "connecting")
            }
            Unit
        }
        when (val r = helper.prepareTools(udid, progress, Cancellation { s.cancelled })) {
            is UsbResult.Ok -> {
                store.mutate(s, udid) { download = null }
                flow.run(s, Stage.TOOLS)
            }
            is UsbResult.Failed -> {
                store.mutate(s, udid) { download = null }
                if (!s.cancelled) flow.toolsFailed(s, udid, r.code)
            }
        }
    }

    // ---- Enable and Done ----

    private fun complete(s: SetupSession, enable: Boolean): CommandOutcome =
        executor.call(finishWaitMillis, CommandOutcome.BUSY) {
            if (s.cancelled) return@call CommandOutcome.STALE
            flow.run(s, Stage.USB)
            val device = store.read(s) { device }
            val ready = store.read(s) { device != null && SetupStep.entries.take(4).all { isDone(it) } }
            if (s.cancelled) return@call CommandOutcome.STALE
            if (!ready || device == null) return@call CommandOutcome.INVALID
            // Enable needs a car place; Done only closes the preparation.
            if (enable && !store.read(s) { positionValid }) return@call CommandOutcome.INVALID
            val outcome = onFinish(s.id, device, enable)
            if (outcome == CommandOutcome.ACCEPTED) finish(s)
            outcome
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

private const val READINESS_WAIT_MILLIS = 15_000L
