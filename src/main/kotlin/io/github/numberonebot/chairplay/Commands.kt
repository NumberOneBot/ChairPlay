package io.github.numberonebot.chairplay

import com.shilapi.xcertplay.iap2.session.Iap2Session
import com.shilapi.xcertplay.mfi.Iap2MfiAuthenticationClient
import com.shilapi.xcertplay.mfi.LocalMfiAuthenticationClient
import com.shilapi.xcertplay.transport.Iap2IdentificationClient
import com.shilapi.xcertplay.transport.Iap2WirelessIdentification
import com.shilapi.xcertplay.transport.Iap2WirelessLinkRole
import com.shilapi.xcertplay.transport.forWirelessLink
import io.github.numberonebot.chairplay.bluetooth.selectIphone
import io.github.numberonebot.chairplay.bluetooth.toDuplexStream
import io.github.numberonebot.chairplay.log.AppLog
import io.github.numberonebot.chairplay.log.routeAllOutput
import io.github.numberonebot.chairplay.media.AudioDevices
import io.github.numberonebot.chairplay.platform.Platforms
import io.github.numberonebot.chairplay.session.IAP2_IPHONE_UUID
import io.github.numberonebot.chairplay.session.desktopIdentification
import io.github.numberonebot.chairplay.session.redactTrace
import java.nio.file.Path

// The command line's tools besides run: the paired devices and audio devices, and the Bluetooth go/no-go probe.

/**
 * Runs devices, probe-bt or (for anything else) the usage line, with the output in logs\<command>-<time>.log of
 * [appDir] instead of a console. Returns the process's exit code. [route] sends the process's output to the log.
 */
internal fun runCommand(
    args: Array<String>,
    appDir: Path = dataDirectory(),
    route: (AppLog) -> Unit = ::routeAllOutput,
): Int {
    route(appLog)
    val prefix = when (args.firstOrNull()) {
        "devices", "probe-bt" -> args[0]
        else -> "chairplay"
    }
    appLog.open(appDir.resolve("logs"), prefix)
    return when (args.firstOrNull()) {
        "devices" -> 0.also { listDevices() }
        "probe-bt" -> if (probeBluetooth(args.getOrNull(1))) 0 else 1
        else -> 0.also { log("usage: devices | probe-bt [iphone-address] | run") }
    }
}

internal fun listDevices() {
    val bluetooth = Platforms.current.bluetooth
    log("radio ${bluetooth.radioAddress()}")
    bluetooth.pairedDevices().forEach {
        log("${it.address}  class=0x%06X  ${it.name}".format(it.classOfDevice))
    }
    // The names audio.output and audio.input match against (Java Sound cuts them to 31 characters).
    log("audio outputs:")
    AudioDevices.outputs().forEach { log("  ${it.name}") }
    log("audio inputs:")
    AudioDevices.inputs().forEach { log("  ${it.name}") }
}

/**
 * The go/no-go check for wireless CarPlay on Windows: reach the iPhone's iAP2 service over RFCOMM
 * and complete identification and MFi authentication with DiPlay's own iAP2 code. Stops before
 * handing the iPhone any Wi-Fi endpoint.
 */
internal fun probeBluetooth(address: String?): Boolean {
    val bluetooth = Platforms.current.bluetooth
    val radio = bluetooth.radioAddress()
    val iphone = selectIphone(bluetooth.pairedDevices(), address)
    log("radio $radio, target ${iphone.name} ${iphone.address}")

    val mfiDirectory = dataDirectory().resolve("offline-mfi").toFile()
    val mfi = LocalMfiAuthenticationClient.load(mfiDirectory) { log("local MFi identity signs ($it-byte signature)") }

    // Not openIphoneLink: the probe reports the radio and target first, and the connection only once it is made.
    val link = bluetooth.connectRfcomm(iphone.address, IAP2_IPHONE_UUID)
    log("RFCOMM connected to the iAP2 service")
    val stream = link.toDuplexStream(::log)
    val session = Iap2Session.openWireless(stream, traceContext = "probe", onTrace = { log(redactTrace(it)) })
    try {
        val identification = desktopIdentification(radio).forWirelessLink(
            Iap2WirelessLinkRole.BLUETOOTH_BOOTSTRAP,
            Iap2WirelessIdentification(radio, "ChairPlayProbe"),
        )
        // The same two steps, in the same order, that Iap2WirelessControlClient.run begins with.
        Iap2IdentificationClient(session).identify(identification, 30_000L, ::log)
        log("iAP2 identification accepted")
        Iap2MfiAuthenticationClient(mfi).run(session, 30_000L, ::log)
        log("iAP2 authentication accepted")
        log("GO: the iPhone accepted ChairPlay over Windows Bluetooth")
        return true
    } catch (error: Exception) {
        log("NO-GO: ${error.javaClass.simpleName}: ${error.message}")
        return false
    } finally {
        session.close()
    }
}
