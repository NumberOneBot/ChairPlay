package io.github.numberonebot.chairplay.phone

import java.io.Closeable

/**
 * The connection to the phone, whatever its protocol: it brings the link up, reports how far it got, and every session
 * end or failure brings it up again until [stop] or [close].
 */
interface PhoneConnection : Closeable {
    /** Brings the link up, unless it is already. */
    fun start()

    /** Reconnect: ends the session or bring-up and starts the next. */
    fun restart()

    /** Disconnect: ends the session or bring-up, and a failed one's retry with it, until the next [start]. */
    fun stop()

    /** Hears each bring-up's progress, on the bring-up's threads. */
    var onStatus: (LinkStatus) -> Unit

    /** Hears the link that went live, and null once it ends; one ended by [restart] or [stop] ends unheard. */
    var onLink: (PhoneLink?) -> Unit
}
