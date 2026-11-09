package io.github.numberonebot.chairplay.platform

import java.io.Closeable
import java.io.InputStream
import java.io.OutputStream
import java.util.UUID

data class PairedDevice(val name: String, val address: String, val classOfDevice: Int)

/** An open RFCOMM channel: the bytes the device sends and the way to send it bytes; closing it ends the channel. */
interface RfcommLink : Closeable {
    val input: InputStream
    val output: OutputStream
}

/** The PC's Bluetooth: its radio, the devices paired with it, and RFCOMM to a service on one of them. */
internal interface Bluetooth {
    /** The radio's address, written AA:BB:CC:DD:EE:FF. */
    fun radioAddress(): String

    fun pairedDevices(): List<PairedDevice>

    /** Connects to [service] on the device at [address]; the system finds the service's channel. */
    fun connectRfcomm(address: String, service: UUID): RfcommLink
}
