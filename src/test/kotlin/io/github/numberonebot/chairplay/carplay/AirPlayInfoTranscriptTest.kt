package io.github.numberonebot.chairplay.carplay

import com.shilapi.xcertplay.airplay.AirPlayConfig
import com.shilapi.xcertplay.airplay.AirPlayInfoPlist
import com.shilapi.xcertplay.airplay.BplistCodec
import io.github.numberonebot.chairplay.config.DesktopConfig
import io.github.numberonebot.chairplay.config.writeTestSettings
import org.junit.Assert.assertEquals
import org.junit.Test
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.StandardOpenOption
import java.security.MessageDigest
import java.util.HexFormat

/**
 * Characterization: what the iPhone is told of the receiver and its screens for a set of settings files, the CarPlay
 * screen's size, insets, dock, corners and touch, the AltScreen's areas, margins and view: the AirPlayConfig, and the
 * /info plist as its bytes and its displays. The expected transcript (airplay/info-transcript.txt) was recorded from
 * DesktopConfig.airPlay and airPlayNow, built in DesktopConfig from DiPlay's types, before the screens were described
 * in ChairPlay's own (ScreenSpec, ClusterSpec) and CarPlayConfig built it from them: the move changed nothing the
 * iPhone is told. A run that differs writes what it got to build/airplay-info-transcript.txt.
 */
class AirPlayInfoTranscriptTest {
    private val hex = HexFormat.of()
    private val lines = ArrayList<String>()

    private val scenarios: List<Pair<String, List<String>>> = listOf(
        "defaults" to emptyList(),
        "800x480, safe insets, dock driver" to listOf("display.width=800", "display.height=480", "display.safe.top=10",
            "display.safe.bottom=20", "display.safe.left=40", "display.safe.right=0", "display.dock=driver"),
        "rounded corners" to listOf("display.corners=rounded"),
        "rounded corners, a safe area drawn inside" to listOf("display.corners=rounded", "display.safe.left=60",
            "display.safe.draw.outside=false"),
        "square corners with a safe area" to listOf("display.corners=square", "display.safe.top=30", "display.safe.right=12"),
        "dock bottom, swipe, right-hand drive" to listOf("display.dock=bottom", "display.touch=swipe", "display.right.hand.drive=true"),
        "tap" to listOf("display.touch=tap"),
        "touch off, dock auto" to listOf("display.touch=none", "display.dock=auto"),
        "size from ppi, 30 fps" to listOf("display.width=1920", "display.height=1080", "display.ppi=160", "display.fps=30"),
        "physical size" to listOf("display.physical.width.mm=250", "display.physical.height.mm=120", "display.ppi=160"),
        "physical width" to listOf("display.physical.width.mm=300"),
        "window apart" to listOf("window.width=1600", "window.height=900"),
        "altscreen on" to listOf("altscreen.enabled=true"),
        "altscreen with two areas and margins" to listOf("altscreen.enabled=true", "altscreen.width=1920", "altscreen.height=720",
            "altscreen.content=card", "altscreen.cards=left", "altscreen.map.area=left5", "altscreen.card.area=custom",
            "altscreen.card.area.custom=412, 36, 616, 468", "altscreen.card.safe.top=10", "altscreen.card.safe.left=20",
            "altscreen.safe.top=20", "altscreen.safe.right=400"),
        "altscreen hud, map margins" to listOf("altscreen.enabled=true", "altscreen.content=hud", "altscreen.cards=auto",
            "altscreen.map.area=custom", "altscreen.map.safe.right=300", "altscreen.map.safe.bottom=40"),
        "altscreen map, cards right, small" to listOf("altscreen.enabled=true", "altscreen.width=1024", "altscreen.height=384",
            "altscreen.content=map", "altscreen.cards=right", "altscreen.card.area=right4"),
        "altscreen map-card, custom rect clamped" to listOf("altscreen.enabled=true", "altscreen.content=map-card",
            "altscreen.map.area=custom", "altscreen.map.area.custom=1000, 100, 900, 500"),
        "altscreen off with its areas set" to listOf("altscreen.enabled=false", "altscreen.map.area=left3"),
        "codecs, audio, keyboard, names" to listOf("video.codecs=h264", "audio.music.mode=standard", "keyboard.text.input=false",
            "car.label=Garage", "device.name=Гараж CarPlay", "display.theme=dark"),
    )

    @Test
    fun `the iPhone is told what it was told before the screens were described in our own terms`() {
        for ((name, settings) in scenarios) {
            lines += "## $name"
            val dir = appFolder(settings)
            val config = DesktopConfig(dir)
            record("airPlay", CarPlayConfig.airPlay(config))
            // The next connection's, with the spatial offer left out: it depends on this PC's decoder and output.
            record("airPlayNow", CarPlayConfig.airPlayNow(config).copy(spatialAudio = false))
        }
        lines += "## the next connection, the file edited after the start"
        lastAirPlay = emptyList()
        val dir = appFolder(listOf("device.name=First"))
        val config = DesktopConfig(dir)
        Files.writeString(dir.resolve("settings.properties"), "device.name=Second\ndisplay.width=1024\ndisplay.height=600\n" +
            "altscreen.enabled=true\naltscreen.content=card\n", StandardOpenOption.APPEND)
        record("airPlayNow", CarPlayConfig.airPlayNow(config).copy(spatialAudio = false))

        val expected = javaClass.getResourceAsStream("/airplay/info-transcript.txt")
            ?.use { String(it.readBytes(), Charsets.UTF_8) }?.replace("\r\n", "\n")?.trimEnd()
        val actual = lines.joinToString("\n")
        if (expected != actual) Files.writeString(Path.of("build", "airplay-info-transcript.txt"), actual + "\n")
        assertEquals(expected, actual)
    }

    /** A fresh app folder with a fixed identity, the Wi-Fi and [settings]. */
    private fun appFolder(settings: List<String>): Path {
        val dir = Files.createTempDirectory("chairplay-info")
        writeTestSettings(dir)
        Files.writeString(dir.resolve("settings.properties"), settings.joinToString("") { "$it\n" }, StandardOpenOption.APPEND)
        Files.writeString(dir.resolve("identity.properties"), "privateKey=${"11".repeat(32)}\npublicKey=${"22".repeat(32)}\n" +
            "pairingId=00000000-0000-4000-8000-000000000001\n")
        return dir
    }

    private fun record(label: String, config: AirPlayConfig) {
        val recorded = ArrayList<String>()
        recorded += "${config.copy(icons = emptyList())}"
        config.icons.forEach { recorded += "  icon ${it.widthPixels}x${it.heightPixels} ${sha(it.data)}" }
        val info = AirPlayInfoPlist.build(config)
        recorded += "  /info ${sha(BplistCodec.encode(info))}"
        (info["displays"] as List<*>).forEach { display ->
            recorded += "  display"
            (display as Map<*, *>).forEach { (key, value) ->
                if (value is List<*>) {
                    recorded += "    $key:"
                    value.forEach { recorded += "      ${compact(it)}" }
                } else {
                    recorded += "    $key: ${compact(value)}"
                }
            }
        }
        // The next connection is told the same as the start, mostly: then one line says so.
        if (label == "airPlayNow" && recorded == lastAirPlay) lines += "$label as airPlay" else {
            lines += "$label ${recorded.first()}"
            lines += recorded.drop(1)
        }
        if (label == "airPlay") lastAirPlay = recorded
    }

    private var lastAirPlay: List<String> = emptyList()

    private fun compact(value: Any?): String = when (value) {
        is Map<*, *> -> value.entries.joinToString(", ", "{", "}") { "${it.key}=${compact(it.value)}" }
        is List<*> -> value.joinToString(", ", "[", "]") { compact(it) }
        else -> leaf(value)
    }

    private fun leaf(value: Any?): String = when (value) {
        null -> "null"
        is ByteArray -> "bytes ${value.size} ${sha(value)}"
        is String -> "\"$value\""
        else -> "${value::class.simpleName} $value"
    }

    private fun sha(bytes: ByteArray): String = hex.formatHex(MessageDigest.getInstance("SHA-256").digest(bytes)).take(16)
}
