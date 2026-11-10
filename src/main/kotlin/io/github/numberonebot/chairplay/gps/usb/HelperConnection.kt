package io.github.numberonebot.chairplay.gps.usb

import io.github.numberonebot.chairplay.gps.UsbErrorCode
import io.github.numberonebot.chairplay.gps.UsbResult
import io.github.numberonebot.chairplay.ui.Json
import java.io.BufferedInputStream
import java.io.ByteArrayOutputStream
import java.io.IOException
import java.util.concurrent.CompletableFuture
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.ExecutionException
import java.util.concurrent.TimeUnit
import java.util.concurrent.TimeoutException
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicLong

/**
 * The client side of the helper's JSON-lines protocol 2 (tools/usb-gps/usb_gps_helper/protocol.py) over one helper's
 * pipes. Requests from any thread are multiplexed by id: each waits for its own reply with its own deadline, so a
 * minutes-long prepare never holds a discovery. One reader thread takes every line; a line that is not the protocol
 * ends the helper. Callbacks ([onEvent], progress, [onEnd]) run on that reader thread and must not block.
 */
internal class HelperConnection(
    private val pipes: HelperPipes,
    private val name: String,
    private val log: (String) -> Unit = {},
) {
    private val pending = ConcurrentHashMap<String, Pending>()
    private val counter = AtomicLong()
    private val writeLock = Any()
    private val hello = CompletableFuture<Map<String, Any?>>()
    private val endedFlag = AtomicBoolean()
    @Volatile private var violation: String? = null

    @Volatile var onEvent: (Map<String, Any?>) -> Unit = {}
    @Volatile var onEnd: () -> Unit = {}

    val ended: Boolean get() = endedFlag.get()

    /** One request's reply, awaited with a deadline. */
    inner class Pending(val id: String, val op: String, internal val progress: ((Map<String, Any?>) -> Unit)?) {
        internal val future = CompletableFuture<Map<String, Any?>?>()

        fun await(millis: Long): UsbResult<Map<String, Any?>> =
            poll(millis) ?: abandon().let { UsbResult.Failed(UsbErrorCode.TIMEOUT, "no reply to $op in $millis ms") }

        /** The reply if it came within [millis], else null and the request still waits. */
        fun poll(millis: Long): UsbResult<Map<String, Any?>>? = try {
            outcome(future.get(millis, TimeUnit.MILLISECONDS))
        } catch (_: TimeoutException) {
            null
        } catch (_: ExecutionException) {
            outcome(null)
        }

        /** Stops waiting; a reply that still comes is dropped. */
        fun abandon() {
            pending.remove(id)
        }
    }

    /** Reads the helper's hello and answers it; a helper of another protocol or one that stays silent is ended. */
    fun start(helloMillis: Long): UsbResult<Unit> {
        Thread(::read, "$name-reader").apply { isDaemon = true }.start()
        pipes.diagnostics?.let { stream -> Thread({ drain(stream) }, "$name-stderr").apply { isDaemon = true }.start() }
        val greeting = try {
            hello.get(helloMillis, TimeUnit.MILLISECONDS)
        } catch (_: TimeoutException) {
            pipes.kill()
            return UsbResult.Failed(UsbErrorCode.HELPER_FAILED, "$name said no hello in $helloMillis ms")
        } catch (_: ExecutionException) {
            return UsbResult.Failed(UsbErrorCode.HELPER_FAILED, "$name ended before its hello")
        }
        if (greeting["protocol"] != PROTOCOL.toLong()) {
            pipes.kill()
            return UsbResult.Failed(UsbErrorCode.PROTOCOL, "$name speaks protocol ${greeting["protocol"]}, not $PROTOCOL")
        }
        return when (val reply = call("hello", mapOf("protocol" to PROTOCOL), helloMillis)) {
            is UsbResult.Ok -> UsbResult.Ok(Unit)
            is UsbResult.Failed -> reply.also { pipes.kill() }
        }
    }

    fun send(op: String, fields: Map<String, Any?>, progress: ((Map<String, Any?>) -> Unit)? = null): Pending {
        val request = Pending("r${counter.incrementAndGet()}", op, progress)
        if (ended) return request.also { it.future.complete(null) }
        pending[request.id] = request
        val line = Json.write(linkedMapOf<String, Any?>("id" to request.id, "op" to op) + fields) + "\n"
        val written = synchronized(writeLock) {
            try {
                pipes.output.write(line.toByteArray(Charsets.UTF_8))
                pipes.output.flush()
                true
            } catch (_: IOException) {
                false
            }
        }
        if (!written || ended) {
            pending.remove(request.id)
            request.future.complete(null)
        }
        return request
    }

    fun call(op: String, fields: Map<String, Any?>, millis: Long): UsbResult<Map<String, Any?>> = send(op, fields).await(millis)

    /** Ends the input (the helper's cue to clean up and end), waits up to [graceMillis], then ends the process. */
    fun close(graceMillis: Long) {
        synchronized(writeLock) { runCatching { pipes.output.close() } }
        if (!pipes.waitFor(graceMillis)) pipes.kill()
    }

    fun kill() = pipes.kill()

    fun alive() = pipes.alive() && !ended

    private fun outcome(reply: Map<String, Any?>?): UsbResult<Map<String, Any?>> {
        if (reply == null) {
            return if (violation != null) UsbResult.Failed(UsbErrorCode.PROTOCOL, violation)
            else UsbResult.Failed(UsbErrorCode.HELPER_FAILED, "$name ended")
        }
        if (reply["ok"] == true) {
            @Suppress("UNCHECKED_CAST")
            return UsbResult.Ok(reply["result"] as? Map<String, Any?> ?: emptyMap())
        }
        val error = reply["error"] as? Map<*, *>
        val key = error?.get("code") as? String
        return UsbResult.Failed(UsbErrorCode.entries.firstOrNull { it.key == key } ?: UsbErrorCode.HELPER_FAILED,
            (error?.get("detail") as? String) ?: key)
    }

    private fun read() {
        val stream = BufferedInputStream(pipes.input)
        val line = ByteArrayOutputStream()
        try {
            while (violation == null) {
                val b = stream.read()
                if (b < 0) break
                if (b == '\n'.code) {
                    handle(line.toString(Charsets.UTF_8).trimEnd('\r'))
                    line.reset()
                } else if (line.size() >= MAX_LINE) {
                    violate("a line over $MAX_LINE bytes")
                } else {
                    line.write(b)
                }
            }
        } catch (_: IOException) {
        }
        end()
    }

    private fun handle(text: String) {
        @Suppress("UNCHECKED_CAST")
        val message = runCatching { Json.parse(text) as? Map<String, Any?> }.getOrNull() ?: return violate("a line that is not JSON")
        when (message["type"]) {
            "hello" -> hello.complete(message)
            "reply" -> when (val id = message["id"] as? String) {
                null -> log("$name refused a request: ${(message["error"] as? Map<*, *>)?.get("detail")}")
                else -> pending.remove(id)?.future?.complete(message)
            }
            "event" -> {
                val progress = (message["id"] as? String)?.let { pending[it]?.progress }
                runCatching { if (progress != null) progress(message) else onEvent(message) }
            }
            else -> violate("a message of type ${message["type"]}")
        }
    }

    private fun violate(why: String) {
        violation = why
        log("$name broke the protocol: $why; ending it")
        pipes.kill()
    }

    private fun end() {
        if (!endedFlag.compareAndSet(false, true)) return
        hello.completeExceptionally(IOException("$name ended"))
        pending.values.forEach { it.future.complete(null) }
        pending.clear()
        runCatching { onEnd() }
    }

    private fun drain(stream: java.io.InputStream) {
        var lines = 0
        runCatching {
            stream.bufferedReader(Charsets.UTF_8).forEachLine { text ->
                if (lines++ < DIAGNOSTIC_LINES) log("$name: ${text.take(300)}")
            }
        }
    }

    companion object {
        const val PROTOCOL = 2
        const val MAX_LINE = 1 shl 16
        private const val DIAGNOSTIC_LINES = 40
    }
}
