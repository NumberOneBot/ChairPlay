package io.github.numberonebot.chairplay.ui

import java.io.StringReader
import java.util.Properties

/** A commit by its short id and the day it was made. */
data class Commit(val id: String, val date: String)

/**
 * Which build this is, for the About page: this repository's commit and the DiPlay core's (the upstream submodule),
 * as the build's buildInfo task wrote them into build-info.properties, and the release [version] (X.Y.Z) when the build was
 * given one. Each is null when the build did not record it.
 */
data class BuildInfo(val app: Commit?, val core: Commit?, val version: String? = null) {
    companion object {
        fun load(): BuildInfo {
            val text = BuildInfo::class.java.getResourceAsStream("/build-info.properties")?.use { String(it.readBytes(), Charsets.UTF_8) }
            return parse(text.orEmpty())
        }

        fun parse(text: String): BuildInfo {
            val values = Properties().apply { load(StringReader(text)) }
            fun commit(name: String): Commit? {
                val id = values.getProperty("$name.commit")?.trim()?.ifEmpty { null } ?: return null
                return Commit(id, values.getProperty("$name.date")?.trim().orEmpty())
            }
            return BuildInfo(commit("app"), commit("core"), values.getProperty("app.version")?.trim()?.ifEmpty { null })
        }
    }
}

/** The log's line on which build wrote it: "ChairPlay 0.1.0, commit 4e6d50df (2026-10-25)", or a development build. */
fun BuildInfo.startLine(): String {
    val build = version?.let { "ChairPlay $it" } ?: "ChairPlay development build"
    return "$build, commit ${app?.let { "${it.id} (${it.date})" } ?: "not recorded"}"
}

/** The About page's part of the UI state: the version, both commits as `{commit, date}`, and the report [details]. */
fun BuildInfo.toPage(details: String): Map<String, Any?> {
    fun commit(commit: Commit?) = commit?.let { mapOf("commit" to it.id, "date" to it.date) }
    return mapOf("version" to version, "app" to commit(app), "core" to commit(core), "details" to details)
}
