package io.github.numberonebot.chairplay.update

import io.github.numberonebot.chairplay.ui.Json
import java.math.BigInteger
import java.net.URI
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse
import java.time.Duration

/** ChairPlay releases use X.Y.Z, optionally prefixed by v in the GitHub tag. */
internal data class ReleaseVersion(private val parts: List<BigInteger>) : Comparable<ReleaseVersion> {
    override fun compareTo(other: ReleaseVersion): Int =
        parts.zip(other.parts).firstNotNullOfOrNull { (a, b) -> a.compareTo(b).takeIf { it != 0 } } ?: 0

    override fun toString() = parts.joinToString(".")

    companion object {
        fun parse(value: String): ReleaseVersion {
            require(Regex("v?\\d+\\.\\d+\\.\\d+").matches(value)) { "Invalid release version" }
            return ReleaseVersion(value.removePrefix("v").split('.').map(::BigInteger))
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
