package io.github.numberonebot.chairplay.update

import io.github.numberonebot.chairplay.platform.Platforms
import java.io.Closeable
import java.util.concurrent.atomic.AtomicBoolean
import kotlin.concurrent.thread

internal sealed interface UpdateResult {
    data class Available(val current: String, val latest: String) : UpdateResult
    data object Current : UpdateResult
    data object Failed : UpdateResult
    data object Development : UpdateResult
}

/** One check/dialog at a time. Startup is quiet unless an update exists; an explicit check always reports its result. */
internal class UpdateChecks(
    private val current: String?,
    private val lookup: () -> ReleaseVersion? = ReleaseClient()::latest,
    private val show: (UpdateResult) -> Boolean = Platforms.current::updateDialog,
    private val openReleases: () -> Unit = {
        ProcessBuilder(Platforms.current.shell.openCommand("https://github.com/NumberOneBot/ChairPlay/releases")).start()
    },
    private val log: (String) -> Unit,
) : Closeable {
    private val busy = AtomicBoolean()
    private val started = AtomicBoolean()
    private val closed = AtomicBoolean()
    private var startup: Thread? = null

    fun start(enabled: Boolean) {
        if (enabled && !closed.get() && started.compareAndSet(false, true)) {
            startup = thread(name = "update-check", isDaemon = true) { check(manual = false) }
        }
    }

    /** Called on the HTTP worker for About, or the startup worker, never the window's event loop. */
    fun check(manual: Boolean = true): Boolean {
        if (closed.get() || !busy.compareAndSet(false, true)) return false
        try {
            val result = try {
                if (current == null) UpdateResult.Development
                else {
                    val installed = ReleaseVersion.parse(current)
                    val latest = lookup()
                    if (latest != null && latest > installed) UpdateResult.Available(current, latest.toString())
                    else UpdateResult.Current
                }
            } catch (error: Exception) {
                if (error is InterruptedException) Thread.currentThread().interrupt()
                log("Update check failed: $error")
                UpdateResult.Failed
            }
            if (!closed.get() && (manual || result is UpdateResult.Available)) {
                if (show(result) && !closed.get()) openReleases()
            }
            return true
        } catch (error: Exception) {
            log("Could not show update result or open releases: $error")
            return false
        } finally {
            busy.set(false)
        }
    }

    override fun close() {
        closed.set(true)
        startup?.interrupt()
    }
}
