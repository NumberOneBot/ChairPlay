package io.github.numberonebot.chairplay.ui

/**
 * The POSTs a page makes about itself rather than the app: `page` ({"visible": bool, "closing": bool?}), its
 * heartbeat, or that it goes; and `open-in-browser`, which the window's page alone makes while it holds the settings:
 * they are handed over to the tab [openHandOver] opens with the key it is given. Answers the status and its JSON.
 */
internal fun pagePost(
    lock: SettingsLock,
    route: String,
    page: String?,
    body: Map<*, *>,
    openHandOver: (String) -> Unit,
): Pair<Int, Map<String, Any?>> = when (route) {
    "page" -> {
        val visible = body["visible"] as? Boolean
        val closing = body["closing"]
        when {
            page == null -> 400 to mapOf("error" to "page missing")
            visible == null || (closing != null && closing !is Boolean) -> 400 to mapOf("error" to "visible missing")
            closing == true -> {
                lock.leave(page)
                200 to mapOf("ok" to true)
            }
            else -> {
                lock.report(page, visible)
                200 to mapOf("ok" to true)
            }
        }
    }
    "open-in-browser" -> {
        val key = if (page == SettingsLock.WINDOW) lock.startHandOver() else null
        if (key == null) {
            409 to mapOf("error" to "only the window holding the settings hands them over")
        } else {
            runCatching { openHandOver(key) }.fold(
                onSuccess = { 200 to mapOf("ok" to true) },
                onFailure = {
                    lock.cancelHandOver()
                    409 to mapOf("error" to "the browser did not open: $it")
                },
            )
        }
    }
    else -> 404 to mapOf("error" to "no such API")
}
