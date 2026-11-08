package io.github.numberonebot.chairplay.session

import com.shilapi.xcertplay.airplay.AirPlayConfig
import com.shilapi.xcertplay.airplay.AirPlayIdentity
import com.shilapi.xcertplay.airplay.AirPlayMediaHandler
import com.shilapi.xcertplay.airplay.CarPlayMediaEngine
import com.shilapi.xcertplay.airplay.MediaSink
import com.shilapi.xcertplay.iap2.session.Iap2Session
import com.shilapi.xcertplay.iap2.wire.Iap2Frame
import com.shilapi.xcertplay.mfi.Iap2MfiAuthenticationClient
import com.shilapi.xcertplay.mfi.LocalMfiAuthenticationClient
import com.shilapi.xcertplay.mfi.MfiAuthenticator
import com.shilapi.xcertplay.network.WirelessHotspotManager
import com.shilapi.xcertplay.transport.BlockingDuplexByteStream
import com.shilapi.xcertplay.transport.Iap2IdentificationConfig
import com.shilapi.xcertplay.transport.Iap2LocationProvider
import com.shilapi.xcertplay.transport.Iap2WirelessCarPlayEndpoint
import com.shilapi.xcertplay.transport.Iap2WirelessControlClient
import com.shilapi.xcertplay.transport.Iap2WirelessControlResult
import io.github.numberonebot.chairplay.bluetooth.WindowsBluetooth
import io.github.numberonebot.chairplay.bluetooth.selectIphone
import io.github.numberonebot.chairplay.carplay.DataSubscriptions
import io.github.numberonebot.chairplay.config.DesktopConfig
import io.github.numberonebot.chairplay.discovery.DesktopBonjour
import io.github.numberonebot.chairplay.network.DesktopNetwork
import java.io.Closeable
import java.io.IOException
import java.net.InetAddress
import java.util.UUID

internal val IAP2_IPHONE_UUID: UUID = UUID.fromString("00000000-deca-fade-deca-deafdecacafe")

/** Iap2Session's own default for a send. */
private const val SUBSCRIPTION_SEND_TIMEOUT_MILLIS = 5_000L

/** What a bring-up takes from the PC's radios and the iPhone; tests substitute fakes. */
internal interface WirelessParts {
    val mfi: MfiAuthenticator?
    fun network(): WirelessHotspotManager
    fun radioAddress(): String
    fun media(sink: MediaSink, microphoneEnabled: Boolean): AirPlayMediaHandler
    /** Returns the started advertisement. */
    fun advertise(config: AirPlayConfig, identity: AirPlayIdentity, addresses: List<InetAddress>): Closeable
    fun connectIphone(): BlockingDuplexByteStream
    fun control(
        session: Iap2Session,
        identification: Iap2IdentificationConfig,
        endpoint: Iap2WirelessCarPlayEndpoint,
        timeoutMillis: Long,
        onReady: () -> Unit,
        keepAliveAfterDeadline: () -> Boolean,
        /** Each frame the control loop does not handle itself: route guidance, now playing, calls, power. */
        onIncoming: (Iap2Frame) -> Unit,
        /** Answers the iPhone's location requests; null offers none. */
        location: Iap2LocationProvider?,
    ): Iap2WirelessControlResult
}

/** The parts of this PC: its MFi files, Wi-Fi, Bluetooth radio, Bonjour, and the iPhone paired with it. */
internal class DesktopParts(private val config: DesktopConfig, private val log: (String) -> Unit) : WirelessParts {
    override val mfi = LocalMfiAuthenticationClient.load(config.mfiDirectory())

    /** Read for each bring-up, so a network changed on the start screen applies with the next connection. */
    override fun network() = DesktopNetwork(
        config.currentWifi() ?: throw IOException("no Wi-Fi network is set: enter it on the start screen (wifi.ssid)"),
        config.currentEthernetAdapter(),
    )

    override fun radioAddress() = WindowsBluetooth.localRadioAddress()

    override fun media(sink: MediaSink, microphoneEnabled: Boolean) = CarPlayMediaEngine(sink, microphoneEnabled)

    /** DesktopBonjour leaves earlier instances registered when a later address fails. */
    override fun advertise(config: AirPlayConfig, identity: AirPlayIdentity, addresses: List<InetAddress>) =
        DesktopBonjour(config, identity, addresses, log).also {
            try {
                it.start()
            } catch (error: Exception) {
                it.close()
                throw error
            }
        }

    override fun connectIphone(): BlockingDuplexByteStream {
        val iphone = selectIphone(WindowsBluetooth.pairedDevices(), config.iphoneAddress)
        log("RFCOMM connecting to ${iphone.name} ${iphone.address}")
        return WindowsBluetooth.connectRfcomm(iphone.address, IAP2_IPHONE_UUID).toDuplexStream(log)
    }

    override fun control(
        session: Iap2Session,
        identification: Iap2IdentificationConfig,
        endpoint: Iap2WirelessCarPlayEndpoint,
        timeoutMillis: Long,
        onReady: () -> Unit,
        keepAliveAfterDeadline: () -> Boolean,
        onIncoming: (Iap2Frame) -> Unit,
        location: Iap2LocationProvider?,
    ) = Iap2WirelessControlClient(session, Iap2MfiAuthenticationClient(mfi)).run(
        identification, endpoint, timeoutMillis, locationProvider = location,
        // Upstream's subscriptions have just gone out on this link; ours ask for more of the same.
        onReady = {
            onReady()
            DataSubscriptions.send({ session.send(it, SUBSCRIPTION_SEND_TIMEOUT_MILLIS) }, log)
        },
        onIncoming = onIncoming, keepAliveAfterDeadline = keepAliveAfterDeadline, onProgress = log,
    )
}
