package io.github.numberonebot.chairplay

import io.github.numberonebot.chairplay.log.AppLog
import io.github.numberonebot.chairplay.platform.Platforms
import java.io.PrintWriter
import java.io.StringWriter
import java.nio.file.Path
import java.util.concurrent.atomic.AtomicBoolean
import kotlin.system.exitProcess

/**
 * Runs the app's [start]: its setup, then the window loop, which calls the `windowsOpen` it is given once every window
 * is open. The app has no console, so a failure before that (a bad setting, no OpenGL) would end it without a word: it
 * goes into [log] with its stack, a message box ([show]) gives the reason and the log file, and the process [exit]s with
 * code 1. A failure once the windows run goes on as before.
 */
internal fun guardStart(
    log: AppLog,
    show: (String) -> Unit = { Platforms.current.shell.showError(it) },
    exit: (Int) -> Unit = { exitProcess(it) },
    start: (windowsOpen: () -> Unit) -> Unit,
) {
    val open = AtomicBoolean()
    try {
        start { open.set(true) }
    } catch (error: Throwable) {
        if (open.get()) throw error
        val stack = StringWriter().also { error.printStackTrace(PrintWriter(it)) }.toString().trimEnd()
        log.error("ChairPlay could not start: $stack")
        runCatching { show(startFailureText(error, log.path)) }.onFailure { log.error("the message box did not show: $it") }
        exit(1)
    }
}

/** What the message box says: the reason, then where the details are ([logFile], null when no log file opened). */
internal fun startFailureText(error: Throwable, logFile: Path?): String {
    val reason = generateSequence(error) { it.cause }.firstNotNullOfOrNull { it.message?.trim()?.trimEnd('.')?.ifEmpty { null } }
        ?: error.javaClass.simpleName
    val details = if (logFile != null) "The details are in logs\\${logFile.fileName} in the app folder."
    else "No log file was written. Report this message at $ISSUES."
    return "ChairPlay could not start: $reason. $details"
}

private const val ISSUES = "https://github.com/NumberOneBot/ChairPlay/issues"
