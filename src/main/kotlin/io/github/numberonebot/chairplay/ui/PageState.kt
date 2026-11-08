package io.github.numberonebot.chairplay.ui

/**
 * The state one page gets: the app's ([base]), with `embedded` and `waiting` true only for the window's own page (a
 * tab is never in the window, nor waits there, black), its view of the settings lock, and the server's fields.
 */
internal fun pageState(base: Map<String, Any?>, page: String?, lock: SettingsLock, server: Map<String, Any?>): Map<String, Any?> {
    val window = page == SettingsLock.WINDOW
    return base + mapOf(
        "embedded" to (window && base["embedded"] == true),
        "waiting" to (window && base["waiting"] == true),
        "lock" to lock.view(page),
        "uiServer" to server,
    )
}
