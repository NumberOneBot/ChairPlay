package io.github.numberonebot.chairplay.gps

import java.util.concurrent.ExecutorService
import java.util.concurrent.ScheduledExecutorService
import java.util.concurrent.TimeUnit

/**
 * The one GPS operation at a time (S-09): it holds the app's input from [begin] to its end, which comes when its body
 * returns or throws, or at [deadlineMillis] whatever the body still waits for. A body's later steps ask [live] first:
 * an operation ended by the deadline changes nothing more.
 */
internal class GpsOperations(
    private val clock: () -> Long,
    private val hold: InputHold,
    private val worker: ExecutorService,
    private val watchdog: ScheduledExecutorService,
    private val deadlineMillis: Long,
    private val log: (String) -> Unit,
    private val changed: () -> Unit,
) {
    private var nextId = 1L

    @Volatile
    var current: GpsOperation? = null
        private set

    /** Starts an operation unless one runs: null when busy. The input is held from here. */
    fun begin(kind: OperationKind, generation: Long, target: GpsMethod?): GpsOperation? {
        val operation = synchronized(this) {
            if (current != null) return null
            GpsOperation(nextId++, generation, kind, OperationStage.QUIESCE, target, clock()).also { current = it }
        }
        hold.hold()
        log("GPS: ${kind.key} operation ${operation.id} started${target?.let { " for ${it.key}" }.orEmpty()}")
        changed()
        return operation
    }

    /** Runs [body] on the coordinator's thread; the operation ends after it, or at the deadline. */
    fun run(operation: GpsOperation, body: () -> Unit) {
        val timer = watchdog.schedule({
            if (finish(operation)) log("GPS: operation ${operation.id} gave up after $deadlineMillis ms; the app goes on")
        }, deadlineMillis, TimeUnit.MILLISECONDS)
        worker.execute {
            try {
                body()
            } catch (error: Exception) {
                log("GPS: operation ${operation.id} failed: $error")
            } finally {
                timer.cancel(false)
                finish(operation)
            }
        }
    }

    fun live(operation: GpsOperation) = current?.id == operation.id

    fun stage(operation: GpsOperation, stage: OperationStage) {
        synchronized(this) {
            val now = current?.takeIf { it.id == operation.id } ?: return
            current = now.copy(stage = stage)
        }
        changed()
    }

    /** Ends [operation] once: false when it had ended already. */
    private fun finish(operation: GpsOperation): Boolean {
        synchronized(this) {
            if (current?.id != operation.id) return false
            current = null
        }
        hold.release()
        log("GPS: operation ${operation.id} ended after ${clock() - operation.startedAtMillis} ms")
        changed()
        return true
    }
}
