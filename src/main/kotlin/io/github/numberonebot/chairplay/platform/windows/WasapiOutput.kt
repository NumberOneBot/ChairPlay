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
import java.io.IOException

internal data class WasapiFormat(val sampleRate: Int, val channels: Int, val mask: Int, val bits: Int)

/**
 * COM calls run on the creating worker; cancellation alone may come from another thread. [channels] is the stream's,
 * 2 or 6, as the decoder makes it; [format] is the device's, which a wider surround output (7.1) widens it to.
 */
internal class WasapiOutput private constructor(
    private val handle: Pointer,
    val format: WasapiFormat,
    override val channels: Int,
) : SpatialDevice {
    override val description get() = "${format.channels} channels ${format.sampleRate} Hz mask=0x${format.mask.toString(16)}"
    private val owner = Thread.currentThread()
    private var closed = false
    // 5.1 laid into a wider output's frame; null when the stream fills the output as it is.
    private val slots = if (channels == 6 && format.channels != 6) surroundSlots(format.mask) else null

    override fun write(samples: ShortArray) {
        checkOwner()
        require(samples.size % channels == 0 && samples.size / channels <= 96000)
        val frame = slots?.let { spreadSurround(samples, it, format.channels) } ?: samples
        checkResult(native.getFunction("cp_audio_write").invokeInt(arrayOf(handle, frame, frame.size / format.channels)))
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

        /**
         * channels=0 chooses 5.1 when the device's speakers hold it ([surroundSlots]), else stereo; channels=6 must
         * never fall back. Surround opens in the device's own format, stereo as Windows converts it.
         */
        fun open(deviceId: String?, channels: Int): WasapiOutput {
            require(channels in listOf(0, 2, 6))
            val surround = channels != 2 && speakerMask(deviceId)?.let(::surroundSlots) != null
            if (channels == 6 && !surround) throw IOException("The Windows output has no 5.1 speakers")
            val stream = if (surround) 6 else 2
            Memory(16).use { format ->
                val handle = PointerByReference()
                checkResult(native.getFunction("cp_audio_open").invokeInt(arrayOf(deviceId?.let(::WString), stream, handle, format)))
                return WasapiOutput(handle.value ?: throw IOException("Windows audio handle missing"),
                    WasapiFormat(format.getInt(0), format.getInt(4), format.getInt(8), format.getInt(12)), stream)
            }
        }

        private fun speakerMask(deviceId: String?): Int? = runCatching {
            if (deviceId == null) defaultDevice().mask else devices().firstOrNull { it.id == deviceId }?.mask
        }.getOrNull()

        private fun checkResult(result: Int) {
            if (result < 0) throw IOException("Windows audio failed (0x${result.toUInt().toString(16)})")
        }
    }
}
