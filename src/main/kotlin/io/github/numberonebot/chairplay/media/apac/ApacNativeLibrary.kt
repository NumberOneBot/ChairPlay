package io.github.numberonebot.chairplay.media.apac

import com.sun.jna.Function
import com.sun.jna.Memory
import com.sun.jna.Native
import com.sun.jna.NativeLibrary
import com.sun.jna.Platform
import com.sun.jna.Pointer
import com.sun.jna.ptr.IntByReference
import com.sun.jna.ptr.PointerByReference
import java.io.IOException
import java.nio.file.Files
import java.nio.file.Path

internal data class ApacFormat(val sampleRate: Int, val channels: Int, val framesPerPacket: Int, val layoutTag: Int)

/**
 * APAC codec, ABI v1 (mib2q-carplay-rgi apac_bridge.h).
 * This adapter contains no decoder implementation or extracted tables. The caller supplies
 * an absolute DLL path; an absent or incompatible DLL leaves APAC unavailable.
 * Keep the library alive for the process lifetime so no stream can call an unloaded function.
 */
internal class ApacNativeLibrary private constructor(
    @Suppress("unused") private val library: NativeLibrary,
    private val create: Function,
    private val decode: Function,
    private val reset: Function,
    private val destroy: Function,
    private val getInfo: Function,
) {
    fun open(cookie: ByteArray): Pair<Pointer, ApacFormat> {
        if (cookie.size !in 1..ApapCookies.MAX_COOKIE_BYTES) throw IOException("Invalid APAC cookie size")
        val configuration = apacCookieAtom(cookie)
        val handle = PointerByReference()
        val channels = IntByReference()
        val frames = IntByReference()
        val rate = IntByReference()
        val status = create.invokeInt(arrayOf(configuration, configuration.size.toLong(), handle, channels, frames, rate))
        val pointer = handle.value
        if (status != 0 || pointer == null) {
            if (pointer != null) destroy(pointer)
            throw IOException("APAC configuration rejected (status $status)")
        }
        try {
            Memory(16).use { info ->
                info.clear()
                if (getInfo.invokeInt(arrayOf(pointer, info)) != 0) throw IOException("APAC format unavailable")
                val format = ApacFormat(info.getInt(8), info.getInt(0), info.getInt(4), info.getInt(12))
                if (format.sampleRate != rate.value || format.channels != channels.value ||
                    format.framesPerPacket != frames.value) throw IOException("Inconsistent APAC format")
                if (format != ApacFormat(48000, 6, 1024, 0x007c0006)) {
                    throw IOException("Unsupported APAC rate or channel layout")
                }
                return pointer to format
            }
        } catch (failure: Exception) {
            destroy(pointer)
            throw failure
        }
    }

    fun decode(pointer: Pointer, packet: ByteArray, output: ShortArray): Int {
        val frames = IntByReference()
        val status = decode.invokeInt(arrayOf(pointer, packet, packet.size.toLong(), output, output.size.toLong(), frames))
        if (status != 0) throw IOException("APAC packet rejected (status $status)")
        if (frames.value !in 0..1024) throw IOException("Invalid APAC decoded frame count")
        return frames.value
    }

    fun reset(pointer: Pointer) { reset.invokeVoid(arrayOf(pointer)) }
    fun destroy(pointer: Pointer) { destroy.invokeVoid(arrayOf(pointer)) }

    companion object {
        fun load(path: Path): ApacNativeLibrary {
            if (!Platform.isWindows() || Native.POINTER_SIZE != 8) throw IOException("APAC backend requires Windows x64")
            val absolute = path.toAbsolutePath().normalize()
            if (!Files.isRegularFile(absolute)) throw IOException("APAC decoder library not found")
            try {
                val library = NativeLibrary.getInstance(absolute.toString())
                // Two uint32 fields followed by five function pointers, all with the C ABI.
                Memory(48).use { api ->
                    api.clear()
                    val status = library.getFunction("spa_apac_codec_get_api").invokeInt(arrayOf(1, api, api.size()))
                    if (status != 0 || api.getInt(0) != 48 || api.getInt(4) != 1) {
                        throw IOException("Incompatible APAC decoder ABI")
                    }
                    val functions = (0..4).map { index ->
                        val pointer = api.getPointer(8L + index * 8L) ?: throw IOException("Incomplete APAC decoder ABI")
                        Function.getFunction(pointer, Function.C_CONVENTION)
                    }
                    return ApacNativeLibrary(library, functions[0], functions[1], functions[2], functions[3], functions[4])
                }
            } catch (failure: UnsatisfiedLinkError) {
                throw IOException("APAC decoder library could not be loaded", failure)
            }
        }
    }
}
