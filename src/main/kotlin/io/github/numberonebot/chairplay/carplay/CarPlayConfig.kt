package io.github.numberonebot.chairplay.carplay

import com.shilapi.xcertplay.airplay.AirPlayConfig
import com.shilapi.xcertplay.airplay.AirPlayIdentity
import io.github.numberonebot.chairplay.config.DesktopConfig
import io.github.numberonebot.chairplay.config.bootstrapRouteGuidance
import io.github.numberonebot.chairplay.config.carPlayAvailability
import io.github.numberonebot.chairplay.config.sourceVersion
import java.security.MessageDigest

/** What the iPhone is told of the receiver and its screens, from ChairPlay's settings ([DesktopConfig]). */
object CarPlayConfig {
    /** The AirPlay port the receiver listens on first, which the firewall rule lets in. */
    const val PORT = 7000

    /**
     * DiPlay's production values (CarPlayHostActivity.airPlayConfig) with [config]'s; btMac is replaced by the radio's.
     * The CarPlay home screen shows the receiver's icon where a car shows its maker's; tapping it opens the settings
     * screen.
     */
    fun airPlay(config: DesktopConfig): AirPlayConfig = AirPlayConfig(
        keyboardInput = config.keyboardTextInput,
        deviceName = config.deviceName,
        deviceId = deviceId(config.identity),
        btMac = deviceId(config.identity),
        sourceVersion = sourceVersion(),
        main = config.screen.toAirPlay(),
        rightHandDrive = config.display.rightHandDrive,
        port = PORT,
        hevc = config.h265,
        microphone = true,
        // Without Opus the wireless iPhone opens no audio stream at all and plays on its own speaker.
        microphoneOpus = true,
        videoInCar = false,
        mainBufferedAudio = config.bufferedMusic,
        spatialAudio = config.spatialAudio,
        cluster = config.cluster?.toCarPlay(),
        oemLabel = config.carLabel,
        icons = listOf(config.homeIcon),
    )

    /**
     * [airPlay] for the next connection: what the iPhone learns of its screens, which it asks at each connection, as
     * [now] has it; the name as the app started with it ([config]), as the radio keeps it.
     */
    fun airPlayNow(config: DesktopConfig, now: DesktopConfig = config.now()): AirPlayConfig =
        airPlay(now).copy(deviceName = config.deviceName)

    /** DiPlayBootstrap.deviceId: a locally administered MAC derived from the AirPlay public key. */
    fun deviceId(identity: AirPlayIdentity): String {
        val bytes = MessageDigest.getInstance("SHA-256").digest(identity.publicKey).copyOf(6)
        bytes[0] = ((bytes[0].toInt() and 0xfc) or 0x02).toByte()
        return bytes.joinToString(":") { "%02X".format(it.toInt() and 0xff) }
    }

    /** The AirPlay version and the identification's switches, for the start-up log. */
    fun summary(): String =
        "airplay sourceVersion=${sourceVersion()} bootstrapRouteGuidance=${bootstrapRouteGuidance()} carPlayAvailability=${carPlayAvailability()}"
}
