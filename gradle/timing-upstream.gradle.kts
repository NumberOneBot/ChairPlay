// The iPhone's play command (SETRATE) is answered on the clock synchronized with it. Remove when upstream syncs
// before answering SETRATE: until its timing clock syncs (about 2 s), DiPlay anchors the first play on the PC's wall
// clock, and buffered music started while ChairPlay connects stays silent.
tasks.named<Sync>("upstreamCore") {
    inputs.file(layout.projectDirectory.file("gradle/timing-upstream.gradle.kts"))
    doLast {
        val root = layout.buildDirectory.dir("upstream-core/com/shilapi/xcertplay/airplay").get().asFile
        fun edit(name: String, block: (patch: (String, String, Int) -> Unit) -> Unit) {
            val target = root.resolve(name)
            var source = target.readText().replace("\r\n", "\n")
            block { from, to, count ->
                check(source.split(from).size - 1 == count) { "Timing upstream $name anchor changed: $from" }
                source = source.replace(from, to)
            }
            target.writeText(source)
        }
        edit("NtpClock.kt") { patch ->
            patch("    private var synced = false", "    private var synced = false\n" +
                "    private val syncedLatch = java.util.concurrent.CountDownLatch(1)", 1)
            patch("            synced = true", "            synced = true\n            syncedLatch.countDown()", 1)
            // Until the first sync, ask every 100 ms; an iPhone that answers none of 30 is asked every second again.
            patch("Thread.sleep(REQUEST_INTERVAL_MS)", "Thread.sleep(nextRequestDelayMs())", 2)
            patch("    private fun sendRequest() {", """
                |    private var fastRequests = 0
                |
                |    private fun nextRequestDelayMs(): Long =
                |        if (syncedLatch.count > 0L && fastRequests++ < 30) 100L else REQUEST_INTERVAL_MS
                |
                |    private fun sendRequest() {
            """.trimMargin(), 1)
            patch("    override fun close() {", """
                |    /** Whether [start] gave the clock an iPhone to sync with. */
                |    val started: Boolean get() = peer != null
                |
                |    /** Waits up to [timeoutMs] for the first sync with the iPhone; true once synced. */
                |    fun awaitSynced(timeoutMs: Long): Boolean = try {
                |        syncedLatch.await(timeoutMs, java.util.concurrent.TimeUnit.MILLISECONDS)
                |    } catch (_: InterruptedException) {
                |        Thread.currentThread().interrupt()
                |        syncedLatch.count == 0L
                |    }
                |
                |    override fun close() {
            """.trimMargin(), 1)
        }
        edit("AirPlaySession.kt") { patch ->
            patch("    fun syncedNtp(): BigInteger = ntp.syncedNtp()", """
                |    fun syncedNtp(): BigInteger = ntp.syncedNtp()
                |
                |    private val anchorSyncTimedOut = AtomicBoolean(false)
                |
                |    /** Now on the synchronized clock, for a play anchor: waits for the first sync, once up to 3 s a session. */
                |    fun syncedNtpForAnchor(): BigInteger {
                |        if (ntp.started && !anchorSyncTimedOut.get() && !ntp.awaitSynced(3_000L) && !anchorSyncTimedOut.getAndSet(true))
                |            debugLog("Timing: no clock sync with the iPhone in 3 s; answering with the local clock")
                |        return ntp.syncedNtp()
                |    }
            """.trimMargin(), 1)
        }
        // The spatial path's SETRATE waits the same way (gradle/spatial-session.gradle.kts).
        edit("CarPlayMediaEngine.kt") { patch ->
            patch("buffered.setRate(rtpTime, rate, session.syncedNtp())", "buffered.setRate(rtpTime, rate, session.syncedNtpForAnchor())", 1)
        }
    }
}
