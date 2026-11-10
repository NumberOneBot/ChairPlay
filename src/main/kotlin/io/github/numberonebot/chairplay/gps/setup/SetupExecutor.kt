package io.github.numberonebot.chairplay.gps.setup

import java.util.concurrent.Callable
import java.util.concurrent.Executors
import java.util.concurrent.ScheduledThreadPoolExecutor
import java.util.concurrent.TimeUnit
import java.util.concurrent.TimeoutException

/** The setup's one worker thread and its timing, so tests can run the work step by step. */
interface SetupExecutor : AutoCloseable {
    /** Queues [task] on the worker. */
    fun post(task: () -> Unit)

    /** Runs [task] on the worker again and again, each [periodMillis] after the last run ended. */
    fun repeat(periodMillis: Long, task: () -> Unit): AutoCloseable

    /** Runs [task] on the worker and waits; [fallback] when it did not finish in [timeoutMillis] (it runs on). */
    fun <T> call(timeoutMillis: Long, fallback: T, task: () -> T): T

    /** Runs a blocking [task] beside the worker; null when it is not done by the deadline (its answer is dropped). */
    fun <T> within(deadlineMillis: Long, task: () -> T): T?
}

class ThreadSetupExecutor(private val log: (String) -> Unit = {}) : SetupExecutor {
    @Volatile private var worker: Thread? = null
    private val pool = ScheduledThreadPoolExecutor(1) { r ->
        Thread(r, "usb-setup").also { it.isDaemon = true; worker = it }
    }
    private val side = Executors.newCachedThreadPool { r -> Thread(r, "usb-setup-side").also { it.isDaemon = true } }

    private fun guarded(task: () -> Unit) = Runnable {
        try { task() } catch (e: Throwable) { log("USB setup task failed: $e") }
    }

    override fun post(task: () -> Unit) { pool.execute(guarded(task)) }

    override fun repeat(periodMillis: Long, task: () -> Unit): AutoCloseable {
        val future = pool.scheduleWithFixedDelay(guarded(task), periodMillis, periodMillis, TimeUnit.MILLISECONDS)
        return AutoCloseable { future.cancel(false) }
    }

    override fun <T> call(timeoutMillis: Long, fallback: T, task: () -> T): T {
        if (Thread.currentThread() === worker) return task()
        val future = pool.submit(Callable { task() })
        return try {
            future.get(timeoutMillis, TimeUnit.MILLISECONDS)
        } catch (_: TimeoutException) {
            fallback
        } catch (e: Exception) {
            log("USB setup call failed: $e")
            fallback
        }
    }

    override fun <T> within(deadlineMillis: Long, task: () -> T): T? {
        val future = side.submit(Callable { task() })
        return try {
            future.get(deadlineMillis, TimeUnit.MILLISECONDS)
        } catch (_: TimeoutException) {
            future.cancel(true)
            null
        } catch (e: Exception) {
            null
        }
    }

    override fun close() {
        pool.shutdownNow()
        side.shutdownNow()
    }
}
