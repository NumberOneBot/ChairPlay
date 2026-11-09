package io.github.numberonebot.chairplay.platform.windows

import com.sun.jna.Memory
import com.sun.jna.NativeLibrary
import com.sun.jna.Pointer
import com.sun.jna.WString
import com.sun.jna.ptr.LongByReference
import com.sun.jna.ptr.PointerByReference
import io.github.numberonebot.chairplay.media.apac.SpatialDevice
import io.github.numberonebot.chairplay.platform.NativeLibraries
import io.github.numberonebot.chairplay.platform.OutputDevice
import java.io.Closeable
import java.io.IOException

internal data class WasapiFormat(val sampleRate: Int, val channels: Int, val mask: Int, val bits: Int)

/** COM calls run on the creating worker; cancellation alone may come from another thread. */
internal class WasapiOutput private constructor(private val handle: Pointer, val format: WasapiFormat) : SpatialDevice {
    override val channels get() = format.channels
    override val description get() = "${format.channels} channels ${format.sampleRate} Hz mask=0x${format.mask.toString(16)}"
    private val owner = Thread.currentThread()
    private var closed = false

    override fun write(samples: ShortArray) {
        checkOwner()
        require(samples.size % format.channels == 0 && samples.size / format.channels <= 96000)
        checkResult(native.getFunction("cp_audio_write").invokeInt(arrayOf(handle, samples, samples.size / format.channels)))
    }

    override fun playedFrames(): Long {
        checkOwner()
        val result = LongByReference()
        checkResult(native.getFunction("cp_audio_position").invokeInt(arrayOf(handle, result)))
        return result.value
    }

    @Synchronized override fun cancel() {
        if (!closed) native.getFunction("cp_audio_cancel").invokeVoid(arrayOf(handle))
    }

    @Synchronized override fun close() {
        if (closed) return
        checkOwner()
        closed = true
        native.getFunction("cp_audio_close").invokeVoid(arrayOf(handle))
    }

    private fun checkOwner() {
        check(Thread.currentThread() === owner) { "WASAPI output belongs to another worker" }
        check(!closed) { "WASAPI output is closed" }
    }

    companion object {
        private val native by lazy {
            NativeLibraries.searchDirs(appPath = null).forEach {
                NativeLibrary.addSearchPath("chairplay_audio", it.path)
            }
            NativeLibrary.getInstance("chairplay_audio")
        }

        fun devices(): List<OutputDevice> = buildList {
            var index = 0
            while (true) add(device(index++) ?: break)
        }
        fun defaultDevice() = device(-1) ?: throw IOException("No Windows default output")

        private fun device(index: Int): OutputDevice? {
            Memory(2048).use { id -> Memory(512).use { name -> Memory(16).use { format ->
                val result = native.getFunction("cp_audio_device").invokeInt(arrayOf(index, id, 1024, name, 256, format))
                if (result == 1) return null
                checkResult(result)
                return OutputDevice(id.getWideString(0), name.getWideString(0), format.getInt(0), format.getInt(4), format.getInt(8))
            } } }
        }

        /** channels=0 chooses verified 5.1 or stereo; channels=6 must never fall back. */
        fun open(deviceId: String?, channels: Int): WasapiOutput {
            require(channels in listOf(0, 2, 6))
            Memory(16).use { format ->
                val handle = PointerByReference()
                checkResult(native.getFunction("cp_audio_open").invokeInt(arrayOf(deviceId?.let(::WString), channels, handle, format)))
                return WasapiOutput(handle.value ?: throw IOException("Windows audio handle missing"),
                    WasapiFormat(format.getInt(0), format.getInt(4), format.getInt(8), format.getInt(12)))
            }
        }

        private fun checkResult(result: Int) {
            if (result < 0) throw IOException("Windows audio failed (0x${result.toUInt().toString(16)})")
        }
    }
}
