package io.github.numberonebot.chairplay.ui

import java.io.Closeable
import java.util.UUID

/**
 * Who holds the settings: ChairPlay's window or one browser tab, never both, so two places do not fight over the
 * state. The lock keeps the pages in step; it is not a security layer. A page is [WINDOW] (the main window's page),
 * [CONTROLS] (the controls window's), [SHARED] (a page opened with `?shared`), or a tab named by its own random id.
 * Requests without a page (scripts, curl) count as [SHARED]: they neither take the settings nor obey the lock.
 *
 * The window holds them by the app's state ([windowShows]), never by a stream of its own. A tab holds them while it
 * is present (an open event stream, its last report visible, heard from within [PRESENCE_MILLIS]) and nobody else
 * holds them. Nothing is held until the first [update].
 */
internal class SettingsLock(
    /** The window's page shows the settings or the start screen: WebView2 works, CarPlay does not cover it, no wait. */
    private val windowShows: () -> Boolean,
    /** Told after every change of holder or hand-over, so the pages hear of it. */
    private val changed: () -> Unit,
    private val now: () -> Long = { System.nanoTime() / 1_000_000 },
) {
    private class Tab(var visible: Boolean, var heard: Long) {
        var streams = 0
        /** When the tab became present, as a running number; 0 while it is not present. */
        var presentSince = 0L
    }

    private val tabs = HashMap<String, Tab>()
    private var holder: String? = null
    private var handOverKey: String? = null
    private var handOverStarted = 0L
    /** The tab whose stream carried [handOverKey]. */
    private var handOverTab: String? = null
    private var presentCount = 0L

    /** One event stream of [page]; closing it (once counts) ends it. [handOver] is the key "Open in browser" gave. */
    fun open(page: String, visible: Boolean, handOver: String? = null): Closeable {
        if (!isTab(page)) return Closeable {}
        val tab = change {
            val tab = tabs.getOrPut(page) { Tab(visible, now()) }
            tab.streams++
            tab.visible = visible
            tab.heard = now()
            if (handOver != null && handOver == handOverKey) handOverTab = page
            tab
        }
        var closed = false
        return Closeable {
            change {
                // A tab that left took its streams with it; their late ends must not count against a new one.
                if (!closed && tabs[page] === tab) tab.streams--
                closed = true
            }
        }
    }

    /** The page's heartbeat: whether it is visible. */
    fun report(page: String, visible: Boolean) {
        if (!isTab(page)) return
        change {
            val tab = tabs.getOrPut(page) { Tab(visible, now()) }
            tab.visible = visible
            tab.heard = now()
        }
    }

    /** The tab goes (reload, close): it lets go at once, whatever its streams still look like. */
    fun leave(page: String) {
        if (!isTab(page)) return
        change { tabs.remove(page) }
    }

    /** "Open in browser": the window lets go until the tab opened with the returned key takes them, or time runs out. */
    fun handOver(): String = change { beginHandOver() }

    /** [handOver], in one step with the check that the window holds them; null when it does not. */
    fun startHandOver(): String? = change { if (holder == WINDOW) beginHandOver() else null }

    private fun beginHandOver(): String {
        val key = UUID.randomUUID().toString()
        handOverKey = key
        handOverStarted = now()
        handOverTab = null
        return key
    }

    fun cancelHandOver() = change { endHandOver() }

    /** Re-evaluates: the app's changes (the window's screen) and each stream's tick call it. */
    fun update() = change {}

    val tabHolds: Boolean get() = synchronized(this) { holder.let { it != null && it != WINDOW } }

    /** The CarPlay car button shows the settings in the window only while no tab holds them; else it minimizes. */
    fun carButtonShowsSettings(embedded: Boolean): Boolean = embedded && !tabHolds

    /**
     * Whether [page] may save. The window may also while nobody holds them (CarPlay covers it, it waits): a field
     * saves on blur, and CarPlay covering the page is what blurs it. Not over a tab, nor during a hand-over.
     */
    fun mayWrite(page: String?): Boolean = page == null || !isLocked(page) || synchronized(this) {
        holder == page || (page == WINDOW && holder == null && handOverKey == null)
    }

    /** [page]'s view of the lock: whether it holds them, which kind of place does, whether it is shared, a hand-over. */
    fun view(page: String?): Map<String, Any?> = synchronized(this) {
        val holderKind = when (holder) {
            null -> null
            WINDOW -> WINDOW
            else -> "tab"
        }
        val shared = page == null || !isLocked(page)
        mapOf("mine" to (!shared && holder == page), "holder" to holderKind, "shared" to shared,
            "handOver" to (handOverKey != null))
    }

    /** Applies [mutation] and the rules under the lock, then tells the pages, outside it, if anything they show changed. */
    private fun <T> change(mutation: () -> T): T {
        val (result, different) = synchronized(this) {
            val before = holder to handOverKey
            val result = mutation()
            // Read under the lock: a read taken before it could undo a newer update's for a tick.
            evaluate(windowShows(), now())
            result to (before != (holder to handOverKey))
        }
        if (different) changed()
        return result
    }

    private fun evaluate(shows: Boolean, at: Long) {
        // 1. Who is present; tabs gone quiet with no stream are forgotten.
        tabs.values.removeAll { it.streams <= 0 && at - it.heard > PRESENCE_MILLIS }
        for (tab in tabs.values) {
            val present = tab.streams > 0 && tab.visible && at - tab.heard <= PRESENCE_MILLIS
            if (!present) tab.presentSince = 0 else if (tab.presentSince == 0L) tab.presentSince = ++presentCount
        }
        fun present(page: String?) = (tabs[page]?.presentSince ?: 0L) > 0
        // 2. A hand-over no tab took in time ends.
        if (handOverKey != null && at - handOverStarted > HAND_OVER_MILLIS) endHandOver()
        // 3. The holder keeps them while it may.
        val windowMay = shows && handOverKey == null
        holder = holder.takeIf { if (it == WINDOW) windowMay else present(it) }
        // 4. Free: the window if it may, else the tab the hand-over is for, else the tab present first.
        if (holder != null) return
        holder = when {
            windowMay -> WINDOW
            handOverKey != null -> handOverTab?.takeIf { present(it) }?.also { endHandOver() }
            else -> tabs.entries.filter { it.value.presentSince > 0 }.minByOrNull { it.value.presentSince }?.key
        }
    }

    private fun endHandOver() {
        handOverKey = null
        handOverTab = null
    }

    companion object {
        const val WINDOW = "window"
        const val CONTROLS = "controls"
        const val SHARED = "shared"
        const val PRESENCE_MILLIS = 5_000L
        const val HAND_OVER_MILLIS = 15_000L

        /** The POSTs that edit settings or act from a settings page; the others are app control. */
        val LOCKED_ROUTES = setOf("settings", "controls", "forget", "window-size", "altscreen-frame", "firewall-rule", "demo")

        private val PAGE = Regex("[A-Za-z0-9-]{1,64}")

        fun validPage(page: String): Boolean = PAGE.matches(page)

        /** The pages the lock applies to: the window and the tabs, not the controls window or shared pages. */
        private fun isLocked(page: String) = page != CONTROLS && page != SHARED

        private fun isTab(page: String) = isLocked(page) && page != WINDOW
    }
}
