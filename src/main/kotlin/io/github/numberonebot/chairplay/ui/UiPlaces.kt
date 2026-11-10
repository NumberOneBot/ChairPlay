package io.github.numberonebot.chairplay.ui

import io.github.numberonebot.chairplay.gps.DeveloperToolsStore
import io.github.numberonebot.chairplay.platform.DesktopShell
import io.github.numberonebot.chairplay.platform.Platforms
import io.github.numberonebot.chairplay.platform.SystemPage
import java.net.URLEncoder
import java.nio.file.Files

/**
 * The system pages, folders and web pages the screens link to, by the name the page gives, opened through [launch]:
 * Bluetooth or Wi-Fi in the system settings, the firewall in the system's security page or its console with the rules, location, the MFi,
 * logs, and screenshots folders (made first if they are missing), and the About page's: the project's pages on GitHub
 * (a new issue with [details] filled in), DiPlay's, xcertplay's, LIVI's, the HUD renderer's, and the third-party notices beside
 * the app ([setup]), in the system's text editor as the file has no extension. [about] and [systemVersion] name the build in a report.
 */
internal class UiPlaces(
    private val setup: SetupInfo,
    private val launch: (List<String>) -> Unit,
    private val log: (String) -> Unit,
    private val about: BuildInfo,
    private val systemVersion: () -> String,
    private val shell: DesktopShell = Platforms.current.shell,
) {
    /**
     * Opens [place]. Each but the notices goes through the shell's command, which hands it on: a settings address
     * opens its page, a web address the browser, and the console asks for the administrator's consent it needs. False
     * for a name the app does not know, or when the system did not start it.
     */
    fun open(place: String): Boolean {
        if (place !in PLACES) return false
        return runCatching {
            if (place == "notices") launch(shell.textFileCommand(setup.settingsFile.resolveSibling("THIRD_PARTY_NOTICES")))
            else launch(shell.openCommand(target(place)))
        }
            .onFailure { log("could not open $place: $it") }
            .isSuccess
    }

    private fun target(place: String): String = when (place) {
        "bluetooth" -> shell.systemPage(SystemPage.BLUETOOTH)
        "wifi" -> shell.systemPage(SystemPage.WIFI)
        "firewall" -> shell.systemPage(SystemPage.FIREWALL)
        "location" -> shell.systemPage(SystemPage.LOCATION)
        "firewall-rules" -> shell.systemPage(SystemPage.FIREWALL_RULES)
        "issues" -> "$PROJECT/issues/new?body=" +
            URLEncoder.encode("What happened, and what you expected:\n\n\n---\n$details", Charsets.UTF_8).replace("+", "%20")
        "discussions" -> "$PROJECT/discussions"
        "pull-requests" -> "$PROJECT/pulls"
        "author" -> "https://github.com/NumberOneBot"
        "buy-me-a-coffee" -> "https://www.buymeacoffee.com/alexstrelets"
        "source" -> PROJECT
        "releases" -> "$PROJECT/releases"
        "diplay" -> "https://github.com/shihabal3amri/DiPlay"
        "xcertplay" -> "https://github.com/shilapi/xcertplay"
        "livi" -> "https://github.com/f-io/LIVI"
        "rgi" -> "https://github.com/luka-dev/mib2q-carplay-rgi"
        "apple-devices" -> "https://apps.microsoft.com/detail/9NP83LWLPZ9K"
        "logs", "screenshots" -> Files.createDirectories(setup.settingsFile.resolveSibling(place)).toString()
        "developer-tools" -> Files.createDirectories(developerTools).toString()
        else -> Files.createDirectories(setup.mfiDirectory).toString()
    }

    /**
     * The logs and screenshots folders' sizes in bytes, the files in their subfolders too; a missing folder is 0 and
     * stays unmade, and a file that goes while they are added up counts nothing.
     */
    fun folderSizes(): Map<String, Long> = listOf("logs", "screenshots").associateWith { size(setup.settingsFile.resolveSibling(it)) } +
        // Developer tools only once a package is published in the store (its manifest is written last).
        (if (published()) mapOf("developerTools" to size(developerTools)) else emptyMap())

    // The USB GPS developer tools' store: cache/developer-tools beside the settings, as the helper uses it.
    private val developerTools get() = DeveloperToolsStore.root(setup.settingsFile.parent)

    private fun published(): Boolean = runCatching {
        Files.list(developerTools).use { folders -> folders.anyMatch { Files.isRegularFile(it.resolve(DeveloperToolsStore.MANIFEST)) } }
    }.getOrDefault(false)

    // Links are not followed: a size never counts what lies outside the folder.
    private fun size(folder: java.nio.file.Path): Long = if (!Files.isDirectory(folder)) 0L else runCatching {
        Files.walk(folder).use { files ->
            files.filter(Files::isRegularFile).mapToLong { file -> runCatching { Files.size(file) }.getOrDefault(0L) }.sum()
        }
    }.getOrDefault(0L)

    /**
     * What a report needs to say which ChairPlay it is about: the release version (or a development build) and its commit,
     * the DiPlay core, and Windows; no setting. Made once: none of them changes while the app runs.
     */
    val details: String by lazy {
        val core = "DiPlay core ${about.core?.let { "${it.id} (${it.date})" } ?: "not recorded"}"
        val windows = runCatching(systemVersion).getOrDefault("Windows")
        "${about.startLine()}\n$core\n$windows"
    }

    /** The About page's part of the state ([BuildInfo.toPage]), made once with [details]. */
    val aboutPage: Map<String, Any?> by lazy { about.toPage(details) }

    private companion object {
        /** The names the page may give ([open]). */
        val PLACES = listOf(
            "bluetooth", "wifi", "firewall", "firewall-rules", "mfi-folder", "location",
            "issues", "discussions", "pull-requests", "author", "source", "releases", "notices", "diplay", "xcertplay", "livi", "rgi",
            "logs", "screenshots", "buy-me-a-coffee", "developer-tools", "apple-devices",
        )

        /** The project's home on GitHub. */
        const val PROJECT = "https://github.com/NumberOneBot/ChairPlay"
    }
}
