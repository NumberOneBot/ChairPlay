package io.github.numberonebot.chairplay.config

import com.shilapi.xcertplay.airplay.AirPlayIdentity
import com.shilapi.xcertplay.airplay.PairingStore
import io.github.numberonebot.chairplay.log.LogLevel
import io.github.numberonebot.chairplay.media.DuckSetting
import io.github.numberonebot.chairplay.media.MediaDucking
import io.github.numberonebot.chairplay.media.decibelGain
import io.github.numberonebot.chairplay.network.SavedWifi
import io.github.numberonebot.chairplay.phone.AltscreenCards
import io.github.numberonebot.chairplay.phone.AltscreenContent
import io.github.numberonebot.chairplay.phone.ClusterSpec
import io.github.numberonebot.chairplay.phone.ScreenSpec
import com.shilapi.xcertplay.transport.Iap2WirelessSecurity
import java.io.File
import java.nio.file.Files
import java.nio.file.Path
import java.util.Properties
import kotlin.io.path.appendText
import kotlin.io.path.exists
import kotlin.io.path.name
import kotlin.io.path.readText
import kotlin.io.path.writeText

data class WindowSize(val width: Int, val height: Int)

enum class Theme { SYSTEM, LIGHT, DARK, PHONE }

/** The main window's title, with the size the iPhone draws the CarPlay screen at and, without a session, [status]. */
fun mainWindowTitle(stream: WindowSize, status: String? = null): String =
    "ChairPlay · ${stream.width}×${stream.height}" + (status?.let { " · $it" } ?: "")

/**
 * The receiver's settings and keys in [dir] (the exe's folder, see [appDirectory]): `settings.properties`
 * written by the user, `identity.properties` generated on first use, and one file per iPhone
 * pairing in `pairings/`, the desktop counterpart of upstream's AirPlayPersistence.
 */
class DesktopConfig private constructor(private val dir: Path, startedWith: DesktopConfig?) {
    constructor(dir: Path) : this(dir, null)

    // As the app starts the file gains what it lacks; read again later ([now]), it is taken as it is.
    private val settings = if (startedWith == null) loadSettings() else readSettings()

    /** Every setting as this reading of the file has it, by key: what a connection made with it runs with. */
    val values: Map<String, String> = settings.stringPropertyNames().associateWith(settings::getProperty)

    val identity: AirPlayIdentity = startedWith?.identity ?: loadIdentity(dir)
    val pairings: PairingStore = startedWith?.pairings ?: loadPairings(dir)
    val iphoneAddress: String? = settings.getProperty("iphone.address")?.trim()?.ifEmpty { null }

    /**
     * iphone.join.seconds: how long an iPhone that paired before has to join the Wi-Fi after the Bluetooth start
     * before the app starts over. It joins within a second or two; one that lost the Wi-Fi meanwhile gets the network
     * again this soon instead of after the five minutes a new iPhone has to accept CarPlay.
     */
    val iphoneJoinSeconds: Int = settings.positiveSetting("iphone.join.seconds") ?: 30

    /** True once an iPhone has paired with this app: its AirPlay pairing record is in pairings/. */
    fun iphoneKnown(): Boolean = Files.list(dir.resolve("pairings")).use { it.findAny().isPresent }

    /**
     * The Wi-Fi network as this reading has it (the app's start, or a bring-up's [now]); null until wifi.ssid is set,
     * which the start screen asks for.
     */
    val wifi: SavedWifi? = savedWifi(settings)

    /** network.ethernet.adapter: the adapter's GUID, blank for the only active one. */
    val ethernetAdapter: String = settings.getProperty("network.ethernet.adapter", "").trim()

    /** ui.autoconnect: the app connects to the iPhone as it starts; false waits for Connect on the start screen. */
    val autoconnect: Boolean = settings.booleanSetting("ui.autoconnect", true)

    /** ui.autoshow: CarPlay covers the start screen as soon as the iPhone is there; false waits for Show CarPlay. */
    val autoshow: Boolean = settings.booleanSetting("ui.autoshow", true)

    /**
     * The car's name on the iPhone: in its CarPlay settings, in /info, in the iAP2 identification and as the
     * Bonjour service name, which holds at most 63 bytes of UTF-8. The window title does not follow it.
     */
    val deviceName: String = run {
        val name = settings.getProperty("device.name")?.trim()?.ifEmpty { null } ?: DEFAULT_DEVICE_NAME
        require(name.toByteArray(Charsets.UTF_8).size <= 63 && name.none(Char::isISOControl)) {
            "device.name must be at most 63 bytes of UTF-8 without control characters, not \"$name\""
        }
        name
    }

    /** The CarPlay screen (display.*) and its window (window.*). */
    internal val display = DisplaySettings(settings)
    val displayWidth = display.width
    val displayHeight = display.height

    /** The CarPlay screen as the phone is told of it, which it asks at each connection. */
    val screen: ScreenSpec = display.screen

    /** The CarPlay window on the PC; the picture is scaled into it, so it needs not match the display. */
    val window = display.window
    val windowOnTop = WindowOnTop(settings)

    /** What the iPhone is told about its screen, for the start-up log. */
    fun displaySummary(): String = display.summary()

    /** audio.output and audio.input as written: blank or "default" for the Windows default, else a device name. */
    val audioOutput: String? = settings.getProperty("audio.output")
    val audioInput: String? = settings.getProperty("audio.input")

    /** audio.echo.cancellation: DiPlay's SpeexDSP echo canceller on calls, for speakers without headphones. */
    val echoCancellation: Boolean = settings.booleanSetting("audio.echo.cancellation", false)

    /**
     * audio.start.silence.ms and audio.start.fade.ms: CarPlay's music starts silent for so long, then rises to full over so
     * long, since the iPhone's first second or two of audio come in bursts and the line runs dry in them. The sound fades
     * out over the same time as CarPlay disconnects or reconnects and as the app closes.
     */
    val musicStartSilenceMillis: Long = settings.nonNegativeOr("audio.start.silence.ms", 2000).toLong()
    val musicStartFadeMillis: Long = settings.nonNegativeOr("audio.start.fade.ms", 800).toLong()

    /**
     * audio.duck.db and audio.duck.iphone: the music under a navigation prompt plays at the iPhone's level when one
     * comes, as a car's head unit takes Apple's, unless turned off; else at this level in decibels, from -30 to 0
     * (Apple's own -14 by default).
     */
    val duckSetting: DuckSetting = DuckSetting(
        decibelGain(duckDecibels().toDouble()),
        settings.booleanSetting("audio.duck.iphone", true),
    )

    private fun duckDecibels(): Int {
        val raw = settings.getProperty("audio.duck.db")?.trim()?.ifEmpty { null } ?: return MediaDucking.DEFAULT_DECIBELS
        val value = raw.toIntOrNull()
        require(value != null && value in DUCK_DECIBELS) { "audio.duck.db must be a whole number from -30 to 0, not $raw" }
        return value
    }

    /**
     * altscreen.*: DiPlay's cluster display, with even margins as for its virtual cluster rather than its car
     * cluster's crop; null when off. The iPhone learns of it only when it connects.
     */
    /** altscreen.width and altscreen.height, on or off: the AltScreen's window waits at this size while it is off. */
    val altscreenSize = WindowSize(settings.evenSetting("altscreen.width", DEFAULT_ALTSCREEN_WIDTH), settings.evenSetting("altscreen.height", DEFAULT_ALTSCREEN_HEIGHT))

    val altscreen: Altscreen? = if (!settings.booleanSetting("altscreen.enabled", false)) null else {
        val content = settings.choiceSetting("altscreen.content", AltscreenContent.MAP_CARD, AltscreenContent.entries.associateBy { it.key })
        val size = altscreenSize
        val cards = settings.choiceSetting("altscreen.cards", AltscreenCards.TOP, AltscreenCards.entries.associateBy { it.key })
        Altscreen(size, content, cards, settings.booleanSetting("altscreen.hud.stream", false))
    }

    /** altscreen.<group>.area, .area.custom and .safe: the views' own parts of an enabled AltScreen's frame. */
    private val viewParts = altscreen?.let { alt -> AltscreenViewParts(alt.size.width, alt.size.height) { settings.getProperty(it) } }

    /** The view areas an enabled AltScreen declares: every part of its frame, again for each view with its margins. */
    private val altscreenAreas = viewParts?.areas

    /** How many view areas the AltScreen declared; 0 without an AltScreen. */
    val altscreenAreaCount: Int = altscreenAreas?.size ?: 0

    /** The view area [content] shows in on the AltScreen, which it moves to as the view changes; 0 for the first. */
    fun altscreenAreaFor(content: AltscreenContent): Int = viewParts?.indexOf(content) ?: 0

    /** altscreen.<group>.area: what [content] is set to show in, the whole frame without an AltScreen or for the HUD. */
    fun altscreenChoice(content: AltscreenContent): AreaChoice = viewParts?.choiceOf(content) ?: AreaChoice.Preset(AreaPreset.FULL)

    /**
     * The view area [content] shows in when set to [choice], among the ones this connection declared: a part set since,
     * while CarPlay runs, is one of them.
     */
    fun altscreenAreaIndex(content: AltscreenContent, choice: AreaChoice): Int = viewParts?.indexOf(content, choice) ?: 0

    /** What reading the AltScreen's parts had to put right, such as a rectangle clamped into a smaller frame. */
    val altscreenWarnings: List<String> = viewParts?.warnings.orEmpty()

    /** The AltScreen as the phone is told of it; null when off. The iPhone learns of it only when it connects. */
    val cluster: ClusterSpec? = altscreen?.let { altscreenCluster(it, settings, altscreenAreas.orEmpty()) }

    /** gps.*: the spoofed GPS and the car that drives it; the iPhone learns of the GPS when it connects. */
    val gps = settings.gpsSettings()

    /** window.keep.aspect: the windows resize only with the picture's shape; a maximized one fills the screen. */
    val keepAspect: Boolean = settings.booleanSetting("window.keep.aspect", true)

    /** window.controls: a window of on-screen car controls beside CarPlay's. */
    val controlsWindow: Boolean = settings.booleanSetting("window.controls", false)

    /** log.carplay.data: the iPhone's route, song, call and battery messages to logs\carplay-data-<time>.jsonl. */
    val logCarPlayData: Boolean = settings.booleanSetting("log.carplay.data", false)

    /** log.level: how much the app's log, in the console and logs\chairplay-<time>.log, tells. */
    val logLevel: LogLevel = settings.choiceSetting("log.level", LogLevel.INFO, LogLevel.entries.associateBy { it.key })

    /** system follows the system's dark mode, as windows, its old name, does; phone sends nothing and leaves it to the iPhone. */
    val theme: Theme = settings.choiceSetting("display.theme", Theme.SYSTEM, Theme.entries.associateBy { it.name.lowercase() })

    /**
     * ui.accent: the accent colour of the screens, the controls window, and the HUD, written #rrggbb; every lighter or
     * fainter shade of it is made from it.
     */
    val accentText: String = (settings.getProperty("ui.accent")?.trim()?.ifEmpty { null } ?: DEFAULT_ACCENT).lowercase().also {
        require(Regex("#[0-9a-f]{6}").matches(it)) { "ui.accent must be a colour written #rrggbb, such as $DEFAULT_ACCENT, not $it" }
    }

    /** [accentText] as 0xRRGGBB. */
    val accent: Int = accentText.drop(1).toInt(16)

    /** ui.port: where the settings' page is served. */
    val uiServer: UiServerSettings = settings.uiServerSettings()

    /** keyboard.text.input: the PC's keyboard types into the iPhone's text fields. */
    val keyboardTextInput: Boolean = settings.booleanSetting("keyboard.text.input", true)

    /** video.codecs: whether the iPhone may send H.265 as well as H.264. */
    val h265: Boolean = settings.choiceSetting("video.codecs", false, mapOf("h264" to false, "h264+h265" to true))

    /** audio.music.mode: buffered, apps that can send music ahead, so short Wi-Fi gaps do not interrupt it; or standard. */
    val bufferedMusic: Boolean = settings.choiceSetting("audio.music.mode", true, mapOf("standard" to false, "buffered" to true))

    /** Whether a connection offers spatial music; only a connection's settings ([now]) ask, as the check is slow. */
    val spatialAudio: Boolean = startedWith != null && io.github.numberonebot.chairplay.media.apac.spatialConfigured(dir, settings)

    /** car.label and car.icon: the button on the CarPlay home screen, where a car shows its maker's, for the settings screen. */
    val carLabel: String = settings.getProperty("car.label")?.trim()?.ifEmpty { null } ?: DEFAULT_CAR_LABEL
    internal val homeIcon = carIcon(dir, settings.getProperty("car.icon"))

    val hardwareVideo: Boolean = settings.choiceSetting("video.decoder", true, mapOf("hardware" to true, "software" to false))

    /** The settings as settings.properties has them now, with this start's identity and pairings. */
    fun now(): DesktopConfig = DesktopConfig(dir, this)

    fun mfiDirectory(): File = dir.resolve("offline-mfi").toFile()

    private fun readSettings() = loadProperties(dir.resolve(SETTINGS_FILE), SETTINGS_REMEDY)

    private fun loadSettings(): Properties {
        val file = dir.resolve(SETTINGS_FILE)
        if (!file.exists()) {
            Files.createDirectories(dir)
            file.writeText(defaultSettingsText())
        } else {
            // A file from an older version gains what it lacks, after its own lines, which stay as they were.
            val saved = loadProperties(file, SETTINGS_REMEDY)
            val missing = SETTINGS_TEMPLATE.mapNotNull { block ->
                block.entries.filter { (key, _) -> !saved.containsKey(key) }.takeIf { it.isNotEmpty() }?.let(block::text)
            }
            if (missing.isNotEmpty()) {
                val text = file.readText()
                file.appendText((if (text.isEmpty() || text.endsWith("\n")) "" else "\n") + missing.joinToString(""))
            }
        }
        return loadProperties(file, SETTINGS_REMEDY)
    }

    companion object {
        /** settings.properties with every setting at its default, as the first start or deploy writes it. */
        fun defaultSettingsText(): String =
            "# ChairPlay settings, read at start. Delete this file to get the defaults back (the Wi-Fi too).\n" +
                "# A setting this file lacks is added at its end, at its default, when the app starts.\n\n" +
                SETTINGS_TEMPLATE.joinToString("") { it.text(it.entries) }

        /** Every key settings.properties knows. */
        val SETTING_KEYS: List<String> = SETTINGS_TEMPLATE.flatMap { block -> block.entries.map { it.first } }

        const val DEFAULT_DEVICE_NAME = "ChairPlay"
        const val DEFAULT_ICON_FILE = "carplay-icon.png"
        internal const val DEFAULT_CAR_LABEL = DEFAULT_DEVICE_NAME
        const val SETTINGS_FILE = "settings.properties"
        private const val SETTINGS_REMEDY = "delete it to get the defaults back (the Wi-Fi too)"
        /** The blue the HUD's animated maneuvers fill with (native/maneuver/render.c, ice). */
        const val DEFAULT_ACCENT = "#52c4ff"
        /** audio.duck.db: from about a thirtieth of the volume to the full volume. */
        val DUCK_DECIBELS = -30..0
        internal const val DEFAULT_ALTSCREEN_WIDTH = 1440
        internal const val DEFAULT_ALTSCREEN_HEIGHT = 540

        /**
         * The folder of the packaged exe, whose path jpackage's launcher puts in `jpackage.app-path`: the
         * program and its data live together there. Run any other way (installDist, an IDE), the working directory.
         */
        fun appDirectory(appPath: String? = System.getProperty("jpackage.app-path")): Path =
            appPath?.let { Path.of(it).toAbsolutePath().parent } ?: Path.of(System.getProperty("user.dir")).toAbsolutePath()

        /** wifi.ssid, wifi.passphrase and wifi.security from [settings]; null while wifi.ssid is blank. */
        private fun savedWifi(settings: Properties): SavedWifi? {
            val ssid = settings.getProperty("wifi.ssid")?.ifBlank { null } ?: return null
            val security = settings.getProperty("wifi.security")?.trim()?.ifEmpty { null } ?: "WPA_WPA2"
            val choices = Iap2WirelessSecurity.entries.associateBy { it.name }
            require(security in choices) { "wifi.security must be one of ${choices.keys.joinToString()}, not $security" }
            return SavedWifi(ssid, settings.getProperty("wifi.passphrase").orEmpty(), choices.getValue(security))
        }
    }
}
