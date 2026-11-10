package io.github.numberonebot.chairplay.gps.usb

import io.github.numberonebot.chairplay.gps.AppleComponents
import io.github.numberonebot.chairplay.gps.Cancellation
import io.github.numberonebot.chairplay.gps.DownloadProgress
import io.github.numberonebot.chairplay.gps.ToolsStatus
import io.github.numberonebot.chairplay.gps.UsbDevice
import io.github.numberonebot.chairplay.gps.UsbErrorCode
import io.github.numberonebot.chairplay.gps.UsbHelper
import io.github.numberonebot.chairplay.gps.UsbProbe
import io.github.numberonebot.chairplay.gps.UsbResult
import io.github.numberonebot.chairplay.gps.UsbSession
import java.util.concurrent.CompletableFuture
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicLong
import java.util.concurrent.atomic.AtomicReference

/**
 * [UsbHelper] over helper processes ([launcher]): one control helper, started on first use and again after it ends,
 * for discovery and setup; and one session helper per [openSession], ended with its session. Every method is
 * thread-safe and bounded: requests to the control helper are multiplexed by id, so [devices] (polled each second)
 * answers while a download runs, and it never waits for a helper that is still starting. A new session helper starts
 * only once the previous one has ended, so a late clear of an old session can never reach the new one.
 */
class ProcessUsbHelper(
    private val launcher: HelperLauncher,
    private val log: (String) -> Unit = {},
    private val timeouts: HelperTimeouts = HelperTimeouts(),
) : UsbHelper {
    private val controlLock = Any()
    private var control: HelperConnection? = null
    private var starting: CompletableFuture<UsbResult<HelperConnection>>? = null
    private var retryAt = 0L
    @Volatile private var closed = false

    private val sessionLock = Any()
    private var live: ProcessUsbSession? = null
    private val generations = AtomicLong(System.currentTimeMillis())

    override fun components(): AppleComponents = when (val result = rpc("components", emptyMap(), timeouts.rpcMillis)) {
        is UsbResult.Ok -> AppleComponents.entries.firstOrNull { it.key == result.value["state"] } ?: AppleComponents.UNKNOWN
        is UsbResult.Failed -> AppleComponents.UNKNOWN
    }

    override fun devices(): UsbResult<List<UsbDevice>> =
        rpc("discover", emptyMap(), timeouts.discoverMillis, wait = false).parse { result ->
            (result["devices"] as List<*>).map { item ->
                val device = item as Map<*, *>
                UsbDevice(device["udid"] as String, device["name"] as? String ?: "iPhone", device["ios"] as? String)
            }
        }

    override fun probe(udid: String): UsbResult<UsbProbe> =
        rpc("probe", mapOf("udid" to udid), timeouts.probeMillis).parse { HelperResults.probe(udid, it) }

    override fun revealDeveloperMode(udid: String): UsbResult<Unit> =
        rpc("reveal", mapOf("udid" to udid), timeouts.revealMillis).parse { }

    override fun toolsStatus(udid: String): UsbResult<ToolsStatus> =
        rpc("tools-status", mapOf("udid" to udid), timeouts.probeMillis).parse {
            ToolsStatus(it["packageCached"] as Boolean, it["imageMounted"] as Boolean, it["serviceReady"] as Boolean)
        }

    override fun prepareTools(udid: String, progress: (DownloadProgress) -> Unit, cancel: Cancellation): UsbResult<Unit> {
        val connection = when (val started = control(wait = true)) {
            is UsbResult.Failed -> return started
            is UsbResult.Ok -> started.value
        }
        val news = AtomicReference<DownloadProgress?>()
        val activity = AtomicLong(System.nanoTime())
        val request = connection.send("prepare", mapOf("udid" to udid)) { event ->
            activity.set(System.nanoTime())
            HelperResults.download(event)?.let(news::set)
        }
        var shown: DownloadProgress? = null
        val idle = TimeUnit.MILLISECONDS.toNanos(timeouts.prepareIdleMillis)
        while (true) {
            val reply = request.poll(POLL_MILLIS)
            // Progress reaches the caller on its own thread, never the helper's reader.
            news.get()?.takeIf { it != shown }?.let {
                shown = it
                runCatching { progress(it) }
            }
            if (reply != null) return reply.parse { }
            val timedOut = System.nanoTime() - activity.get() > idle
            if (cancel.cancelled() || timedOut) {
                connection.call("cancel", mapOf("target" to request.id), timeouts.rpcMillis)
                val late = request.poll(timeouts.cancelGraceMillis)
                request.abandon()
                if (late is UsbResult.Ok) return UsbResult.Ok(Unit)
                return if (timedOut) UsbResult.Failed(UsbErrorCode.TIMEOUT, "no progress in ${timeouts.prepareIdleMillis} ms")
                else UsbResult.Failed(UsbErrorCode.CANCELLED, "cancelled")
            }
        }
    }

    override fun openSession(udid: String): UsbResult<UsbSession> = synchronized(sessionLock) {
        if (closed) return UsbResult.Failed(UsbErrorCode.HELPER_FAILED, "the USB helper is closed")
        live?.let { previous ->
            if (!previous.finished) return UsbResult.Failed(UsbErrorCode.PROTOCOL, "a USB GPS session is already open")
            previous.awaitExit(timeouts.exitGraceMillis)
            live = null
        }
        val generation = generations.incrementAndGet()
        val id = "s$generation"
        val pipes = try {
            launcher.launch(HelperRole.SESSION)
        } catch (error: Exception) {
            log("USB GPS session helper did not start: ${error.message}")
            return UsbResult.Failed(UsbErrorCode.HELPER_FAILED, error.message)
        }
        val connection = HelperConnection(pipes, "usb-session-$generation", log)
        connection.start(timeouts.helloMillis).let { if (it is UsbResult.Failed) return it }
        val opened = connection.call("open", mapOf("udid" to udid, "session" to id, "generation" to generation), timeouts.openMillis)
        if (opened is UsbResult.Failed) {
            log("USB GPS session not opened: ${opened.code.key}")
            connection.close(timeouts.exitGraceMillis)
            return opened
        }
        val session = ProcessUsbSession(connection, id, generation, log, timeouts)
        session.start()
        live = session
        UsbResult.Ok(session)
    }

    override fun close() {
        closed = true
        synchronized(sessionLock) { live?.close() }
        val connection = synchronized(controlLock) { control.also { control = null } }
        connection?.close(timeouts.exitGraceMillis)
    }

    private fun rpc(op: String, fields: Map<String, Any?>, millis: Long, wait: Boolean = true): UsbResult<Map<String, Any?>> =
        when (val started = control(wait)) {
            is UsbResult.Failed -> started
            is UsbResult.Ok -> started.value.call(op, fields, millis)
        }

    /** The running control helper; one is started when there is none, and [wait] says whether to wait for it. */
    private fun control(wait: Boolean): UsbResult<HelperConnection> {
        val future = synchronized(controlLock) {
            if (closed) return UsbResult.Failed(UsbErrorCode.HELPER_FAILED, "the USB helper is closed")
            control?.let { if (it.alive()) return UsbResult.Ok(it) else control = null }
            starting ?: run {
                if (System.nanoTime() - retryAt < 0) {
                    return UsbResult.Failed(UsbErrorCode.HELPER_FAILED, "the USB helper failed; it starts again shortly")
                }
                CompletableFuture<UsbResult<HelperConnection>>().also { future ->
                    starting = future
                    Thread({ future.complete(startControl()) }, "usb-helper-start").apply { isDaemon = true }.start()
                }
            }
        }
        if (!wait && !future.isDone) return UsbResult.Failed(UsbErrorCode.HELPER_FAILED, "the USB helper is starting")
        return future.get()
    }

    private fun startControl(): UsbResult<HelperConnection> {
        val result: UsbResult<HelperConnection> = try {
            val connection = HelperConnection(launcher.launch(HelperRole.CONTROL), "usb-helper", log)
            when (val started = connection.start(timeouts.helloMillis)) {
                is UsbResult.Ok -> UsbResult.Ok(connection)
                is UsbResult.Failed -> started
            }
        } catch (error: Exception) {
            UsbResult.Failed(UsbErrorCode.HELPER_FAILED, error.message)
        }
        synchronized(controlLock) {
            starting = null
            when {
                result is UsbResult.Ok && closed -> result.value.kill()
                result is UsbResult.Ok -> control = result.value
                result is UsbResult.Failed -> {
                    retryAt = System.nanoTime() + TimeUnit.MILLISECONDS.toNanos(timeouts.restartBackoffMillis)
                    log("USB GPS helper did not start: ${result.code.key} ${result.detail ?: ""}")
                }
            }
        }
        return result
    }

    private companion object {
        const val POLL_MILLIS = 100L
    }
}

/** A reply's result read as [T]; a result of the wrong shape is a protocol failure. */
internal fun <T> UsbResult<Map<String, Any?>>.parse(read: (Map<String, Any?>) -> T): UsbResult<T> = when (this) {
    is UsbResult.Failed -> this
    is UsbResult.Ok -> try {
        UsbResult.Ok(read(value))
    } catch (error: RuntimeException) {
        UsbResult.Failed(UsbErrorCode.PROTOCOL, "unexpected reply: ${error.javaClass.simpleName}")
    }
}
