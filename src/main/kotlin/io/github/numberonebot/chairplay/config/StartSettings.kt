package io.github.numberonebot.chairplay.config

import java.nio.file.Files
import java.nio.file.Path
import java.util.Properties
import kotlin.io.path.appendText
import kotlin.io.path.exists
import kotlin.io.path.readText
import kotlin.io.path.writeText

/**
 * [file] as the app's start reads it: written with [defaults] when it is missing; else a file from an older version
 * gains what it lacks of [SETTINGS_TEMPLATE], after its own lines, which stay as they were. [remedy] goes into the error
 * of a file that cannot be read.
 */
internal fun startSettings(file: Path, defaults: () -> String, remedy: String): Properties {
    if (!file.exists()) {
        Files.createDirectories(file.parent)
        file.writeText(defaults())
    } else {
        val saved = loadProperties(file, remedy)
        val missing = SETTINGS_TEMPLATE.mapNotNull { block ->
            block.entries.filter { (key, _) -> !saved.containsKey(key) }.takeIf { it.isNotEmpty() }?.let(block::text)
        }
        if (missing.isNotEmpty()) {
            val text = file.readText()
            file.appendText((if (text.isEmpty() || text.endsWith("\n")) "" else "\n") + missing.joinToString(""))
        }
    }
    return loadProperties(file, remedy)
}
