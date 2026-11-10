package io.github.numberonebot.chairplay.gps.usb

import java.io.InputStream
import java.io.OutputStream
import java.nio.file.Path
import java.util.concurrent.TimeUnit

/** The helper's two kinds of process: one control helper for discovery and setup, one per simulation session. */
enum class HelperRole(val key: String) { CONTROL("control"), SESSION("session") }

/** A started helper's pipes, and the means to end that process (and only it). */
interface HelperPipes {
    /** The helper's stdout: protocol lines only. */
    val input: InputStream

    /** The helper's stdin; closing it is the helper's cue to clean up and end. */
    val output: OutputStream

    /** The helper's stderr, bounded diagnostics; null when not kept. */
    val diagnostics: InputStream?

    fun alive(): Boolean

    /** Waits up to [millis] for the process to end; true when it has. */
    fun waitFor(millis: Long): Boolean

    /** Ends the helper process and any child it started, never anything else. */
    fun kill()
}

/** Starts a helper; throws IOException when it cannot. Injectable, so the tests play the helper in memory. */
fun interface HelperLauncher {
    fun launch(role: HelperRole): HelperPipes
}

/**
 * Starts the helper as a process: [command] and then `--role`, plus `--store` for the control role. The packaged app
 * runs the frozen helper from its image ([packaged]); a development run runs the package with the Python that has
 * the pinned pymobiledevice3 ([module], tools/usb-gps/.venv). The store is the app's data folder's
 * cache/developer-tools (DeveloperToolsStore.root), passed explicitly, never computed by the helper.
 */
class ProcessHelperLauncher(
    private val command: List<String>,
    private val store: Path,
    private val workingDirectory: Path?,
    private val extra: List<String> = emptyList(),
) : HelperLauncher {
    override fun launch(role: HelperRole): HelperPipes {
        val arguments = command + listOf("--role", role.key) +
            (if (role == HelperRole.CONTROL) listOf("--store", store.toString()) else emptyList()) + extra
        val builder = ProcessBuilder(arguments)
        workingDirectory?.let { builder.directory(it.toFile()) }
        builder.environment().apply {
            remove("PYTHONHOME")
            remove("PYTHONPATH")
            put("PYTHONDONTWRITEBYTECODE", "1")
        }
        return ProcessPipes(builder.start())
    }

    companion object {
        /** The frozen helper in the app image (PyInstaller --onedir, D7): one process, no Python needed. */
        fun packaged(executable: Path, store: Path) =
            ProcessHelperLauncher(listOf(executable.toString()), store, executable.parent)

        /** The helper package run by [python] from [toolsDirectory] (tools/usb-gps), for development. */
        fun module(python: Path, toolsDirectory: Path, store: Path) =
            ProcessHelperLauncher(listOf(python.toString(), "-s", "-m", "usb_gps_helper"), store, toolsDirectory)
    }
}

private class ProcessPipes(private val process: Process) : HelperPipes {
    override val input: InputStream = process.inputStream
    override val output: OutputStream = process.outputStream
    override val diagnostics: InputStream = process.errorStream

    override fun alive() = process.isAlive

    override fun waitFor(millis: Long) = process.waitFor(millis, TimeUnit.MILLISECONDS)

    override fun kill() {
        // A venv's python is a launcher that runs the real interpreter as its child: end the child too.
        process.descendants().forEach { it.destroyForcibly() }
        process.destroyForcibly()
    }
}
