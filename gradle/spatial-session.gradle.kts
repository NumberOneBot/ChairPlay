// Desktop spatial negotiation/control. Remove when upstream provides the same APAP session contract.
tasks.named<Sync>("upstreamCore") {
    inputs.file(layout.projectDirectory.file("gradle/spatial-session.gradle.kts"))
    doLast {
        val root = layout.buildDirectory.dir("upstream-core/com/shilapi/xcertplay/airplay").get().asFile
        fun edit(name: String, block: (patch: (String, String) -> Unit) -> Unit) {
            val target = root.resolve(name)
            var source = target.readText().replace("\r\n", "\n")
            block { from, to ->
                check(source.split(from).size - 1 == 1) { "Spatial upstream $name anchor changed: $from" }
                source = source.replace(from, to)
            }
            target.writeText(source)
        }
        edit("AirPlayConfig.kt") { patch ->
            patch("    val mainBufferedAudio: Boolean = false,", "    val mainBufferedAudio: Boolean = false,\n    val spatialAudio: Boolean = false,")
        }
        edit("AirPlayInfoPlist.kt") { patch ->
            patch("        return info", "        if (config.spatialAudio && config.bufferedAudioOutputEnabled) io.github.numberonebot.chairplay.media.apac.spatialOffer(info)\n        return info")
        }
        edit("AirPlaySession.kt") { patch ->
            patch("    val keyboardInputEnabled: Boolean get() = config.keyboardInput", "    val keyboardInputEnabled: Boolean get() = config.keyboardInput\n" +
                "    private var spatialBufferedNegotiated = false\n    internal val spatialAudioEnabled get() = config.spatialAudio && spatialBufferedNegotiated")
            patch("        response[\"enabledFeatures\"] = features", "        spatialBufferedNegotiated = MAIN_BUFFERED_FEATURE in features\n        response[\"enabledFeatures\"] = features")
            patch("                val info = AirPlayInfoPlist.build(config)", "                val info = AirPlayInfoPlist.build(config).toMutableMap()\n" +
                "                if (config.spatialAudio && !spatialBufferedNegotiated) {\n" +
                "                    info.remove(\"mainBufferedInfo\")\n" +
                "                    info[\"audioFormats\"] = (info[\"audioFormats\"] as? List<*>)?.filterNot { (it as? Map<*, *>)?.get(\"type\") == 103 }\n                }")
            patch("            val response = media.onBufferedAudioControl(this, request.method, body)",
                "            val response = try { media.onBufferedAudioControl(this, request.method, body) }\n" +
                "                catch (_: java.io.IOException) { return RtspMessage.Response(status = 400) }")
            patch("if (request.method != \"GETANCHOR\") debugLog", "if (request.method !in setOf(\"GETANCHOR\", \"SETMAGICCOOKIE\")) debugLog")
            patch("setOf(\"SETRATE\", \"SETRATEANCHORTIME\", \"GETANCHOR\", \"FLUSHBUFFERED\")",
                "setOf(\"SETRATE\", \"GETANCHOR\", \"SETRATEANCHORTIME\", \"FLUSHBUFFERED\", \"SETMAGICCOOKIE\")")
        }
        edit("CarPlayMediaEngine.kt") { patch ->
            patch("import android.util.Log", "import android.util.Log\nimport io.github.numberonebot.chairplay.media.apac.*")
            patch("        val format = AudioStreamCodec.fromFormatBits((stream[\"audioFormat\"] as? Number)?.toLong() ?: 0L, type, \"media\")",
                "        val spatial = try { spatialStreamSource(session, sink, stream, key) }\n" +
                "            catch (error: Exception) { session.logDebug(\"Spatial SETUP declined: \${error.message}\"); return null }\n" +
                "        val format = AudioStreamCodec.fromFormatBits(if (spatial != null) 0x800000L else (stream[\"audioFormat\"] as? Number)?.toLong() ?: 0L, type, \"media\")")
            patch("        if (stream[\"ct\"]?.let { it !is Number || it.toInt() != 4 } == true ||\n            stream[\"spf\"]?.let { it !is Number || it.toInt() != 1024 } == true) {",
                "        if (spatial == null && (stream[\"ct\"]?.let { it !is Number || it.toInt() != 4 } == true ||\n            stream[\"spf\"]?.let { it !is Number || it.toInt() != 1024 } == true)) {")
            patch("        val peer = session.remoteAddress ?: return null\n        val buffered = try {",
                "        val peer = session.remoteAddress ?: run { spatial?.close(); return null }\n        val buffered = try {")
            patch("BufferedAudioStream(key, format, sink, session::logDebug, expectedPeer = peer)",
                "BufferedAudioStream(key, format, sink, session::logDebug, expectedPeer = peer, apap = spatial)")
            patch("            Log.w(TAG, \"buffered audio listener failed\", error)",
                "            spatial?.close()\n            Log.w(TAG, \"buffered audio listener failed\", error)")
            patch("\"audioBufferSize\" to BufferedAudioStream.AUDIO_BUFFER_BYTES", "\"audioBufferSize\" to buffered.audioBufferBytes")
            // SETRATE waits for the clock sync (gradle/timing-upstream.gradle.kts): pass session::syncedNtp again when
            // upstream syncs before answering SETRATE.
            patch("        val rtpTime = (body[\"rtpTime\"] as? Number)?.toLong()",
                "        if (buffered.apap != null) return spatialControl(buffered, method, body, session::syncedNtpForAnchor)\n" +
                "        val rtpTime = (body[\"rtpTime\"] as? Number)?.toLong()")
        }
    }
}
