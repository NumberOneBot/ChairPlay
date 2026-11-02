// The portable CarPlay core is compiled from the Android library's own sources in the DiPlay submodule
// (upstream/), so DiPlay's changes come in by moving the submodule. A Sync copy applies the file filter to
// upstream alone: filtering the source set would also hide desktop replacements that share an upstream path.
// build.gradle.kts adds both copies to the source sets.
val upstreamCore by tasks.registering(Sync::class) {
    from("upstream/shared/src/main/java") {
        include(
            "com/shilapi/xcertplay/airplay/**",
            "com/shilapi/xcertplay/iap2/**",
            "com/shilapi/xcertplay/mfi/**",
            "com/shilapi/xcertplay/compat/Base64Compat.kt",
            // Upstream's route guidance and now playing parsers, for the CarPlay data bus.
            "com/shilapi/xcertplay/hud/BydHudRouteState.kt",
            "com/shilapi/xcertplay/hud/BydManeuverCodes.kt",
            "com/shilapi/xcertplay/media/CarPlayPlaybackStatus.kt",
            "com/shilapi/xcertplay/media/EchoReference.kt",
            "com/shilapi/xcertplay/media/MediaCodecSupport.kt",
            "com/shilapi/xcertplay/media/SpeexEchoCanceller.kt",
            "com/shilapi/xcertplay/media/TouchLatencyProbe.kt",
            "com/shilapi/xcertplay/network/AirPlayPortSelector.kt",
            "com/shilapi/xcertplay/network/HotspotAddressPolicy.kt",
            "com/shilapi/xcertplay/network/WirelessHotspotManager.kt",
            "com/shilapi/xcertplay/transport/BlockingDuplexByteStream.kt",
            "com/shilapi/xcertplay/transport/BluetoothRfcommDuplexStream.kt",
            "com/shilapi/xcertplay/transport/Iap2*.kt",
            "com/shilapi/xcertplay/transport/I2cTransport.kt",
            "com/shilapi/xcertplay/transport/VehicleSpeedNmea.kt",
            "org/concentus/**",
        )
        // USB multiplexing belongs to wired CarPlay.
        exclude("com/shilapi/xcertplay/transport/Iap2UsbMuxHost.kt")
        // Desktop identification includes GPS and per-link capability extensions.
        exclude("com/shilapi/xcertplay/transport/Iap2IdentificationClient.kt")
    }
    into(layout.buildDirectory.dir("upstream-core"))
    // A view area carries a safe area of its own, where DiPlay clips the display's to every area: the AltScreen's
    // views each have their own. Patched into the copy, each anchor checked once, so a newer DiPlay that moved one
    // stops the build here instead of dropping the patch.
    val patches = mapOf(
        // Desktop-only buffer telemetry. Remove these hooks when upstream exposes equivalent snapshots.
        "com/shilapi/xcertplay/airplay/BufferedAudioStream.kt" to listOf(
            "import android.util.Log" to "import android.util.Log\n" +
                "import io.github.numberonebot.chairplay.media.BufferedAudioStatus\n" +
                "import io.github.numberonebot.chairplay.media.BufferedAudioStatusSink",
            "        Thread({ receive() }, \"airplay-buffered-rx\")" to
                "        synchronized(lock) {\n" +
                "            if (closed.get()) return\n" +
                "            (sink as? BufferedAudioStatusSink)?.bufferedAudioOpened(this)\n" +
                "        }\n" +
                "        Thread({ receive() }, \"airplay-buffered-rx\")",
            "                    retireFedLocked()" to
                "                    retireFedLocked()\n                    publishDesktopStatusLocked()",
            "    private fun leadMillisLocked(): Long {" to """
                |    private fun publishDesktopStatusLocked() {
                |        val observer = sink as? BufferedAudioStatusSink ?: return
                |        val audible = feedStartTimestamp != null &&
                |            nanoTime() - feedStartNs >= START_LATENCY_MILLIS * 1_000_000L
                |        observer.bufferedAudioUpdated(this, BufferedAudioStatus.snapshot(
                |            rate, anchorNtp != null, audible, queue.size, fed.map { it.timestamp },
                |            positionLocked(), format.sampleRate, queuedBytes))
                |    }
                |
                |    private fun leadMillisLocked(): Long {
            """.trimMargin(),
            "                outputCleanupComplete = true" to
                "                outputCleanupComplete = true\n" +
                "                (sink as? BufferedAudioStatusSink)?.bufferedAudioClosed(this)",
        ),
        // Upstream now includes the lane limits. Only MHI2Q's destination and maneuver lengths remain extra.
        "com/shilapi/xcertplay/iap2/catalog/Iap2Endpoint.kt" to listOf(
            "                    field(4, \"MaxAfterManeuverRoadNameLength\", Iap2WireType.U16)," to
                "                    field(3, \"MaxDestinationNameLength\", Iap2WireType.U16),\n" +
                "                    field(4, \"MaxAfterManeuverRoadNameLength\", Iap2WireType.U16),\n" +
                "                    field(5, \"MaxManeuverDescriptionLength\", Iap2WireType.U16),",
        ),
        "com/shilapi/xcertplay/airplay/AirPlayConfig.kt" to listOf(
            "    val callAtCallRate: Boolean = false,\n)" to
                "    val callAtCallRate: Boolean = false,\n    val keyboardInput: Boolean = false,\n)",
            (
            "    val dockEdge: Int? = null,\n)" to
                "    val dockEdge: Int? = null,\n" +
                "    /** ChairPlay: this area's own safe area, as insets from the whole display; null clips the display's to it. */\n" +
                "    val safeArea: AirPlayInsets? = null,\n)"
            ),
        ),
        "com/shilapi/xcertplay/airplay/AirPlayInfoPlist.kt" to listOf(
            (
            "val displaySafe = display.safeArea ?: AirPlayInsets()" to "val displaySafe = area?.safeArea ?: display.safeArea ?: AirPlayInsets()"
            ),
            "        if (config.icons.isNotEmpty()) {" to
                "        io.github.numberonebot.chairplay.input.HandwritingHid.advertise(info,\n" +
                "            experimental = config.keyboardInput, keyboard = config.keyboardInput)\n" +
                "        if (config.icons.isNotEmpty()) {",
        ),
        "com/shilapi/xcertplay/airplay/AirPlaySession.kt" to listOf(
            "    fun sendCommand(command: Map<String, Any?>): Boolean" to
                "    val keyboardInputEnabled: Boolean get() = config.keyboardInput\n\n" +
                "    fun sendCommand(command: Map<String, Any?>): Boolean",
            "    fun onCommand(session: AirPlaySession, type: String, params: Map<String, Any?>) {}" to
                "    fun onCommand(session: AirPlaySession, type: String, params: Map<String, Any?>) {}\n" +
                "    fun onCommandResponse(session: AirPlaySession, type: String, params: Map<String, Any?>): Map<String, Any?>? = null",
            "        listener.onCommand(this, type, params)\n        return RtspMessage.Response(status = 200)" to
                "        listener.onCommand(this, type, params)\n" +
                "        val response = listener.onCommandResponse(this, type, params)\n" +
                "        return if (response == null) RtspMessage.Response(status = 200) else RtspMessage.Response(\n" +
                "            status = 200, headers = mapOf(\"Content-Type\" to PLIST_CONTENT_TYPE), body = BplistCodec.encode(response))",
            "        trace { \"airplay event tx headers=\$head bodyHex=\${body.toHex()}\" }" to
                "        if (io.github.numberonebot.chairplay.input.HandwritingHid.isCharacterCommand(command)) debugLog(\"airplay character report tx cseq=\$eventCseq (contents hidden)\")\n" +
                "        else trace { \"airplay event tx headers=\$head bodyHex=\${body.toHex()}\" }",
            "                    if (message.method.startsWith(\"RTSP/\") || message.method.startsWith(\"HTTP/\")) continue" to
                "                    if (message.method.startsWith(\"RTSP/\") || message.method.startsWith(\"HTTP/\")) {\n" +
                "                        if (io.github.numberonebot.chairplay.input.HandwritingHid.enabled) debugLog(\n" +
                "                            \"airplay event response cseq=\${message.headers[\"cseq\"]?.toLongOrNull()} status=\${message.path.toIntOrNull()} bytes=\${message.body.size}\")\n" +
                "                        continue\n                    }",
        ),
    )
    val target = layout.buildDirectory.dir("upstream-core")
    doLast {
        for ((path, changes) in patches) {
            val file = target.get().file(path).asFile
            // A checkout with core.autocrlf=true (git's default on Windows, GitHub's runners) has CRLF: match on LF.
            var text = file.readText().replace("\r\n", "\n")
            for (change in changes) {
                val found = text.split(change.first).size - 1
                check(found == 1) { "upstream $path: the line to patch is there $found times, not once; update gradle/upstream.gradle.kts" }
                text = text.replace(change.first, change.second)
            }
            file.writeText(text)
        }
    }
}

// Upstream unit tests for the same core, copied the same way. Tests are excluded when they use
// Robolectric or android.* (no Android runtime here) or when they exercise a class outside the
// core set; the excluded groups are listed below.
val upstreamCoreTests by tasks.registering(Sync::class) {
    from("upstream/shared/src/test/java") {
        // These new protocol tests use only sockets and our Log shim. Run their bodies unchanged on the JVM;
        // the Android runner and SDK annotation are unnecessary. Actual AudioTrack tests remain excluded below.
        val desktopProtocolTests = setOf(
            "AirPlayEventPeerTest.kt", "AirPlayTouchTraceCostTest.kt", "AudioDuckCommandTest.kt",
            "ControlReceiveBoundsTest.kt", "ScreenStreamPeerTest.kt",
            "BufferedAudioSessionTest.kt", "CarPlayAudioStreamIsolationTest.kt",
        )
        eachFile {
            if (name in desktopProtocolTests) filter { line: String ->
                if (line.startsWith("import org.robolectric.") || line == "import org.junit.runner.RunWith" ||
                    line.startsWith("@RunWith(") || line.startsWith("@Config(")) null else line
            }
        }
        include(
            "com/shilapi/xcertplay/airplay/**",
            "com/shilapi/xcertplay/iap2/**",
            "com/shilapi/xcertplay/mfi/**",
            "com/shilapi/xcertplay/compat/Base64CompatTest.kt",
            "com/shilapi/xcertplay/hud/BydHudRouteStateTest.kt",
            "com/shilapi/xcertplay/media/CarPlayPlaybackStatusTest.kt",
            "com/shilapi/xcertplay/media/EchoReferenceTest.kt",
            "com/shilapi/xcertplay/network/AirPlayPortSelectorTest.kt",
            "com/shilapi/xcertplay/network/HotspotAddressPolicyTest.kt",
            "com/shilapi/xcertplay/network/WirelessHotspotManagerTest.kt",
            "com/shilapi/xcertplay/transport/BlockingDuplexByteStream*.kt",
            "com/shilapi/xcertplay/transport/BluetoothRfcommDuplexStreamTest.kt",
            "com/shilapi/xcertplay/transport/Iap2*.kt",
            "com/shilapi/xcertplay/transport/I2cTransport*.kt",
            "com/shilapi/xcertplay/transport/VehicleSpeedNmeaTest.kt",
        )
        // Robolectric runner: needs an Android runtime.
        exclude(
            "com/shilapi/xcertplay/airplay/AirPlayCryptoAndroidCompatibilityTest.kt",
            "com/shilapi/xcertplay/airplay/ClusterContentTest.kt",
            "com/shilapi/xcertplay/airplay/CurrentScreenListenerTest.kt",
            "com/shilapi/xcertplay/airplay/IphoneCornerMaskSessionTest.kt",
            "com/shilapi/xcertplay/airplay/BufferedMusicClockTrimTest.kt",
            "com/shilapi/xcertplay/airplay/RealtimeMusicClockTrimTest.kt",
        )
        // Uses KeyEvent constants beyond the shim's, which carries only what the main code references.
        exclude("com/shilapi/xcertplay/airplay/CarPlayMediaButtonTest.kt")
        // Written against Android's Conscrypt: it expects the platform ChaCha20-Poly1305 to seal the same
        // key and nonce twice, which a desktop JDK refuses (the core then falls back to BouncyCastle, so
        // 5 of its 9 cases fail on the JVM only).
        exclude("com/shilapi/xcertplay/airplay/AirPlayCryptoChachaTest.kt")
        // SoftwareOpusEncoder is not in the core set (replaced in a later task).
        exclude("com/shilapi/xcertplay/airplay/AirPlayInfoPlistTest.kt")
        // Pins DiPlay's RouteGuidance component, which ChairPlay's identification replaces with LuKa's; its checks
        // are in ChairPlay's Iap2LaneGuidanceIdentificationTest with ChairPlay's values.
        exclude("com/shilapi/xcertplay/transport/Iap2RouteGuidanceSubscriptionTest.kt")
    }
    into(layout.buildDirectory.dir("upstream-core-tests"))
}
