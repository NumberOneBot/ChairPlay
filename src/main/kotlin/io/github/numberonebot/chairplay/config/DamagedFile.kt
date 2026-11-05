package io.github.numberonebot.chairplay.config

import java.nio.file.Path
import java.util.Properties
import kotlin.io.path.reader

/**
 * A file of the app's own that cannot be read, named in the message the start shows: [file], what is wrong with it
 * ([problem]), and what to do ([todo]), where Java's own message ("Input length = 1") named no file.
 */
internal class DamagedFileException(val file: Path, problem: String, todo: String, cause: Throwable? = null) :
    IllegalArgumentException("${file.fileName} $problem. $todo", cause)

/**
 * [file] as properties, or a [DamagedFileException] saying why not: a backslash starts an escape, and \u wants four
 * hexadecimal digits after it, which a Windows path typed with single backslashes (C:\users) lacks. [remedy] is the
 * other way out, as "delete it to get the defaults back". Bytes that are not UTF-8 read as U+FFFD, not as an error.
 */
internal fun loadProperties(file: Path, remedy: String): Properties = try {
    Properties().apply { file.reader().use(::load) }
} catch (error: IllegalArgumentException) {
    // Properties' only refusal: "Malformed \uxxxx encoding."
    throw DamagedFileException(file, "has a \\u without four hexadecimal digits after it",
        "Write each backslash in it as \\\\, or $remedy", error)
}
