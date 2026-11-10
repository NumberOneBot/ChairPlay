package io.github.numberonebot.chairplay.gps

import io.github.numberonebot.chairplay.platform.LocationResult
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.StandardCopyOption
import java.util.concurrent.CompletableFuture
import java.util.concurrent.TimeUnit
import java.util.concurrent.TimeoutException

/**
 * The cleanup journal as a file, cache/usb-gps-cleanup.json in the app's data folder: the UDID of the iPhone a USB
 * simulation of this app may still run on, and nothing else (no points, no pairing). Written whole through a temporary
 * file, so a crash leaves the old record or the new one.
 */
class FileCleanupJournal(private val file: Path, private val log: (String) -> Unit = {}) : CleanupJournal {
    @Volatile private var udid: String? = read()

    override fun dirty(): String? = udid

    @Synchronized
    override fun mark(udid: String) {
        if (this.udid == udid) return
        write("{\"udid\":\"${udid.filter { it.isLetterOrDigit() || it == '-' }}\"}")
        this.udid = udid
    }

    @Synchronized
    override fun clear() {
        if (udid == null) return
        runCatching { Files.deleteIfExists(file) }.onFailure { log("GPS: cleanup journal not deleted: $it") }
        udid = null
    }

    private fun read(): String? = runCatching {
        if (!Files.exists(file)) return null
        Regex("\"udid\"\\s*:\\s*\"([A-Za-z0-9-]+)\"").find(Files.readString(file))?.groupValues?.get(1)
    }.getOrNull()

    private fun write(text: String) {
        runCatching {
            Files.createDirectories(file.parent)
            val temporary = file.resolveSibling(file.fileName.toString() + ".tmp")
            Files.writeString(temporary, text)
            Files.move(temporary, file, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE)
        }.onFailure { log("GPS: cleanup journal not written: $it") }
    }

    companion object {
        fun at(dataDirectory: Path) = dataDirectory.resolve("cache").resolve("usb-gps-cleanup.json")
    }
}

/**
 * Windows' place within a deadline (V47): the system's read blocks up to about 20 s, so it runs on a thread of its own,
 * one at a time; a locate while one is out waits for that one rather than starting another, and an answer after the
 * deadline is dropped. A finished read is never reused: each locate asks anew.
 */
class DeadlineLocator(private val read: () -> LocationResult) : ReturnLocator {
    private var pending: CompletableFuture<LocationResult>? = null

    override fun locate(deadlineMillis: Long): ReturnPoint {
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
