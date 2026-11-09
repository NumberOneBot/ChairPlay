package io.github.numberonebot.chairplay.hud.maneuver

import io.github.numberonebot.chairplay.log.AppLog
import io.github.numberonebot.chairplay.log.LogLevel
import io.github.numberonebot.chairplay.platform.NativeLibraries
import java.nio.ByteBuffer

/**
 * chairplay_maneuver.dll (native/maneuver): the MHI2Q project's maneuver renderer, run frame by frame inside the
 * HUD window's OpenGL 3.3 core context and drawing into a texture of its own. Every call except [glslToCore] needs
 * that context current on the calling thread. The library is looked for on java.library.path, like speex_echo.dll.
 */
internal object ManeuverNative {
    /** Null once the library has loaded, else why it could not. */
    val loadError: String? by lazy {
        try {
            System.loadLibrary("chairplay_maneuver")
            null
        } catch (e: UnsatisfiedLinkError) {
            e.message ?: "${NativeLibraries.fileName("chairplay_maneuver")} not found"
        }
    }

    /** [frame]'s result bits. */
    const val RENDERED = 1
    /** A transition runs: the next frame should follow soon. */
    const val ANIMATING = 2
    /** Only the destination flag waves: a slower frame rate does. */
    const val LOOPING = 4
    /** A maneuver is on screen, not cleared. */
    const val SHOWING = 8

    /** The renderer's whole frame in its own pixels (protocol.h); the texture keeps this shape. */
    const val FRAME_WIDTH = 328
    const val FRAME_HEIGHT = 181
    /** Where a maneuver is centred and framed in that frame: the cluster's popup crop. */
    const val CROP_X = 59
    const val CROP_Y = 27
    const val CROP_WIDTH = 210
    const val CROP_HEIGHT = 153

    /** Creates the renderer drawing into a [width] x [height] texture and returns that texture, or 0 ([lastError]). */
    @JvmStatic external fun init(width: Int, height: Int): Int

    @JvmStatic external fun lastError(): String

    @JvmStatic external fun resize(width: Int, height: Int)

    /**
     * Replaces the destination flag's pictures: [frames] frames of [frameWidth] x [frameHeight] RGBA in a direct
     * buffer, side by side in one row, top row first. False before [init] or for a buffer too small.
     */
    @JvmStatic external fun flagAtlas(pixels: ByteBuffer, frameWidth: Int, frameHeight: Int, frames: Int): Boolean

    /** The MHI2Q project's destination flag (its flag_atlas.rgba): [FLAG_FRAMES] frames of [FLAG_FRAME] square. */
    const val FLAG_ATLAS = "/hud/destination-flag.rgba"
    /** ChairPlay's own chequered flag of the same shape, drawn before the project's was taken; kept to compare. */
    const val FLAG_ATLAS_CHAIRPLAY = "/hud/destination-flag-chairplay.rgba"
    const val FLAG_FRAME = 128
    const val FLAG_FRAMES = 14

    /** Gives the renderer the destination flag [atlas] from the app's resources; false when it is missing or refused. */
    fun loadFlagAtlas(atlas: String = FLAG_ATLAS): Boolean {
        val bytes = ManeuverNative::class.java.getResourceAsStream(atlas)?.use { it.readBytes() } ?: return false
        val pixels = ByteBuffer.allocateDirect(bytes.size).put(bytes).flip()
        return flagAtlas(pixels, FLAG_FRAME, FLAG_FRAME, FLAG_FRAMES)
    }

    /** Queues one 48-byte command ([ManeuverPackets]) for the next [frame]. */
    @JvmStatic external fun command(packet: ByteArray): Boolean

    /** Applies the queued commands and draws when anything changed; [now] is a monotonic time in seconds. */
    @JvmStatic external fun frame(now: Double): Int

    /** Copies the texture, bottom row first, premultiplied RGBA, into a direct buffer; for tests. */
    @JvmStatic external fun read(width: Int, height: Int, pixels: ByteBuffer): Boolean

    @JvmStatic external fun shutdown()

    /** What the renderer wrote to stderr since the last call (the DLL keeps up to 16 KiB), cleared; null when nothing. */
    @JvmStatic external fun drainLog(): String?

    /** For tests: has the DLL write [text] to stderr, as the renderer does. */
    @JvmStatic external fun logSelfTest(text: String)

    /** The GLSL translation the GL layer applies to the renderer's shaders; needs no GL context. */
    @JvmStatic external fun glslToCore(source: String, fragment: Boolean): String?
}

/** Logs what the renderer wrote to stderr since the last call, each non-blank line as a warning tagged [native]. */
internal fun drainManeuverLog(log: AppLog) {
    ManeuverNative.drainLog()?.lineSequence()?.filter { it.isNotBlank() }?.forEach { log.log(LogLevel.WARNING, "[native] $it") }
}
