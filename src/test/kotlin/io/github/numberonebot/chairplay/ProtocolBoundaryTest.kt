package io.github.numberonebot.chairplay

import java.nio.file.Files
import java.nio.file.Path
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** Ratchet: only the listed files may import DiPlay's types (`com.shilapi.`); the list only shrinks. */
class ProtocolBoundaryTest {
    private val sourceRoot: Path = findSourceRoot()

    @Test
    fun `only listed files import DiPlay`() {
        val importers = kotlinFiles().filter { importsDiPlay(it) }.map { relative(it) }.toSortedSet()
        val unlisted = importers - ALLOWED
        val stale = ALLOWED - importers
        assertTrue("Files import com.shilapi. but are not in ALLOWED: $unlisted", unlisted.isEmpty())
        assertTrue("Entries in ALLOWED no longer import com.shilapi.; remove them: $stale", stale.isEmpty())
        assertEquals(ALLOWED, importers)
    }

    @Test
    fun `phone, data and drive packages never import DiPlay`() {
        val offenders = kotlinFiles().map { relative(it) }.filter { path -> NEUTRAL.any { path.startsWith(it) } }
            .filter { importsDiPlay(sourceRoot.resolve(it)) }
        assertTrue("Files under ${NEUTRAL.joinToString()} import com.shilapi.: $offenders", offenders.isEmpty())
    }

    private fun kotlinFiles(): List<Path> = Files.walk(sourceRoot).use { stream ->
        stream.filter { Files.isRegularFile(it) && it.toString().endsWith(".kt") }.toList()
    }

    private fun importsDiPlay(file: Path): Boolean =
        Files.readAllLines(file).any { it.startsWith("import com.shilapi.") }

    private fun relative(file: Path): String = sourceRoot.relativize(file).toString().replace('\\', '/')

    private fun findSourceRoot(): Path {
        var dir: Path? = Path.of(System.getProperty("user.dir")).toAbsolutePath()
        while (dir != null) {
            val candidate = dir.resolve(SOURCE_PATH)
            if (Files.isDirectory(candidate)) return candidate
            dir = dir.parent
        }
        error("Cannot find $SOURCE_PATH from ${System.getProperty("user.dir")}")
    }

    companion object {
        private const val SOURCE_PATH = "src/main/kotlin/io/github/numberonebot/chairplay"

        /** The protocol-neutral folders, which never import DiPlay's types. */
        private val NEUTRAL = listOf("phone/", "data/", "drive/")

        /** Paths relative to the chairplay source folder. */
        val ALLOWED: Set<String> = sortedSetOf(
            "AltscreenView.kt",
            "AppControls.kt",
            "CarPlayTheme.kt",
            "Commands.kt",
            "Main.kt",
            "SessionListener.kt",
            "bluetooth/WindowsBluetooth.kt",
            "carplay/AltscreenAreaMove.kt",
            "carplay/CarPlayConfig.kt",
            "carplay/CarPlayControl.kt",
            "carplay/CarPlayDataFeed.kt",
            "carplay/CarPlayDataLog.kt",
            "carplay/CarPlayDataMessages.kt",
            "carplay/CarPlayDisplay.kt",
            "carplay/CarPlayLocation.kt",
            "carplay/CarPlayMediaSink.kt",
            "carplay/DataSubscriptions.kt",
            "carplay/Iap2DataDecoder.kt",
            "config/ConfigFiles.kt",
            "config/DesktopConfig.kt",
            "config/EditableSettings.kt",
            "config/PairingFiles.kt",
            "discovery/CarPlayTxt.kt",
            "discovery/DesktopBonjour.kt",
            "input/CarPlayTextInput.kt",
            "input/HandwritingHid.kt",
            "input/KeyboardHid.kt",
            "media/AudioRenderer.kt",
            "media/BufferedMusicOutput.kt",
            "media/MicrophoneSender.kt",
            "media/PcmDecoders.kt",
            "media/RealtimeAudioOutput.kt",
            "media/apac/ApapFrameReader.kt",
            "media/apac/ApapStreamSource.kt",
            "media/apac/SpatialAudioBridge.kt",
            "media/apac/SpatialControls.kt",
            "network/DesktopNetwork.kt",
            "network/SameLanNetwork.kt",
            "session/AirPlayListener.kt",
            "session/AltscreenUi.kt",
            "session/BringUpStack.kt",
            "session/TraceRedaction.kt",
            "session/WirelessBootstrap.kt",
            "session/WirelessEndpoint.kt",
            "session/WirelessParts.kt",
        )
    }
}
