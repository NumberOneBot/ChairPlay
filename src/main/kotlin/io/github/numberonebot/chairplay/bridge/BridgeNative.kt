package io.github.numberonebot.chairplay.bridge

import com.sun.jna.Callback
import com.sun.jna.Library
import com.sun.jna.Native
import com.sun.jna.NativeLibrary
import com.sun.jna.ptr.IntByReference
import java.io.File
import java.nio.ByteBuffer

/**
 * chairplay_bridge.dll (native/bridge, bridge.h): publishes the picture as a named D3D11 shared texture and keeps
 * the status file. Pointers are direct buffers. The frame calls are serialised inside the DLL, and the telemetry calls
 * apart from them.
 */
@Suppress("FunctionName")
internal interface BridgeLibrary : Library {
    /** Creates the device (WARP when [warp] is 1) and the status file in state 1; 0, else negative ([cpb_last_error]). */
    fun cpb_frame_open(warp: Int): Int

    /** Copies one BGRA picture into the shared texture, a new one (and generation) when the size changes; 0 or negative. */
    fun cpb_frame_publish(
        bgra: ByteBuffer, width: Int, height: Int, stride: Int, safeX: Int, safeY: Int, safeW: Int, safeH: Int,
    ): Int

    fun cpb_frame_state(state: Int)

    /** Writes state 0 for a game still holding the status file, then releases the device and textures. */
    fun cpb_frame_close()

    /** Creates the 256-byte telemetry file when no game has, else opens it without clearing it; 0, else negative. */
    fun cpb_telemetry_open(): Int

    /** Copies the telemetry file into [out256]; 1 when consistent, 0 when closed or torn. */
    fun cpb_telemetry_read(out256: ByteBuffer): Int

    fun cpb_telemetry_close()

    /** For tests: writes [in256] into the telemetry file under its seqlock, as a game does; 0 or negative. */
    fun cpb_test_write_telemetry(in256: ByteBuffer): Int

    /** The calling thread's last failure with its HRESULT in hex. */
    fun cpb_last_error(): String

    /** For tests: copies the status file as a reader does; 1 when consistent, 0 when missing or torn. */
    fun cpb_test_read_status(out256: ByteBuffer): Int

    /** For tests: opens [name] on a second device and reads its size and the pixel at 0,0; 0 or negative. */
    fun cpb_test_open_texture(name: String, width: IntByReference, height: IntByReference, firstPixel: IntByReference): Int

    /** For tests: the next publish finds the device lost; with [failNewDevice] 1 no new one can be made either. */
    fun cpb_test_lose_device(failNewDevice: Int)

    /** For tests: runs [during] while holding the lock the frame calls take. */
    fun cpb_test_hold_frame_lock(during: Held)

    fun interface Held : Callback {
        fun invoke()
    }
}

internal object BridgeNative {
    private const val NAME = "chairplay_bridge"

    private val loaded: Result<BridgeLibrary> by lazy {
        System.getProperty("java.library.path").split(File.pathSeparator).forEach { NativeLibrary.addSearchPath(NAME, it) }
        try {
            Result.success(Native.load(NAME, BridgeLibrary::class.java))
        } catch (e: UnsatisfiedLinkError) {
            Result.failure(e)
        }
    }

    /** Null once the library has loaded, else why it could not. */
    val loadError: String? get() = loaded.exceptionOrNull()?.let { it.message ?: "$NAME.dll not found" }

    /** The loaded library; throws the load's error when [loadError] is set. */
    val lib: BridgeLibrary get() = loaded.getOrThrow()
}
