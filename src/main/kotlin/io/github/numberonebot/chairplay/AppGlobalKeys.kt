package io.github.numberonebot.chairplay

import io.github.numberonebot.chairplay.input.*
import io.github.numberonebot.chairplay.platform.Platforms
import org.lwjgl.glfw.GLFW.*

/** Native producer -> bounded router -> existing control actions on the window thread. */
internal class AppGlobalKeys(
    private val config: () -> ControlsConfig,
    private val session: () -> Any?,
    private val send: (Int, Control?, Any?) -> Unit,
    private val drive: (Int, Int, Int) -> Boolean,
    private val changed: () -> Unit,
    private val factory: (GlobalKeyboardCallbacks) -> GlobalKeyboardSource = Platforms.current::globalKeyboard,
) : AutoCloseable {
    private val router = GlobalKeyRouter()
    private val leases = KeyboardFocusLease()
    private val keys = KeyControls(config)
    private val held = HashMap<Int, Boolean>()
    private var applied: ControlsConfig? = null
    private var previousSession: Any? = null
    private var source: GlobalKeyboardSource? = null
    @Volatile private var enabled = false
    @Volatile private var ready = false
    @Volatile private var failure: String? = null
    @Volatile private var closed = false
    @Volatile private var generation = 0L
    var wake: () -> Unit = {}

    fun owns(key: Int) = enabled && ready && router.owns(key)
    fun state(): Map<String, Any?> = mapOf("enabled" to (enabled && ready), "error" to failure)
    /** A page's renewals keep its lease without waking the loop: only the page taking the focus releases holds. */
    fun focus(client: String, focused: Boolean) {
        if (leases.update(client, focused)) reset()
    }
    fun reset() { router.reset(); wake() }

    @Synchronized fun pass(): Double? {
        if (closed) return null
        val now = config()
        val sameSession = previousSession === session()
        if (applied !== now || !sameSession) {
            router.reset()
            releaseHeld(sameSession)
            router.drain()
            previousSession = session()
        }
        if (applied !== now) {
            applied = now
            enabled = now.globalEnabled
            if (!enabled) {
                generation++
                source?.close(); source = null; ready = false; failure = null
                router.clear()
            } else if (source == null) {
                val current = ++generation
                val callbacks = GlobalKeyboardCallbacks(
                    key = { key, phase, mods, background ->
                        if (generation == current) {
                            // Enter and Escape, pinned to Select and Back, stay with the app that has the focus.
                            val global = background && key !in ControlsConfig.PINNED_KEYS
                            router.key(key, phase, mods, enabled && ready && global && !leases.active())
                            wake()
                        }
                    },
                    reset = { if (generation == current) reset() },
                    ready = { if (generation == current) { ready = true; failure = null; changed() } },
                    failed = { problem -> if (generation == current) { ready = false; failure = problem; reset(); changed() } },
                )
                try { source = factory(callbacks).also { it.start() } }
                catch (problem: Throwable) { callbacks.failed(problem.message ?: "Cannot start global shortcuts") }
            }
            changed()
        }
        if (router.takeOverflow() && enabled) {
            ready = false
            failure = "Global shortcuts stopped: too many pending keys. Turn the switch off and on to restart."
            generation++
            source?.close()
            releaseHeld(previousSession === session())
            router.clear()
            changed()
        }
        if (leases.active()) router.reset()
        for (event in router.drain()) {
            val (key, phase, mods) = event
            if (phase == GLFW_PRESS) held[key] = drive(key, phase, mods)
            else if (held[key] == true) drive(key, phase, mods)
            if (held[key] == false) send(key, keys.key(key, phase, mods), previousSession)
            if (phase == GLFW_RELEASE) held.remove(key)
            // An action such as reconnect can invalidate the rest of this batch.
            if (previousSession !== session()) { router.reset(); releaseHeld(false); break }
        }
        // Nothing here is due later: each key, reset and failure wakes the loop, and a saved binding repaints the window.
        return null
    }

    private fun releaseHeld(deliver: Boolean) {
        for ((key, driving) in held) {
            if (driving) drive(key, GLFW_RELEASE, 0)
            val released = keys.key(key, GLFW_RELEASE, 0)
            if (deliver) send(key, released, previousSession)
        }
        held.clear()
    }

    @Synchronized override fun close() {
        closed = true; enabled = false
        generation++
        source?.close(); source = null
        router.reset()
        releaseHeld(previousSession === session())
        router.clear()
        wake = {}
    }
}
