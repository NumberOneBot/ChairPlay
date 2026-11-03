package io.github.numberonebot.chairplay.media.apac

import java.io.Closeable
import java.io.IOException

/** One worker owns the handle, from configuration through close. PCM order is C,L,R,Ls,Rs,LFE. */
internal class ApacNativeDecoder(private val library: ApacNativeLibrary, cookie: ByteArray) : Closeable {
    private val owner = Thread.currentThread()
    private val opened = library.open(cookie)
    private var handle = opened.first
    val format = opened.second
    private val output = ShortArray(format.channels * format.framesPerPacket)
    private var closed = false

    fun decode(packet: ByteArray): ShortArray {
        checkOpen()
        if (packet.size !in 1..ApapFrameReader.MAX_RECORD_BYTES) throw IOException("Invalid APAC access unit size")
        val frames = library.decode(handle, packet, output)
        return output.copyOf(frames * format.channels)
    }

    fun reset() {
        checkOpen()
        library.reset(handle)
    }

    override fun close() {
        check(Thread.currentThread() === owner) { "APAC decoder belongs to another worker" }
        if (closed) return
        closed = true
        library.destroy(handle)
    }

    private fun checkOpen() {
        check(Thread.currentThread() === owner) { "APAC decoder belongs to another worker" }
        check(!closed) { "APAC decoder is closed" }
    }
}
