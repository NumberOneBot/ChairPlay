package io.github.numberonebot.chairplay.carplay

import com.shilapi.xcertplay.iap2.wire.Iap2Frame
import com.shilapi.xcertplay.transport.Iap2ArtworkTransfer
import io.github.numberonebot.chairplay.data.PhoneDataBus
import java.io.Closeable

/** What the wireless bring-up hands over from its iAP2 links besides the bring-up itself. */
interface PhoneDataTap {
    /** A frame the control loop did not handle itself, on that link's control thread. */
    fun onFrame(link: DataLink, frame: Iap2Frame) {}

    /** A completed artwork file transfer; empty bytes clear the artwork. */
    fun onArtwork(link: DataLink, transfer: Iap2ArtworkTransfer) {}

    /** The bring-up that delivered the data is over: its session ended or failed. */
    fun onSessionEnded() {}

    companion object {
        val NONE: PhoneDataTap = object : PhoneDataTap {}
    }
}

/** The data [bus] and, with log.carplay.data, the data [log], fed by the iAP2 links of every CarPlay session. */
class CarPlayDataFeed(private val log: CarPlayDataLog?, val bus: PhoneDataBus = PhoneDataBus()) : PhoneDataTap, Closeable {
    private val decoder = Iap2DataDecoder(bus)

    override fun onFrame(link: DataLink, frame: Iap2Frame) {
        if (!CarPlayDataMessages.isData(frame.messageId)) return
        log?.frame(link, frame)
        decoder.accept(frame)
    }

    override fun onArtwork(link: DataLink, transfer: Iap2ArtworkTransfer) {
        log?.artwork(link, transfer)
        decoder.acceptArtwork(transfer)
    }

    override fun onSessionEnded() = decoder.clear()

    override fun close() {
        log?.close()
    }
}
