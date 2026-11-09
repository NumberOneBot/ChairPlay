package io.github.numberonebot.chairplay.bluetooth

import com.shilapi.xcertplay.transport.BlockingDuplexByteStream
import com.shilapi.xcertplay.transport.BluetoothRfcommDuplexStream
import com.shilapi.xcertplay.transport.BluetoothRfcommSocketAccess
import io.github.numberonebot.chairplay.platform.RfcommLink
import java.io.InputStream
import java.io.OutputStream

/** DiPlay's iAP2 byte stream over this link; closing the stream closes the link, once. */
internal fun RfcommLink.toDuplexStream(onDiagnostic: (String) -> Unit): BlockingDuplexByteStream =
    BluetoothRfcommDuplexStream(
        object : BluetoothRfcommSocketAccess {
            override fun inputStream(): InputStream = input
            override fun outputStream(): OutputStream = output
            override fun close() = this@toDuplexStream.close()
        },
        onDiagnostic,
    )
