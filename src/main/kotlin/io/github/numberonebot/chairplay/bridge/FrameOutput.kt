package io.github.numberonebot.chairplay.bridge

import io.github.numberonebot.chairplay.config.AreaRect
import io.github.numberonebot.chairplay.config.WindowSize
import io.github.numberonebot.chairplay.media.DecodedFrame
import io.github.numberonebot.chairplay.phone.ScreenKind
import java.io.Closeable
import java.nio.ByteBuffer
import java.util.concurrent.locks.ReentrantLock
import kotlin.concurrent.withLock

/** Sees each decoded picture of a screen stream before its window takes it, and when the stream starts and stops. */
interface FrameTap {
    /** On the decoder's thread; [frame]'s planes are valid only during the call, and the tap never releases it. */
    fun frame(screen: ScreenKind, frame: DecodedFrame)
    fun active(screen: ScreenKind, active: Boolean)

    /** Gives the tap a way to ask the phone for a new picture of the main screen; it may be called from any thread. */
    fun attach(requestPicture: () -> Unit) {}

    companion object {
        val NONE: FrameTap = object : FrameTap {
            override fun frame(screen: ScreenKind, frame: DecodedFrame) {}
            override fun active(screen: ScreenKind, active: Boolean) {}
        }
    }
}

/**
 * Publishes the main CarPlay screen for games while [enabled] (display.share). The decoder's thread converts each
 * picture into one of two BGRA buffers and leaves it for the worker, taking back the one still waiting if there is one:
 * the latest picture wins, and the decoder never waits on the worker or a game. The worker alone talks to [sink]: it
 * publishes, and between pictures wakes every 0.5 s to mark a picture over 1 s old frozen while the stream lasts.
 * The first failure is logged and ends publishing until [enabled] is turned off and on. Off holds no thread, buffer or
 * native side. The safe rect ([safe], in the pixels of [screen]) is scaled to a picture of another size.
 */
internal class FrameOutput(
    private val sink: FrameSink = NativeFrameSink,
    private val screen: () -> WindowSize,
    private val safe: () -> AreaRect,
    private val log: (String) -> Unit,
    private val nanoTime: () -> Long = System::nanoTime,
    /** Starts the loop on its thread and returns it; the tests return one never started and drive [pass] themselves. */
    private val worker: (Runnable) -> Thread = ::daemon,
    /** A fresh converter for each time sharing turns on. */
    private val converters: () -> PictureConverter = ::BgraConverter,
) : FrameTap, Closeable {
    /** One converted picture: written by the decoder's thread, then read by the worker, never both at once. */
    private class Slot {
        var buffer: ByteBuffer? = null
        var width = 0
        var height = 0

        fun fill(frame: DecodedFrame, converter: PictureConverter): Slot {
            val size = frame.width * frame.height * 4
            val out = buffer?.takeIf { it.capacity() == size } ?: ByteBuffer.allocateDirect(size).also { buffer = it }
            converter.convert(frame, out)
            width = frame.width
            height = frame.height
            return this
        }
    }

    // Guards the hand-off between the decoder's thread and the worker; nobody holds it during a conversion or a call.
    private val lock = ReentrantLock()
    private val wake = lock.newCondition()
    private val free = ArrayList<Slot>(2)
    private var pending: Slot? = null
    private var running = false
    private var streamStopped = false
    private var failure: String? = null

    // Keeps a conversion off the converter and buffers while sharing turns on or off.
    private val convertLock = Any()
    // A fresh one per start: a closed converter could miss its colour-space setup if swscale reused the address.
    private var converter: PictureConverter? = null

    @Volatile private var publishing = false
    @Volatile private var streamActive = false

    // The worker's own state; start and stop touch it only while no worker runs.
    private var sinkOpen = false
    private var lastPicture: Long? = null
    private var frozen = false

    private val toggleLock = Any()
    private var thread: Thread? = null
    @Volatile private var on = false

    var enabled: Boolean
        get() = on
        set(value) {
            var turnedOn = false
            synchronized(toggleLock) {
                if (value == on) return
                on = value
                if (value) start() else stop()
                turnedOn = value && publishing
            }
            // CarPlay sends nothing while its screen is still, so ask for a picture; with no lock of ours held.
            if (turnedOn && streamActive) requestPicture()
        }

    @Volatile private var requestPicture: () -> Unit = {}

    override fun attach(requestPicture: () -> Unit) {
        this.requestPicture = requestPicture
    }

    override fun frame(screen: ScreenKind, frame: DecodedFrame) {
        if (screen != ScreenKind.MAIN || !publishing) return
        synchronized(convertLock) {
            val converter = converter
            if (!publishing || converter == null) return
            // Two buffers suffice: the worker holds at most one, so the other is free or the one still waiting.
            val slot = lock.withLock { pending.also { pending = null } ?: free.removeLastOrNull() } ?: return
            // An Error too (out of direct or native memory): the decoder's thread must go on to show the picture.
            val filled = try {
                slot.fill(frame, converter)
            } catch (e: Throwable) {
                lock.withLock { free += slot; if (failure == null) failure = "cannot convert the picture: $e"; wake.signal() }
                return
            }
            lock.withLock { pending = filled; wake.signal() }
        }
    }

    override fun active(screen: ScreenKind, active: Boolean) {
        if (screen != ScreenKind.MAIN) return
        streamActive = active
        if (active || !publishing) return
        lock.withLock {
            pending?.let { free += it }
            pending = null
            streamStopped = true
            wake.signal()
        }
    }

    /**
     * One turn of the worker: waits up to [waitNanos] for something to do, then does it: tells a stopped stream, publishes
     * the waiting picture, or marks an old one frozen. False once the loop is to end.
     */
    internal fun pass(waitNanos: Long = WAKE_NANOS): Boolean {
        val job: Slot?
        val stopped: Boolean
        val failed: String?
        lock.withLock {
            var left = waitNanos
            while (running && pending == null && !streamStopped && failure == null && left > 0) left = wake.awaitNanos(left)
            if (!running) return false
            failed = failure.also { failure = null }
            stopped = streamStopped.also { streamStopped = false }
            job = pending.also { pending = null }
        }
        try {
            if (failed != null) return fail(failed)
            if (stopped) {
                sink.state(WAITING)
                lastPicture = null
            }
            if (job != null) {
                val error = sink.publish(checkNotNull(job.buffer), job.width, job.height, scaled(job.width, job.height))
                if (error != null) return fail(error)
                lastPicture = nanoTime()
                frozen = false
            } else if (!stopped) {
                freezeIfOld()
            }
            return true
        } catch (e: Throwable) {
            // An Error too: an ended worker would leave the status saying a picture flows.
            return fail(e.toString())
        } finally {
            if (job != null) lock.withLock { free += job }
        }
    }

    private fun freezeIfOld() {
        val last = lastPicture ?: return
        if (streamActive && !frozen && nanoTime() - last > FROZEN_NANOS) {
            sink.state(FROZEN)
            frozen = true
        }
    }

    /** [safe] from the display's pixels into a [width]x[height] picture's, edge by edge. */
    private fun scaled(width: Int, height: Int): AreaRect {
        val rect = safe()
        val display = screen()
        if (display.width == width && display.height == height) return rect
        fun along(value: Int, picture: Int, of: Int) = Math.round(value.toDouble() * picture / of).toInt()
        val left = along(rect.x, width, display.width)
        val top = along(rect.y, height, display.height)
        val right = along(rect.x + rect.width, width, display.width)
        val bottom = along(rect.y + rect.height, height, display.height)
        return AreaRect(left, top, right - left, bottom - top)
    }

    /** Logs [reason] and lets the native side go (state 0 for a game) until sharing is turned off and on; false. */
    private fun fail(reason: String): Boolean {
        log("picture sharing: $reason; the picture is no longer shared until sharing is turned off and on")
        publishing = false
        lock.withLock { running = false }
        closeSink()
        return false
    }

    private fun closeSink() {
        if (!sinkOpen) return
        sinkOpen = false
        runCatching { sink.close() }.onFailure { log("picture sharing: cannot close: $it") }
    }

    private fun start() {
        val error = runCatching { sink.open() }.getOrElse { it.toString() }
        if (error != null) {
            log("picture sharing: cannot share the picture: $error; turn sharing off and on to try again")
            return
        }
        sinkOpen = true
        lastPicture = null
        frozen = false
        synchronized(convertLock) {
            converter = converters()
            lock.withLock {
                free.clear()
                free += Slot()
                free += Slot()
                pending = null
                streamStopped = false
                failure = null
                running = true
            }
            publishing = true
        }
        thread = worker(Runnable { while (pass()) Unit })
        log("picture sharing: sharing the picture")
    }

    private fun stop() {
        publishing = false
        lock.withLock { running = false; wake.signal() }
        thread?.join()
        thread = null
        synchronized(convertLock) {
            converter?.close()
            converter = null
            lock.withLock { free.clear(); pending = null }
        }
        if (sinkOpen) log("picture sharing: picture no longer shared")
        closeSink()
    }

    override fun close() {
        enabled = false
    }

    companion object {
        /** The default worker: the loop on a daemon thread of its own. */
        fun daemon(loop: Runnable): Thread = Thread(loop, "game-bridge").apply { isDaemon = true; start() }

        private const val WAITING = 1
        private const val FROZEN = 3
        private const val WAKE_NANOS = 500_000_000L
        private const val FROZEN_NANOS = 1_000_000_000L
    }
}
