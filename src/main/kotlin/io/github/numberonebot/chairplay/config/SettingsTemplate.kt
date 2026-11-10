package io.github.numberonebot.chairplay.config

import io.github.numberonebot.chairplay.config.DesktopConfig.Companion.DEFAULT_ACCENT
import io.github.numberonebot.chairplay.config.DesktopConfig.Companion.DEFAULT_CAR_LABEL
import io.github.numberonebot.chairplay.ui.BuildInfo

/** log.level in a new settings file: a release build (it has a version) logs warnings and errors, a development build more. */
internal fun defaultLogLevel(version: String?) = if (version != null) "warning" else "info"

/** settings.properties as the first start writes it, and what an older file is completed with. */
internal val SETTINGS_TEMPLATE = listOf(
    SettingBlock("Router Wi-Fi details sent to the iPhone when CarPlay connects.", "wifi.ssid" to "", "wifi.passphrase" to ""),
    SettingBlock("Ethernet adapter GUID; blank selects the only active physical adapter when Wi-Fi is disconnected.",
        "network.ethernet.adapter" to ""),
    SettingBlock("'NONE', 'WEP', 'WPA_WPA2', 'WPA3_TRANSITION' or 'WPA3_ONLY'", "wifi.security" to "WPA_WPA2"),
    SettingBlock("Paired iPhone's Bluetooth address, needed only when several phones are paired.", "iphone.address" to ""),
    SettingBlock(
        "Seconds an iPhone that paired before has to join the Wi-Fi before the app starts over (a new one has five minutes).",
        "iphone.join.seconds" to "30",
    ),
    SettingBlock(
        "'true' connects to the iPhone as the app starts, 'false' waits for Connect.",
        "ui.autoconnect" to "true",
    ),
    SettingBlock("Check for a newer ChairPlay release at startup.", "updates.check.on.start" to "true"),
    SettingBlock(
        "'true' shows CarPlay over the settings as soon as the iPhone connects, 'false' stays on the settings\n" +
            "until you choose CarPlay.",
        "ui.autoshow" to "true",
    ),
    SettingBlock(
        "'true' shows each controls window button's key on it; 'false' shows them only while Alt is held.",
        "ui.controls.keys" to "false",
    ),
    SettingBlock("Use the keyboard to type in CarPlay when the iPhone requests text input; reconnect to apply.", "keyboard.text.input" to "true"),
    SettingBlock(
        "The accent colour of the screens, the controls window, and the HUD, written #rrggbb; its lighter and\n" +
            "fainter shades are made from it.",
        "ui.accent" to DEFAULT_ACCENT,
    ),
    SettingBlock(
        "The port of the settings' address in a browser, http://localhost:7529 (7529 spells PLAY on a phone keypad).\n" +
            "When another program has it, the app takes a free port for that run and says so on the Connection page.",
        "ui.port" to "7529",
    ),
    SettingBlock(
        "'true' lets phones and other PCs on the local network open the settings at this PC's address; 'false' keeps\n" +
            "them to this PC. Applies at the next start.",
        "ui.network" to "false",
    ),
    SettingBlock("The car's name on the iPhone (CarPlay settings, the network); blank is 'ChairPlay'.", "device.name" to ""),
    SettingBlock(
        "The car button on the CarPlay home screen: its label, and a square image in this folder\n" +
            "(sent at 192 pixels at most). carplay-icon.png is written with ChairPlay's armchair to replace.",
        "car.label" to DEFAULT_CAR_LABEL, "car.icon" to "carplay-icon.png",
    ),
    SettingBlock(
        "Sound devices: 'default' (the Windows default) or a part of a device name that\n" +
            "`ChairPlay.exe devices` lists.",
        "audio.output" to "default", "audio.input" to "default",
    ),
    SettingBlock(
        "Echo cancellation for calls through speakers, without headphones (experimental): 'true' or 'false'.",
        "audio.echo.cancellation" to "false",
    ),
    SettingBlock(
        "Music playback: 'buffered' (AAC and APAC when available) or 'standard'. Reconnect to apply.",
        "audio.music.mode" to "buffered",
    ),
    SettingBlock(
        "How standard CarPlay music starts, since the iPhone's first audio comes in bursts: silent for this many\n" +
            "milliseconds, then rising to full over this many (both 0 play it at once). The sound fades out over\n" +
            "the second as CarPlay disconnects or reconnects and as the app closes.",
        "audio.start.silence.ms" to "2000",
        "audio.start.fade.ms" to "800",
    ),
    SettingBlock(
        "How loud CarPlay's music stays under a navigation prompt, in decibels from -30 to 0 (-14 is\n" +
            "Apple's own, a fifth of the volume), and whether a level the iPhone sends wins over it: 'true' or 'false'.",
        "audio.duck.db" to "-14",
        "audio.duck.iphone" to "true",
    ),
    SettingBlock(
        "Write every message the iPhone sends about the route, the song, calls and its battery to\n" +
            "logs\\carplay-data-<date-time>.jsonl in this folder, one JSON object per line: 'true' or 'false'.",
        "log.carplay.data" to "false",
    ),
    SettingBlock(
        "How much the app's log tells, in logs\\chairplay-<date-time>.log in this folder\n" +
            "(the ten newest are kept): error, warning, info, debug, or trace (with every packet).",
        "log.level" to defaultLogLevel(BuildInfo.load().version),
    ),
    SettingBlock(
        "The CarPlay screen the iPhone draws, in pixels (positive even numbers), and its frame rate (30-60).\n" +
            "iOS draws at @3x from about 1280x720 on both sides, at @2x below; more pixels fit more on screen.",
        "display.width" to "1280", "display.height" to "720", "display.fps" to "60",
    ),
    SettingBlock(
        "Video for both screens: h264, or h264+h265 to let the iPhone choose. Reconnect to apply.",
        "video.codecs" to "h264+h265",
    ),
    SettingBlock(
        "Video decoding: hardware (D3D11VA, with software fallback), or software. Reconnect to apply.",
        "video.decoder" to "hardware",
    ),
    SettingBlock(
        "Its physical size: the millimetres, or a pixel density. Blank (best) uses 200 mm wide with the height\n" +
            "from the aspect ratio; other proportions than the pixels' squeeze or stretch the picture.",
        "display.physical.width.mm" to "", "display.physical.height.mm" to "", "display.ppi" to "",
    ),
    SettingBlock(
        "The window on this PC; the picture is scaled into it. Blank uses the display size.\n" +
            "The app then remembers where you leave the window and its size, until these change.",
        "window.width" to "", "window.height" to "",
    ),
    SettingBlock(
        "Keep each window above other windows. Changes apply immediately.",
        "window.always.on.top" to "false", "altscreen.always.on.top" to "false",
    ),
    SettingBlock(
        "'true' shows the CarPlay window and brings it to the front when a call comes in. Its taskbar button\n" +
            "flashes on a call either way. Changes apply immediately.",
        "window.front.on.call" to "false",
    ),
    SettingBlock(
        "'true' resizes the windows only with the picture's shape (a maximized one still fills the screen),\n" +
            "'false' lets them take any shape.",
        "window.keep.aspect" to "true",
    ),
    SettingBlock(
        "'true' opens a window of on-screen car controls: the knob, back, home, media, calls, Siri and the\n" +
            "AltScreen's zoom; 'false' does not.",
        "window.controls" to "false",
    ),
    SettingBlock(
        "Altscreen (experimental): the car's instrument cluster as a second CarPlay screen in a window of its\n" +
            "own, 'true' or 'false' (the iPhone learns of it when it connects); its size in pixels (positive even\n" +
            "numbers); what it starts with: 'map', 'map-card', 'hud' or 'card' (the turn card).",
        "altscreen.enabled" to "false", "altscreen.width" to "1440", "altscreen.height" to "540",
        "altscreen.content" to "map-card",
    ),
    SettingBlock(
        "Where the altscreen shows the navigation app's ETA and turn card on Map + card: 'auto' (the driver's\n" +
            "side, as display.right.hand.drive declares it), 'left', 'right', or 'top'.",
        "altscreen.cards" to "top",
    ),
    SettingBlock(
        "Whether the iPhone keeps drawing the map view picked last while the altscreen shows the HUD, 'true' or\n" +
            "'false': F11 then comes back to the map at once, at the cost of the iPhone's work and Wi-Fi.",
        "altscreen.hud.stream" to "false",
    ),
    SettingBlock(
        "Altscreen pixels at each edge the iPhone keeps its map text and car marker out of, for an overlay;\n" +
            "all zero uses the whole panel.",
        "altscreen.safe.top" to "0", "altscreen.safe.bottom" to "0", "altscreen.safe.left" to "0",
        "altscreen.safe.right" to "0",
    ),
    SettingBlock(
        "The altscreen's own part of its frame for the maps (Map, Map + card) and for the turn card: 'full'; of all\n" +
            "its height, five eighths ('left5', 'center5', 'right5'), a half ('left4', 'center4', 'right4'), or three\n" +
            "eighths ('left3', 'center3', 'right3'); or 'custom', the rectangle in .area.custom: 'x, y, width, height'\n" +
            "in altscreen pixels, blank until set. Then the margins inside that part its map text and car marker keep\n" +
            "out of, in pixels. A group with the full frame and blank margins uses altscreen.safe.*. The altscreen\n" +
            "moves to a group's part as it changes view.",
        *AreaGroup.entries.flatMap { group ->
            listOf(areaKey(group) to defaultArea(group).key, customKey(group) to "") +
                SAFE_EDGES.map { edge -> safeKey(group, edge) to "" }
        }.toTypedArray(),
    ),
    SettingBlock("'system' (follow the system theme), 'light', 'dark', or 'phone' (the iPhone decides).", "display.theme" to "system"),
    SettingBlock("'true' puts CarPlay's dock and controls on the right, 'false' on the left.", "display.right.hand.drive" to "false"),
    SettingBlock("Where the dock goes: 'auto' (the iPhone decides), 'driver' (the driver's side) or 'bottom'.", "display.dock" to "auto"),
    SettingBlock(
        "Pixels at each edge CarPlay keeps its controls out of, and whether it may still draw there ('true' or 'false').",
        "display.safe.top" to "0", "display.safe.bottom" to "0", "display.safe.left" to "0", "display.safe.right" to "0",
        "display.safe.draw.outside" to "true",
    ),
    SettingBlock(
        "Whether the CarPlay picture is published as a shared texture for other apps on this PC to show,\n" +
            "'true' or 'false'.",
        "display.share" to "false",
    ),
    SettingBlock("'square' (the picture fills the window to its corners) or 'rounded' (CarPlay rounds them).",
        "display.corners" to "square"),
    SettingBlock(
        "The touchscreen CarPlay is told of: 'none' (CarPlay leaves out its scroll buttons; clicks and drags still\n" +
            "tap, the keys move the focus), 'swipe' or 'tap' (a touchscreen that swipes or only taps; CarPlay shows\n" +
            "scroll buttons).",
        "display.touch" to "none",
    ),
    SettingBlock(
        "Spoofed GPS: the iPhone navigates by a car this app drives (F10 or a gamepad), 'true' or 'false' (the\n" +
            "iPhone learns of it when it connects); whether it also gets the car's wheel speed for its dead\n" +
            "reckoning: 'true' or 'false'.",
        "gps.enabled" to "false", "gps.wheel.speed" to "true",
    ),
    SettingBlock(
        "Whether the position, heading, and speed another app on this PC sends drive the GPS car instead\n" +
            "of the gamepad and keys while they come, 'true' or 'false'.",
        "gps.external" to "false",
    ),
    SettingBlock(
        "Where the car is: 'latitude, longitude' or a Google or Apple Maps link, and its heading in degrees\n" +
            "(0 north, 90 east); the app writes them as the car stops, and the next start begins there. Whether\n" +
            "the heading goes to the iPhone as the course in the sentences, 'true' or 'false'.",
        "gps.start" to "", "gps.heading" to "0", "gps.heading.send" to "true",
    ),
    SettingBlock(
        "The car: its power in hp (20-2000) and its mass in kg (300-10000); how it turns, 'gentle', 'normal' or\n" +
            "'sharp'; drive mode's gauges, 'large' or 'small' in the window's lower corners (Shift+F10); and what the\n" +
            "gauges' wheel shows, 'steering' (the car up, turning with the input like a real wheel, for a track-up\n" +
            "map like Google Maps) or 'heading' (north up, for a north-up map like Apple Maps). Speed units are\n" +
            "'metric' (km/h) or 'imperial' (mph), for the gauges and speed readouts only. The gauges' style:\n" +
            "'german' (both clockwise over the top), 'italian' (both from 6 o'clock to 3, yellow needles) or\n" +
            "'british' (the tachometer from 5 o'clock to 2, the speedometer back from 7 to 10, green needles).",
        "gps.car.power.hp" to "300", "gps.car.mass.kg" to "1500", "gps.car.handling" to "normal", "gps.gauges" to "large",
        "gps.wheel" to "steering", "gps.units" to "metric", "gps.gauges.style" to "german",
    ),
    SettingBlock(
        "Experiments in what the iPhone takes from the GPS: fixes a second, '1', '4' (as the iPhone asks) or '10';\n" +
            "and the sentences, 'classic' (\$GPGGA, \$GPRMC) or 'modern' (\$GNGGA, \$GNRMC, \$GNGSA, \$GNVTG).",
        "gps.rate" to "4", "gps.nmea" to "classic",
    ),
    SettingBlock(
        "How an update's sentences go to the iPhone: 'params' (a parameter each, in one message) or 'messages' (a\n" +
            "message each), which the iPhone follows; or 'joined' (one string in one message, as DiPlay sends them),\n" +
            "which it ignores.",
        "gps.framing" to "params",
    ),
)

/** A commented group of settings in the template, with their default values. */
internal class SettingBlock(val comment: String, vararg entries: Pair<String, String>) {
    val entries = entries.toList()

    fun text(shown: List<Pair<String, String>>): String =
        comment.lines().joinToString("") { "# $it\n" } + shown.joinToString("") { (key, value) -> "$key=$value\n" }
}
