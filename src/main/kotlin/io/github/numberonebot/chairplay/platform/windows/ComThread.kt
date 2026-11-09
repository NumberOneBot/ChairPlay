package io.github.numberonebot.chairplay.platform.windows

import com.sun.jna.platform.win32.COM.COMUtils
import com.sun.jna.platform.win32.Ole32
import java.util.concurrent.CompletableFuture
import java.util.concurrent.TimeUnit
import java.util.concurrent.TimeoutException

/**
 * Runs [work] on a daemon thread of its own with COM set up in [apartment] (Ole32.COINIT_*), and waits up to
 * [timeoutMillis] for it: a Windows call that hangs keeps that thread, never the caller, and the caller's own thread
 * keeps whatever apartment it has. Null when [work] threw or ran out of time.
 */
internal fun <T : Any> onComThread(
    name: String,
    timeoutMillis: Long,
    apartment: Int = Ole32.COINIT_MULTITHREADED,
    work: () -> T?,
): T? {
    val result = CompletableFuture<T?>()
    val thread = Thread({
        val initialized = Ole32.INSTANCE.CoInitializeEx(null, apartment)
        try {
            result.complete(work())
        } catch (_: Throwable) {
            result.complete(null)
        } finally {
            if (COMUtils.SUCCEEDED(initialized)) Ole32.INSTANCE.CoUninitialize()
        }
    }, name)
    thread.isDaemon = true
    thread.start()
    return try {
        result.get(timeoutMillis, TimeUnit.MILLISECONDS)
    } catch (_: TimeoutException) {
        null
    }
}
