package io.github.numberonebot.chairplay.carplay

import io.github.numberonebot.chairplay.drive.GeoPoint
import io.github.numberonebot.chairplay.drive.LocationLink
import io.github.numberonebot.chairplay.gps.ChannelState
import io.github.numberonebot.chairplay.gps.GpsChannel
import io.github.numberonebot.chairplay.gps.GpsMethod
import io.github.numberonebot.chairplay.gps.GpsReason
import io.github.numberonebot.chairplay.gps.OperationStage
import io.github.numberonebot.chairplay.gps.Release
import io.github.numberonebot.chairplay.gps.UsbDevice

/**
 * The gate a [CarPlayLocation] sends through: closed, nothing goes; [close] waits for a send under way, so once it
 * returns nothing more goes (the coordinator's barrier).
 */
class SendGate(open: Boolean) {
    @Volatile var isOpen = open
        private set

    /** [send]'s answer while open, else null; a [close] waits for it. */
    fun <T> pass(send: () -> T): T? = synchronized(this) { if (isOpen) send() else null }

    fun open() {
        isOpen = true
    }

    fun close() = synchronized(this) { isOpen = false }
}

/**
 * CarPlay's GPS as the GPS output's channel: each connection's [CarPlayLocation] sends through [gate], which the
 * coordinator opens and closes. Whether a session runs comes from [session]; whether it identified with the GPS, from
 * [identified], told as each bring-up reads the settings. It never reconnects anything (D4).
 */
class CarPlayGpsChannel(
    private val session: () -> Boolean,
    private val location: () -> LocationLink?,
) : GpsChannel {
    val gate = SendGate(open = false)

    @Volatile private var identifiedGps = false

    override val method = GpsMethod.CARPLAY

    override var onChanged: () -> Unit = {}

    /** A bring-up identifies with the GPS ([gps]) or without it. */
    fun identified(gps: Boolean) {
        identifiedGps = gps
        onChanged()
    }

    override fun state(): ChannelState = when {
        !session() -> ChannelState(GpsReason.CARPLAY_NOT_CONNECTED)
        identifiedGps -> ChannelState(null, sessionGps = true)
        else -> ChannelState(GpsReason.CARPLAY_RECONNECT_NEEDED, sessionGps = false)
    }

    override fun start(seed: GeoPoint, device: UsbDevice?): GpsReason? {
        state().reason?.let { return it }
        gate.open()
        return null
    }

    override fun quiesce() = gate.close()

    override fun release(returnTo: GeoPoint?, onStage: (OperationStage) -> Unit) = Release.NOTHING

    override val running: Boolean get() = gate.isOpen && session()

    override val sends: Long get() = location()?.status()?.sent ?: 0L
}
