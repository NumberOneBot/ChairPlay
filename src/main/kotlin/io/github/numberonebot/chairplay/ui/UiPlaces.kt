package io.github.numberonebot.chairplay.ui

import java.net.URLEncoder
import java.nio.file.Files
import java.nio.file.Path

/**
 * The Windows pages, folders and web pages the screens link to, by the name the page gives, opened through [launch]:
 * Bluetooth or Wi-Fi in Settings, the firewall in Windows Security or its console with the rules, location, the MFi,
 * logs, and screenshots folders (made first if they are missing), and the About page's: the project's pages on GitHub
 * (a new issue with [details] filled in), DiPlay's, xcertplay's, LIVI's, the HUD renderer's, and the third-party notices beside
 * the app ([setup]), in Notepad as the file has no extension. [about] and [windowsVersion] name the build in a report.
 */
internal class UiPlaces(
    private val setup: SetupInfo,
    private val launch: (List<String>) -> Unit,
    private val log: (String) -> Unit,
    private val about: BuildInfo,
    private val windowsVersion: () -> String,
) {
    /**
     * Opens [place]. Each but the notices goes through explorer.exe, which hands it to the shell: a Settings address
     * opens its page, a web address the browser, and the console asks for the administrator's consent it needs. False
     * for a name the app does not know, or when Windows did not start it.
     */
    fun open(place: String): Boolean {
        if (place !in PLACES) return false
        return runCatching {
            if (place == "notices") launch(listOf("notepad.exe", setup.settingsFile.resolveSibling("THIRD_PARTY_NOTICES").toString()))
            else launch(listOf("explorer.exe", target(place)))
        }
            .onFailure { log("could not open $place: $it") }
            .isSuccess
    }

    private fun target(place: String): String = when (place) {
        "bluetooth" -> "ms-settings:bluetooth"
        "wifi" -> "ms-settings:network-wifi"
        "firewall" -> "windowsdefender://network/"
        "location" -> "ms-settings:privacy-location"
        "firewall-rules" -> Path.of(System.getenv("SystemRoot") ?: "C:/Windows", "System32", "wf.msc").toString()
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
        "logs", "screenshots" -> Files.createDirectories(setup.settingsFile.resolveSibling(place)).toString()
        else -> Files.createDirectories(setup.mfiDirectory).toString()
    }

    /**
     * The logs and screenshots folders' sizes in bytes, the files in their subfolders too; a missing folder is 0 and
     * stays unmade, and a file that goes while they are added up counts nothing.
     */
    fun folderSizes(): Map<String, Long> = listOf("logs", "screenshots").associateWith { name ->
        val folder = setup.settingsFile.resolveSibling(name)
        if (!Files.isDirectory(folder)) 0L
        else runCatching {
            Files.walk(folder).use { files ->
                files.filter(Files::isRegularFile).mapToLong { file -> runCatching { Files.size(file) }.getOrDefault(0L) }.sum()
            }
        }.getOrDefault(0L)
    }

    /**
     * What a report needs to say which ChairPlay it is about: the release version (or a development build) and its commit,
     * the DiPlay core, and Windows; no setting. Made once: none of them changes while the app runs.
     */
    val details: String by lazy {
        val core = "DiPlay core ${about.core?.let { "${it.id} (${it.date})" } ?: "not recorded"}"
        val windows = runCatching(windowsVersion).getOrDefault("Windows")
        "${about.startLine()}\n$core\n$windows"
    }

    /** The About page's part of the state ([BuildInfo.toPage]), made once with [details]. */
    val aboutPage: Map<String, Any?> by lazy { about.toPage(details) }

    private companion object {
        /** The names the page may give ([open]). */
        val PLACES = listOf(
            "bluetooth", "wifi", "firewall", "firewall-rules", "mfi-folder", "location",
            "issues", "discussions", "pull-requests", "author", "source", "releases", "notices", "diplay", "xcertplay", "livi", "rgi",
            "logs", "screenshots", "buy-me-a-coffee",
        )

        /** The project's home on GitHub. */
        const val PROJECT = "https://github.com/NumberOneBot/ChairPlay"
    }
}
