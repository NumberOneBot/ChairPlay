package io.github.numberonebot.chairplay.gps

/** The notices the GPS page shows, one of each code at a time; one with an end goes at it. Safe from any thread. */
internal class GpsNotices(private val clock: () -> Long) {
    private var next = 1L
    private val shown = mutableListOf<GpsNotice>()

    /** Shows [code] anew, in place of one showing, for [forMillis] or until dropped. */
    @Synchronized
    fun show(code: NoticeCode, tone: NoticeTone, args: Map<String, String> = emptyMap(), forMillis: Long? = null) {
        shown.removeAll { it.code == code }
        shown += GpsNotice(next++, tone, code, args, forMillis?.let { clock() + it })
    }

    /** Keeps [code] showing while [on], as it is when it already shows; drops it otherwise. */
    @Synchronized
    fun keep(code: NoticeCode, tone: NoticeTone, on: Boolean) {
        if (!on) drop(code) else if (shown.none { it.code == code }) show(code, tone)
    }

    @Synchronized
    fun drop(vararg codes: NoticeCode) {
        shown.removeAll { it.code in codes }
    }

    @Synchronized
    fun current(): List<GpsNotice> {
        val now = clock()
        shown.removeAll { notice -> notice.expiresAtMillis?.let { it <= now } == true }
        return shown.toList()
    }
}

/** How long the coordinator waits; engineering defaults (03 §5), not the owner's. */
data class GpsTiming(
    val operationMillis: Long = 30_000,
    val tickMillis: Long = 1_000,
    val shutdownMillis: Long = 10_000,
)
