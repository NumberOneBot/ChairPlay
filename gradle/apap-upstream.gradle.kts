// APAP is absent in pinned DiPlay. Remove these generated-copy seams once upstream supplies
// APAP framing, cookie controls and typed spatial delivery; keep desktop integration regressions.
tasks.named<Sync>("upstreamCore") {
    inputs.file(layout.projectDirectory.file("gradle/apap-upstream.gradle.kts"))
    doLast {
        val root = layout.buildDirectory.dir("upstream-core/com/shilapi/xcertplay/airplay").get().asFile
        val file = root.resolve("BufferedAudioStream.kt")
        var source = file.readText().replace("\r\n", "\n")
        fun patch(from: String, to: String, count: Int = 1) {
            check(source.split(from).size - 1 == count) { "APAP upstream anchor changed: $from" }
            source = source.replace(from, to)
        }
        patch("import android.util.Log", "import android.util.Log\nimport io.github.numberonebot.chairplay.media.apac.*")
        patch("    private val expectedPeer: InetAddress = InetAddress.getLoopbackAddress(),",
            "    private val expectedPeer: InetAddress = InetAddress.getLoopbackAddress(),\n    internal val apap: ApapStreamSource? = null,")
        patch("internal class Frame(val sequence: Int, val timestamp: Long, val rtp: ByteArray)",
            "internal class Frame(val sequence: Int, val timestamp: Long, val rtp: ByteArray, val apap: ApapAccessUnit? = null, val size: Int = rtp.size)")
        patch("    val port: Int get() = boundPort", "    val port: Int get() = boundPort\n" +
            "    val audioBufferBytes: Int get() = if (apap == null) AUDIO_BUFFER_BYTES else 21_168_000")
        patch("                val length = input.readUnsignedShort()", "                val frame = if (apap != null) apap.read(input) else {\n                val length = input.readUnsignedShort()")
        patch("                receivedBytes += length", "                frame\n                }\n                receivedBytes += frame.size + if (apap == null) LENGTH_BYTES + TAG_BYTES + NONCE_BYTES else 0")
        patch("                if (frame.rtp.size <= RTP_HEADER) continue", "                if (frame.apap == null && frame.rtp.size <= RTP_HEADER) continue")
        patch("queuedBytes + frame.rtp.size > AUDIO_BUFFER_BYTES", "queuedBytes + frame.size > audioBufferBytes")
        patch("queuedBytes += frame.rtp.size", "queuedBytes += frame.size")
        patch("queuedBytes -= queue.pollFirst()!!.rtp.size", "queuedBytes -= queue.pollFirst()!!.size")
        patch("queuedBytes -= fed.removeFirst().rtp.size", "queuedBytes -= fed.removeFirst().size")
        patch("                    queue.addLast(frame)", """
            |                    if (apap != null && (apap.flushRange?.contains(frame.sequence, frame.timestamp) == true ||
            |                        queue.any { it.timestamp == frame.timestamp && it.sequence == frame.sequence } ||
            |                        fed.any { it.timestamp == frame.timestamp && it.sequence == frame.sequence })) return@synchronized
            |                    queue.addLast(frame)
        """.trimMargin())
        patch("            if (!closed.get()) log(\"Buffered audio: connection ended (\${error.javaClass.simpleName})\")",
            "            if (!closed.get()) log(\"Buffered audio: connection ended (\${error.javaClass.simpleName})\")\n            if (apap != null) close()")
        patch("                                sink.onAudioStarted(id, format, frame.timestamp.toInt())",
            "                                if (apap != null) apap.begin() else sink.onAudioStarted(id, format, frame.timestamp.toInt())")
        patch("                            sink.onAudioRtp(id, format, frame.rtp, frame.timestamp.toInt())",
            "                            if (apap != null) apap.push(requireNotNull(frame.apap)) else sink.onAudioRtp(id, format, frame.rtp, frame.timestamp.toInt())")
        patch("    private fun leadMillisLocked(): Long {", "    private fun leadMillisLocked(): Long {\n        if (apap != null) return apap.leadMillis")
        patch("    private fun positionLocked(): Long? {", "    private fun positionLocked(): Long? {\n        if (apap != null) return apap.position ?: anchorRtp")
        patch("positionLocked(), format.sampleRate, queuedBytes))",
            "positionLocked(), format.sampleRate, queuedBytes, apap?.multichannelOutput))")
        patch("    private fun anchorLocked(): Map<String, Any?>? {", """
            |    fun flushSpatial(range: ApapFlushRange) = synchronized(lock) {
            |        if (closed.get()) return@synchronized
            |        restoreFedLocked()
            |        apap?.flushRange = range
            |        val removed = queue.removeAll { range.contains(it.sequence, it.timestamp) }
            |        queuedBytes = queue.sumOf { it.size.toLong() }
            |        if (range.prefix || removed) {
            |            if (range.prefix) range.untilTime?.let { apap?.retainFrom(it) }
            |            restartOutputLocked()
            |        }
            |        lock.notifyAll()
            |    }
            |
            |    private fun anchorLocked(): Map<String, Any?>? {
        """.trimMargin())
        patch("        flushUntil = timestamp", "        flushUntil = timestamp\n        apap?.retainFrom(timestamp)")
        patch("while (queue.peekFirst()?.let { before(it.timestamp, timestamp) } == true)",
            "while (queue.peekFirst()?.let { before(it.timestamp, timestamp) && (apap == null || !before(timestamp, (it.timestamp + SAMPLES_PER_FRAME) and U32)) } == true)")
        patch("if (feedStartTimestamp != null) runCatching { sink.onAudioStopped(id) }",
            "if (feedStartTimestamp != null) runCatching { if (apap != null) apap.pause() else sink.onAudioStopped(id) }")
        patch("                outputCleanupComplete = true", "                apap?.close()\n                outputCleanupComplete = apap?.cleanupComplete ?: true")
        file.writeText(source)
    }
}
