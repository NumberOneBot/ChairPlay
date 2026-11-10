package io.github.numberonebot.chairplay.gps.setup

import io.github.numberonebot.chairplay.gps.DownloadProgress
import io.github.numberonebot.chairplay.gps.SetupIntent
import io.github.numberonebot.chairplay.gps.SetupStep
import io.github.numberonebot.chairplay.gps.StepState
import io.github.numberonebot.chairplay.gps.StepStatus
import io.github.numberonebot.chairplay.gps.UsbDevice
import io.github.numberonebot.chairplay.gps.UsbDeviceView
import io.github.numberonebot.chairplay.gps.UsbSetupView
import java.security.MessageDigest

/** One wizard run. Its results belong to [device] and to this operation; guarded by the store's lock. */
internal class SetupSession(val id: Long, val intent: SetupIntent) {
    /** Set when the operation ends: late work sees it and drops its result. */
    @Volatile var cancelled = false
    var device: UsbDevice? = null
    val steps: MutableMap<SetupStep, StepState> = SetupStep.entries.associateWith { StepState(StepStatus.UNKNOWN) }.toMutableMap()
    /** Developer Mode was revealed on this phone in this run. */
    var revealed = false
    var download: DownloadProgress? = null
    var windowsWarning = false
    var positionValid = false

    fun state(step: SetupStep) = steps.getValue(step)
    fun isDone(step: SetupStep) = state(step).status == StepStatus.DONE
    fun current(): SetupStep = SetupStep.entries.first { !isDone(it) }
}

/** The current wizard run and the view of it, readable from any thread. Work happens on the worker, never under the lock. */
internal class SetupStore(private val changed: () -> Unit) {
    private val lock = Any()
    private var current: SetupSession? = null
    private var generation = 0L
    private var nextId = 1L

    @Volatile var view: UsbSetupView = UsbSetupView.closed()
        private set

    fun begin(intent: SetupIntent, positionValid: Boolean): SetupSession? {
        val session = synchronized(lock) {
            if (current != null) return null
            SetupSession(nextId++, intent).also {
                it.positionValid = positionValid
                it.steps[SetupStep.USB] = StepState(StepStatus.CHECKING)
                current = it
                generation++
                publish(it)
            }
        }
        changed()
        return session
    }

    /** The run with this id, or null when it is not the current one. */
    fun find(id: Long): SetupSession? = synchronized(lock) { current?.takeIf { it.id == id } }

    fun <T> read(session: SetupSession, block: SetupSession.() -> T): T = synchronized(lock) { session.block() }

    /** Changes [session] unless it ended, or [udid] is no longer its phone; true when the change was made. */
    fun mutate(session: SetupSession, udid: String? = null, change: SetupSession.() -> Unit): Boolean {
        synchronized(lock) {
            if (current !== session || session.cancelled) return false
            if (udid != null && session.device?.udid != udid) return false
            session.change()
            publish(session)
        }
        changed()
        return true
    }

    /** Ends [session] (cancel or finish): its late work is dropped, the view closes. */
    fun end(session: SetupSession): Boolean {
        synchronized(lock) {
            if (current !== session) return false
            session.cancelled = true
            current = null
            generation++
            view = UsbSetupView.closed(generation)
        }
        changed()
        return true
    }

    private fun publish(s: SetupSession) {
        // Ready waits on the four steps before it.
        val ready = SetupStep.entries.take(4).all { s.isDone(it) }
        if (!ready && s.state(SetupStep.READY).status != StepStatus.UNKNOWN) s.steps[SetupStep.READY] = StepState(StepStatus.UNKNOWN)
        view = UsbSetupView(
            open = true, operationId = s.id, generation = generation, intent = s.intent,
            device = s.device?.let { UsbDeviceView(ref(it.udid), it.name) },
            steps = SetupStep.entries.associateWith { s.state(it) },
            current = s.current(), download = s.download,
            windowsWarning = s.windowsWarning, positionValid = s.positionValid,
        )
    }

    private fun ref(udid: String): String =
        MessageDigest.getInstance("SHA-256").digest(udid.toByteArray()).take(6).joinToString("") { "%02x".format(it) }
}
