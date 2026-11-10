package io.github.numberonebot.chairplay.session

import com.shilapi.xcertplay.airplay.AirPlayConfig
import com.shilapi.xcertplay.airplay.AirPlayIdentity
import com.shilapi.xcertplay.network.WirelessHotspotInfo
import com.shilapi.xcertplay.transport.Iap2IdentificationConfig
import com.shilapi.xcertplay.transport.Iap2WirelessCarPlayEndpoint
import io.github.numberonebot.chairplay.config.DesktopConfig
import io.github.numberonebot.chairplay.config.bootstrapRouteGuidance
import io.github.numberonebot.chairplay.config.carPlayAvailability
import io.github.numberonebot.chairplay.drive.GpsSettings
import java.net.Inet6Address
import java.net.InetAddress

/**
 * The values DiPlay's head-unit build identifies with, under [name] and keyed to this PC's Bluetooth radio; with the
 * spoofed [gps] on, the location component with the sentences it sends.
 */
internal fun desktopIdentification(
    radio: String,
    name: String = DesktopConfig.DEFAULT_DEVICE_NAME,
    gps: GpsSettings = GpsSettings(enabled = false),
) = Iap2IdentificationConfig(
    name = name,
    // ChairPlay's own since 1.0.9 (before, DiPlayDesktop, DiPlay and DIPLAY-<radio>): an iPhone may see a new accessory.
    modelIdentifier = "ChairPlay",
    manufacturer = "ChairPlay",
    serialNumber = "CHAIRPLAY-" + radio.replace(":", ""),
    firmwareVersion = "0.1.0",
    hardwareVersion = "1.0",
    // Only the wired transport reads this; the value matches DiPlay's head-unit build.
    carPlayUsbInterfaceNumber = 3,
    locationInformationEnabled = gps.enabled,
    vehicleSpeedEnabled = gps.enabled && gps.wheelSpeed,
    bootstrapRouteGuidance = bootstrapRouteGuidance(),
    carPlayAvailabilityEnabled = carPlayAvailability(),
)

/** The 0x4301/0x5703 endpoint; the address goes as upstream sends it, compressed and without scope. */
fun buildEndpoint(
    info: WirelessHotspotInfo,
    host: InetAddress,
    port: Int,
    config: AirPlayConfig,
    identity: AirPlayIdentity,
) = Iap2WirelessCarPlayEndpoint(
    ssid = info.ssid,
    passphrase = info.passphrase,
    channel = info.channel,
    security = info.security,
    ipAddresses = listOf(endpointText(host)),
    airPlayPort = port,
    deviceIdentifier = config.deviceId,
    publicKey = identity.publicKeyHex,
    sourceVersion = config.sourceVersion,
    accessPointBssid = info.accessPointBssid,
)

/** Android prints IPv6 in RFC 5952 short form; the JDK prints every group and the scope. */
private fun endpointText(address: InetAddress): String {
    if (address !is Inet6Address) return checkNotNull(address.hostAddress)
    val groups = (0 until 8).map { ((address.address[2 * it].toInt() and 0xff) shl 8) or (address.address[2 * it + 1].toInt() and 0xff) }
    var zerosAt = -1
    var zerosLength = 1
    var run = 0
    for ((index, group) in groups.withIndex()) {
        run = if (group == 0) run + 1 else 0
        if (run > zerosLength) {
            zerosLength = run
            zerosAt = index - run + 1
        }
    }
    val hex = groups.map { it.toString(16) }
    if (zerosAt < 0) return hex.joinToString(":")
    return hex.subList(0, zerosAt).joinToString(":") + "::" + hex.subList(zerosAt + zerosLength, 8).joinToString(":")
}
