package io.github.numberonebot.chairplay.update

import io.github.numberonebot.chairplay.config.SetupStore
import io.github.numberonebot.chairplay.config.SupportStore
import io.github.numberonebot.chairplay.config.readWindowState
import io.github.numberonebot.chairplay.config.writeWindowState
import java.nio.file.Path
import kotlin.concurrent.thread

/** A notification that ChairPlay was updated: its lines, and its buttons as (label, web page) in order. */
data class UpdateToast(
    val title: String,
    val body: String?,
    val attribution: String,
    val buttons: List<Pair<String, String>>,
    /** The page a click on the toast itself opens. */
    val open: String,
)

/**
 * Whether the first start of version [current] shows the update toast, after [stored] ran last (app.version.last).
 * Only a newer minor or major version, whatever the patches (1.1.4 to 1.2.1); a pre-release never, though the release
 * after it does; an install from before this feature ([stored] null, [setupDone]) as an update; a new install, a
 * development build ([current] null) or a downgrade never (owner, 2026-11-21).
 */
internal fun showsUpdateToast(stored: String?, current: String?, setupDone: Boolean): Boolean = runCatching {
    val now = ReleaseVersion.parse(current ?: return false)
    if (now.prerelease) return false
    val last = ReleaseVersion.parse(stored ?: return setupDone)
    now > last && (last.prerelease || !last.sameMinor(now))
}.getOrDefault(false)

/** The toast for [version]: the coffee ask when [ask], else the release's [highlight] line (owner's mock toast-b.png). */
fun updateToast(version: String, highlight: String?, ask: Boolean): UpdateToast {
    val whatsNew = tagged(releasePage(version), CAMPAIGN, version)
    val coffee = "☕ Buy me a coffee" to tagged(BUY_ME_A_COFFEE_PAGE, CAMPAIGN, version)
    return UpdateToast(
        title = "ChairPlay updated to $version",
        body = if (ask) ASK else highlight,
        attribution = "Made in Kyiv, Ukraine",
        buttons = listOfNotNull(coffee.takeIf { ask }, "What's new" to whatsNew),
        open = whatsNew,
    )
}

private const val CAMPAIGN = "update-toast"
private const val ASK = "One developer, between air-raid alerts and power cuts. It's free and always will be. " +
    "If it helps, a coffee keeps it going."

/**
 * At start, once the windows are open: compares this build's [version] with the one that ran last in [dir]'s
 * window.properties, stores this one, and has [show] tell of an update. A development build stores nothing.
 */
internal class UpdateAnnouncement(
    private val dir: Path,
    private val version: String?,
    private val highlight: String?,
    private val show: (UpdateToast) -> Unit,
    private val log: (String) -> Unit,
) {
    fun start() {
        val current = version ?: return
        thread(name = "update-toast", isDaemon = true) {
            runCatching {
                val stored = readWindowState(dir)?.getProperty(LAST)?.trim()
                val toast = showsUpdateToast(stored, current, SetupStore(dir).done())
                // A pre-update install has counted no CarPlay time: its first toast asks without waiting for it.
                val ask = toast && SupportStore(dir).asks(unknownTime = stored == null)
                writeWindowState(dir, listOf(LAST to current))
                if (toast) {
                    show(updateToast(current, highlight, ask))
                    log("updated from ${stored ?: "an earlier version"} to $current: toast shown${if (ask) ", with the coffee ask" else ""}")
                }
            }.onFailure { log("update toast not shown: $it") }
        }
    }

    private companion object {
        const val LAST = "app.version.last"
    }
}
