package io.github.numberonebot.chairplay.session

import io.github.numberonebot.chairplay.phone.ClusterControl

/**
 * What the iPhone is told its AltScreen view is for, as DiPlay tells it whether a car's cluster shows CarPlay's map
 * (CarPlayController.applyClusterUi): while the window shows the view, the iPhone is asked for it (showUI with its
 * URL); under the HUD or with the window closed it is told the view is not wanted (stopUI), so it stops drawing a
 * picture nobody sees, and keeps the URL for the next showUI. A stop is sent once per stream: each new AltScreen
 * stream starts with the iPhone drawing its initial view ([newStream]).
 */
class AltscreenUi {
    // The phone told to stop, until it is asked for the view again or a new stream starts.
    private var stopped: Any? = null

    /** Tells [phone] about [url], [seen] while the window shows it; true when the command went out. */
    @Synchronized
    fun tell(phone: ClusterControl, url: String, seen: Boolean, key: Any = phone): Boolean {
        if (seen) {
            if (stopped === key) stopped = null
            return phone.setClusterUrl(url, send = true)
        }
        val kept = phone.setClusterUrl(url, send = false)
        if (stopped === key) return kept
        val stop = phone.setClusterUiShown(false)
        if (stop) stopped = key
        return kept && stop
    }

    /** A new AltScreen stream draws its initial view until told otherwise. */
    @Synchronized
    fun newStream() {
        stopped = null
    }
}
