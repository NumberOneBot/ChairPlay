package io.github.numberonebot.chairplay.gps.usb

import io.github.numberonebot.chairplay.drive.GeoPoint
import io.github.numberonebot.chairplay.gps.UsbErrorCode
import io.github.numberonebot.chairplay.gps.UsbResult
import io.github.numberonebot.chairplay.gps.UsbSession
import java.util.concurrent.ArrayBlockingQueue
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.atomic.AtomicReference

/**
 * One simulation session in a session helper of its own. [offer] only stores the newest point and wakes the sender
 * thread, which keeps at most one set in flight and sends the newest point when that set's reply is in: latest-wins,
 * no burst after a stall. [returnTo], [clear] and [close] go first: they drop the waiting point and are written
 * under the same lock the sender checks, so no point taken before them reaches the helper after them; the helper
 * then runs them after the set in flight. Every request names this session and generation, so nothing of it can act
 * on a later session.
 *
 * [onLost] runs once, on a thread of its own (never the reader's: its owner may be waiting on this session's reply
 * under the lock its handler takes), when the helper reports the phone gone, the helper ends, or a set's reply never
 * comes; the helper is then ended. Not after [close].
 */
internal class ProcessUsbSession(
    private val connection: HelperConnection,
    override val id: String,
    private val generation: Long,
    private val log: (String) -> Unit,
    private val timeouts: HelperTimeouts,
) : UsbSession {
    @Volatile private var acks = 0L
    override val acknowledged: Long get() = acks

    @Volatile override var onLost: (UsbErrorCode) -> Unit = {}

    private val latest = AtomicReference<GeoPoint?>()
    private val wake = ArrayBlockingQueue<Unit>(1)
    private val sendLock = Any()
    private val priority = AtomicInteger()
    private val closing = AtomicBoolean()
    private val lost = AtomicBoolean()
    @Volatile private var fenced = false
    @Volatile private var lastRequest = System.nanoTime()
    @Volatile private var replaced = 0L

    /** Closed or lost: its helper is ended or ending. */
    val finished: Boolean get() = closing.get() || lost.get()

    private val tag = mapOf("session" to id, "generation" to generation)

    fun start() {
        connection.onEvent = { event ->
            if (event["event"] == "lost" && event["session"] == id && event["generation"] == generation) {
                lose(UsbErrorCode.entries.firstOrNull { it.key == event["code"] } ?: UsbErrorCode.LINK_LOST)
            }
        }
        connection.onEnd = { lose(UsbErrorCode.HELPER_FAILED) }
        if (connection.ended) lose(UsbErrorCode.HELPER_FAILED)
        Thread(::pump, "usb-gps-session-$generation").apply { isDaemon = true }.start()
    }

    override fun offer(point: GeoPoint) {
        if (fenced || finished || !valid(point)) return
        latest.set(point)
        wake.offer(Unit)
    }

    override fun returnTo(point: GeoPoint): UsbResult<Unit> {
        if (!valid(point)) return UsbResult.Failed(UsbErrorCode.INVALID_COORDINATES, "not a place on Earth")
        return first("return", mapOf("latitude" to point.latitude, "longitude" to point.longitude), fence = true)
    }

    override fun clear(): UsbResult<Unit> = first("clear", emptyMap(), fence = false)

    override fun close() {
        if (!closing.compareAndSet(false, true)) return
        fenced = true
        latest.set(null)
        wake.offer(Unit)
        if (!lost.get() && connection.alive()) {
            val request = synchronized(sendLock) { connection.send("close", tag) }
            when (val result = request.await(timeouts.closeMillis)) {
                is UsbResult.Ok -> log("USB GPS session $generation closed: ${result.value["cleanup"]}, $acks sets, $replaced replaced")
                is UsbResult.Failed -> log("USB GPS session $generation close: ${result.code.key}")
            }
        }
        connection.close(timeouts.exitGraceMillis)
    }

    /** Ends this session's helper within [millis], whatever its state: before a new session may start. */
    fun awaitExit(millis: Long) = connection.close(millis)

    private fun first(op: String, fields: Map<String, Any?>, fence: Boolean): UsbResult<Unit> {
        if (closing.get()) return UsbResult.Failed(UsbErrorCode.CANCELLED, "the session is closed")
        if (lost.get()) return UsbResult.Failed(UsbErrorCode.LINK_LOST, "the session is lost")
        priority.incrementAndGet()
        try {
            if (fence) fenced = true
            latest.set(null)
            val request = synchronized(sendLock) { connection.send(op, tag + fields) }
            lastRequest = System.nanoTime()
            return when (val result = request.await(timeouts.priorityMillis)) {
                is UsbResult.Ok -> UsbResult.Ok(Unit)
                is UsbResult.Failed -> result
            }
        } finally {
            priority.decrementAndGet()
            wake.offer(Unit)
        }
    }

    private fun pump() {
        while (!finished) {
            wake.poll(timeouts.heartbeatMillis, TimeUnit.MILLISECONDS)
            if (finished) break
            val request = synchronized(sendLock) {
                if (priority.get() > 0 || fenced) null
                else latest.getAndSet(null)?.let { point ->
                    connection.send("set", tag + mapOf("latitude" to point.latitude, "longitude" to point.longitude))
                }
            }
            if (request == null) {
                if (System.nanoTime() - lastRequest >= TimeUnit.MILLISECONDS.toNanos(timeouts.heartbeatMillis)) heartbeat()
                continue
            }
            lastRequest = System.nanoTime()
            when (val result = request.await(timeouts.setMillis)) {
                is UsbResult.Ok -> if (result.value["applied"] == true) {
                    (result.value["acknowledged"] as? Long)?.let { acks = it }
                } else {
                    replaced++
                }
                is UsbResult.Failed -> when (result.code) {
                    UsbErrorCode.TIMEOUT, UsbErrorCode.LINK_LOST, UsbErrorCode.HELPER_FAILED, UsbErrorCode.PROTOCOL ->
                        lose(result.code)
                    else -> Unit
                }
            }
        }
    }

    private fun heartbeat() {
        lastRequest = System.nanoTime()
        val result = connection.call("ping", emptyMap(), timeouts.rpcMillis)
        if (result is UsbResult.Failed && !finished) {
            lose(if (result.code == UsbErrorCode.TIMEOUT) UsbErrorCode.TIMEOUT else UsbErrorCode.HELPER_FAILED)
        }
    }

    private fun lose(code: UsbErrorCode) {
        if (closing.get() || !lost.compareAndSet(false, true)) return
        fenced = true
        latest.set(null)
        wake.offer(Unit)
        log("USB GPS session $generation lost: ${code.key} after $acks sets")
        Thread({
            runCatching { onLost(code) }
            connection.close(timeouts.exitGraceMillis)
        }, "usb-gps-lost-$generation").apply { isDaemon = true }.start()
    }

    companion object {
        fun valid(point: GeoPoint) = point.latitude.isFinite() && point.longitude.isFinite() &&
            point.latitude in -90.0..90.0 && point.longitude in -180.0..180.0
    }
}
