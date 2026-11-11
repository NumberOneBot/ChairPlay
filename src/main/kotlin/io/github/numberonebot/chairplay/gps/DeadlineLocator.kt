package io.github.numberonebot.chairplay.gps

import io.github.numberonebot.chairplay.platform.LocationResult
import java.util.concurrent.CompletableFuture
import java.util.concurrent.TimeUnit
import java.util.concurrent.TimeoutException

/**
 * Windows' place within a deadline (V47): the system's read blocks up to about 20 s, so it runs on a thread of its own,
 * one at a time; a locate while one is out waits for that one rather than starting another, and an answer after the
 * deadline is dropped. A finished read is never reused: each locate asks anew.
 */
class DeadlineLocator(
    /** Says why there was no place (denied, unknown, timeout), for a report that shows it. */
    private val log: (String) -> Unit = {},
    private val read: () -> LocationResult,
) : ReturnLocator {
    private var pending: CompletableFuture<LocationResult>? = null

    override fun locate(deadlineMillis: Long): ReturnPoint =
        ask(deadlineMillis).also { if (it is ReturnPoint.Unavailable) log("GPS: no Windows location (${it.reason})") }

    private fun ask(deadlineMillis: Long): ReturnPoint {
        val future = synchronized(this) {
            pending?.takeIf { !it.isDone } ?: CompletableFuture.supplyAsync(read, { Thread(it, "gps-locate").apply { isDaemon = true }.start() })
                .also { pending = it }
        }
        return try {
            when (val found = future.get(deadlineMillis, TimeUnit.MILLISECONDS)) {
                is LocationResult.Found -> ReturnPoint.Found(found.place)
                LocationResult.Denied -> ReturnPoint.Unavailable("denied")
                LocationResult.Unknown -> ReturnPoint.Unavailable("unknown")
            }
        } catch (_: TimeoutException) {
            ReturnPoint.Unavailable("timeout")
        } catch (_: Exception) {
            ReturnPoint.Unavailable("unknown")
        }
    }
}
