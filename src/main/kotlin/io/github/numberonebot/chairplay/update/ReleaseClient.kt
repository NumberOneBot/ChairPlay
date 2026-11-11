package io.github.numberonebot.chairplay.update

import io.github.numberonebot.chairplay.ui.Json
import java.math.BigInteger
import java.net.URI
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse
import java.time.Duration

/**
 * ChairPlay versions: X.Y.Z, or a pre-release of it, X.Y.Z-<label><n> such as 1.1.0-rc1, optionally prefixed by v in
 * the GitHub tag. A pre-release comes before its own release and after every earlier one; labels compare by name
 * (beta before rc), then by number.
 */
internal data class ReleaseVersion(
    private val parts: List<BigInteger>,
    private val label: String? = null,
    private val number: BigInteger? = null,
) : Comparable<ReleaseVersion> {
    override fun compareTo(other: ReleaseVersion): Int =
        parts.zip(other.parts).firstNotNullOfOrNull { (a, b) -> a.compareTo(b).takeIf { it != 0 } }
            ?: when {
                label == null && other.label == null -> 0
                label == null -> 1
                other.label == null -> -1
                else -> label.compareTo(other.label).takeIf { it != 0 } ?: number!!.compareTo(other.number!!)
            }

    override fun toString() = parts.joinToString(".") + (label?.let { "-$it$number" } ?: "")

    companion object {
        private val FORMAT = Regex("v?(\\d+)\\.(\\d+)\\.(\\d+)(?:-([a-z]+)(\\d+))?")

        fun parse(value: String): ReleaseVersion {
            val match = requireNotNull(FORMAT.matchEntire(value)) { "Invalid release version" }
            val (major, minor, patch, label, number) = match.destructured
            return ReleaseVersion(
                listOf(major, minor, patch).map(::BigInteger),
                label.ifEmpty { null },
                number.ifEmpty { null }?.let(::BigInteger),
            )
        }
    }
}

/** GitHub's latest published, stable release; no account or token is needed for this public repository. */
internal class ReleaseClient(
    private val endpoint: URI = URI("https://api.github.com/repos/NumberOneBot/ChairPlay/releases/latest"),
    private val timeout: Duration = Duration.ofSeconds(15),
) {
    private val http by lazy { HttpClient.newBuilder().connectTimeout(timeout).build() }

    fun latest(): ReleaseVersion? {
        val request = HttpRequest.newBuilder(endpoint).timeout(timeout)
            .header("Accept", "application/vnd.github+json")
            .header("User-Agent", "ChairPlay-update-check")
            .GET().build()
        val response = http.send(request, HttpResponse.BodyHandlers.ofString())
        if (response.statusCode() == 404) return null
        check(response.statusCode() == 200) { "GitHub returned HTTP ${response.statusCode()}" }
        val release = Json.parse(response.body()) as? Map<*, *> ?: error("Invalid release response")
        check(release["draft"] == false && release["prerelease"] == false) { "Not a stable published release" }
        return ReleaseVersion.parse(release["tag_name"] as? String ?: error("Missing release version"))
    }
}
