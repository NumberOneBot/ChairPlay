package io.github.numberonebot.chairplay.update

import io.github.numberonebot.chairplay.platform.Platforms
import java.io.Closeable
import java.time.LocalDate
import java.util.concurrent.atomic.AtomicBoolean
import kotlin.concurrent.thread

internal sealed interface UpdateResult {
    /** [latest] is newer than this build: version [current], or a development build from a commit of [built]. */
    data class Available(val current: String?, val latest: String, val built: LocalDate? = null) : UpdateResult
    data object Current : UpdateResult
    data object Failed : UpdateResult
    data object Development : UpdateResult
}

/**
 * [stored], the release the last check found (updates.latest), while it is newer than this build: than [installed],
 * its version; or, for a development build, which has none, when it was [published] after the day of the build's
 * commit, [built] (owner, 2026-11-12). Null for anything that is not a version or a day.
 */
internal fun newerRelease(stored: String?, installed: String?, published: String? = null, built: String? = null): String? {
    val found = stored?.trim()?.ifEmpty { null } ?: return null
    return runCatching {
        found.takeIf {
            if (installed != null) ReleaseVersion.parse(it) > ReleaseVersion.parse(installed)
            else published != null && built != null && LocalDate.parse(published.trim()) > LocalDate.parse(built.trim())
        }
    }.getOrNull()
}

/** The latest stable release's page: GitHub sends it on to the release itself (owner, 2026-11-12). */
internal const val LATEST_RELEASE_PAGE = "https://github.com/NumberOneBot/ChairPlay/releases/latest"

/**
 * One check/dialog at a time. Each check that reaches GitHub tells [remember] the release it found, so the About page
 * can offer it after the dialog is gone (owner, 2026-11-12). Startup is quiet unless an update exists; an explicit
 * check always reports its result. A build without a version ([current] null) made from a commit of [built] takes a
 * release published after that day for newer; without that day it has nothing to compare, and asks nothing.
 */
internal class UpdateChecks(
    private val current: String?,
    private val built: LocalDate? = null,
    private val lookup: () -> Release? = ReleaseClient()::latest,
    private val show: (UpdateResult) -> Boolean = Platforms.current::updateDialog,
    private val openReleases: () -> Unit = {
        ProcessBuilder(Platforms.current.shell.openCommand(LATEST_RELEASE_PAGE)).start()
    },
    private val remember: (Release) -> Unit = {},
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

    /** Called on the HTTP worker for Check now, or the startup worker, never the window's event loop. */
    fun check(manual: Boolean = true): Boolean {
        if (closed.get() || !busy.compareAndSet(false, true)) return false
        try {
            val result = try {
                if (current == null && built == null) UpdateResult.Development else outcome(lookup())
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

    /** What the release found, if any, is to this build; it is kept whatever it is. */
    private fun outcome(latest: Release?): UpdateResult {
        if (latest != null) runCatching { remember(latest) }.onFailure { log("Could not keep the release found: $it") }
        val newer = latest != null && if (current != null) latest.version > ReleaseVersion.parse(current)
            else latest.published != null && built != null && latest.published > built
        return when {
            newer -> UpdateResult.Available(current, latest!!.version.toString(), built.takeIf { current == null })
            current != null -> UpdateResult.Current
            else -> UpdateResult.Development
        }
    }

    override fun close() {
        closed.set(true)
        startup?.interrupt()
    }
}
