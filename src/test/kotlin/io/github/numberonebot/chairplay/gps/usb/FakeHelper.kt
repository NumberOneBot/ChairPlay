package io.github.numberonebot.chairplay.gps.usb

import io.github.numberonebot.chairplay.ui.Json
import java.io.ByteArrayOutputStream
import java.io.InputStream
import java.io.OutputStream
import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.LinkedBlockingQueue
import java.util.concurrent.TimeUnit

/** A one-way in-memory pipe that, unlike PipedInputStream, does not care which thread wrote last. */
class MemoryPipe {
    private val chunks = LinkedBlockingQueue<ByteArray>()
    private var current: ByteArray? = null
    private var index = 0
    @Volatile var closed = false
        private set

    val output: OutputStream = object : OutputStream() {
        override fun write(b: Int) = write(byteArrayOf(b.toByte()), 0, 1)
        override fun write(b: ByteArray, off: Int, len: Int) {
            if (closed) throw java.io.IOException("pipe closed")
            chunks.put(b.copyOfRange(off, off + len))
        }
        override fun close() = end()
    }

    val input: InputStream = object : InputStream() {
        override fun read(): Int {
            while (true) {
                val chunk = current
                if (chunk != null && index < chunk.size) return chunk[index++].toInt() and 0xff
                val next = chunks.take()
                if (next.isEmpty()) {
                    chunks.put(next)
                    return -1
                }
                current = next
                index = 0
            }
        }

        // Like a real pipe: what is there now, at least one byte, not a full buffer.
        override fun read(b: ByteArray, off: Int, len: Int): Int {
            if (len == 0) return 0
            val first = read()
            if (first < 0) return -1
            b[off] = first.toByte()
            var count = 1
            while (count < len) {
                val chunk = current ?: break
                if (index >= chunk.size) break
                b[off + count++] = chunk[index++]
            }
            return count
        }
    }

    fun end() {
        if (!closed) {
            closed = true
            chunks.put(ByteArray(0))
        }
    }
}

/**
 * A helper process played by the test: it says hello, reads each request and lets [answer] reply. Requests are kept
 * in [requests]; [answer] runs on the fake's own thread, as the real helper's replies arrive on its own.
 */
class FakeHelper(
    private val hello: Boolean = true,
    var answer: FakeHelper.(Map<String, Any?>) -> Unit = { standard(it) },
) : HelperPipes {
    private val toHelper = MemoryPipe()
    private val fromHelper = MemoryPipe()
    val requests = CopyOnWriteArrayList<Map<String, Any?>>()
    @Volatile var killed = false
    @Volatile var exited = false

    override val input: InputStream get() = fromHelper.input
    override val output: OutputStream get() = toHelper.output
    override val diagnostics: InputStream? = null

    init {
        Thread({
            if (hello) say(mapOf("type" to "hello", "protocol" to 2, "role" to "test"))
            val buffer = ByteArrayOutputStream()
            val stream = toHelper.input
            while (true) {
                val b = stream.read()
                if (b < 0) break
                if (b != '\n'.code) {
                    buffer.write(b)
                    continue
                }
                @Suppress("UNCHECKED_CAST")
                val request = Json.parse(buffer.toString(Charsets.UTF_8)) as Map<String, Any?>
                buffer.reset()
                requests += request
                answer(request)
            }
            onInputEnd()
        }, "fake-helper").apply { isDaemon = true }.start()
    }

    /** What the fake does at the end of its input; by default it ends like the real one. */
    var onInputEnd: () -> Unit = { exit() }

    fun say(line: Map<String, Any?>) = sayRaw(Json.write(line))

    fun sayRaw(text: String) {
        if (!fromHelper.closed) runCatching { fromHelper.output.write((text + "\n").toByteArray()) }
    }

    fun reply(request: Map<String, Any?>, result: Map<String, Any?> = emptyMap()) =
        say(mapOf("type" to "reply", "id" to request["id"], "ok" to true, "result" to result))

    fun fail(request: Map<String, Any?>, code: String) = say(
        mapOf("type" to "reply", "id" to request["id"], "ok" to false, "error" to mapOf("code" to code, "detail" to "test")),
    )

    fun event(name: String, fields: Map<String, Any?>) = say(mapOf("type" to "event", "event" to name) + fields)

    /** Replies hello and ping, and leaves the rest to the test. */
    fun standard(request: Map<String, Any?>): Boolean {
        when (request["op"]) {
            "hello" -> reply(request, mapOf("protocol" to 2))
            "ping" -> reply(request)
            else -> return false
        }
        return true
    }

    fun ops(): List<Any?> = requests.map { it["op"] }

    fun exit() {
        exited = true
        fromHelper.end()
    }

    override fun alive() = !exited && !killed

    override fun waitFor(millis: Long): Boolean {
        val end = System.nanoTime() + TimeUnit.MILLISECONDS.toNanos(millis)
        while (alive() && System.nanoTime() < end) Thread.sleep(5)
        return !alive()
    }

    override fun kill() {
        killed = true
        fromHelper.end()
        toHelper.end()
    }

    fun inputClosed() = toHelper.closed
}

/** Waits for [condition], failing after [millis]. */
fun eventually(millis: Long = 3_000, condition: () -> Boolean) {
    val end = System.nanoTime() + TimeUnit.MILLISECONDS.toNanos(millis)
    while (!condition()) {
        if (System.nanoTime() > end) throw AssertionError("condition not met in $millis ms")
        Thread.sleep(5)
    }
}
