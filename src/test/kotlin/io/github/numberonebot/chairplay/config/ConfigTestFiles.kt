package io.github.numberonebot.chairplay.config

import com.shilapi.xcertplay.airplay.AirPlayConfig
import io.github.numberonebot.chairplay.carplay.CarPlayConfig
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.StandardOpenOption

/** A settings file with just the Wi-Fi network, as a set-up app has. */
internal fun writeTestSettings(dir: Path) {
    Files.writeString(dir.resolve("settings.properties"), "wifi.ssid=Home\nwifi.passphrase=secret123\n")
}

/** A DesktopConfig read from a fresh folder whose settings file has the Wi-Fi network and [lines]. */
internal fun configWith(vararg lines: String): DesktopConfig {
    val dir = Files.createTempDirectory("chairplay-config")
    writeTestSettings(dir)
    Files.writeString(dir.resolve("settings.properties"), lines.joinToString("") { "$it\n" }, StandardOpenOption.APPEND)
    return DesktopConfig(dir)
}

/** What the iPhone is told for these settings, as CarPlay builds it from them. */
internal val DesktopConfig.airPlay: AirPlayConfig get() = CarPlayConfig.airPlay(this)

/** What the iPhone is told at the next connection, as the file has it [now]. */
internal fun DesktopConfig.airPlayNow(now: DesktopConfig = now()): AirPlayConfig = CarPlayConfig.airPlayNow(this, now)
