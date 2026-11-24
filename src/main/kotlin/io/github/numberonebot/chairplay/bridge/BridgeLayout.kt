package io.github.numberonebot.chairplay.bridge

import io.github.numberonebot.chairplay.config.AreaRect
import java.nio.ByteBuffer
import java.nio.ByteOrder

/** Byte layouts of the two 256-byte game bridge files. All values are little-endian. */
object BridgeLayout {
    const val SIZE = 256
    const val STATUS_MAGIC = 0x52425043
    const val TELEMETRY_MAGIC = 0x54435043
    const val VERSION = 1
    const val STATUS_FILE = "ChairPlay.Bridge.v1"
    const val TELEMETRY_FILE = "ChairPlay.Telemetry.v1"
    const val STATUS_SIZE = 140
    const val TELEMETRY_SIZE = 156

    fun textureName(generation: Int): String = "ChairPlay.Main.v1.$generation"

    /** A little-endian view of [bytes] from position 0, leaving the caller's buffer untouched. */
    internal fun view(bytes: ByteBuffer): ByteBuffer = bytes.duplicate().order(ByteOrder.LITTLE_ENDIAN)

    internal fun readText(b: ByteBuffer, at: Int, max: Int): String {
        val raw = ByteArray(max) { b.get(at + it) }
        val end = raw.indexOf(0).let { if (it < 0) max else it }
        return String(raw, 0, end, Charsets.UTF_8)
    }

    internal fun writeText(b: ByteBuffer, at: Int, max: Int, text: String) {
        val raw = text.toByteArray(Charsets.UTF_8)
        // Keep room for the terminating zero and never cut a multi-byte character.
        var n = minOf(raw.size, max - 1)
        while (n > 0 && n < raw.size && (raw[n].toInt() and 0xC0) == 0x80) n--
        for (i in 0 until n) b.put(at + i, raw[i])
    }
}

data class BridgeStatus(
    val state: Int,
    val generation: Int,
    val width: Int,
    val height: Int,
    val safe: AreaRect,
    val dxgiFormat: Int,
    val adapterLuid: Long,
    val frameCounter: Long,
    val streamFps: Int,
    val producerPid: Int,
    val textureName: String,
    /** Whose screen a game's car display shows: 0 CarPlay's (the texture), 1 the car's own; 0 at each open. */
    val screen: Int,
) {
    companion object {
        private const val MAGIC = 0
        private const val VERSION = 4
        private const val STATE = 12
        private const val GENERATION = 16
        private const val WIDTH = 20
        private const val HEIGHT = 24
        private const val SAFE = 28
        private const val DXGI_FORMAT = 44
        private const val LUID_LOW = 48
        private const val LUID_HIGH = 52
        private const val FRAME_COUNTER = 56
        private const val STREAM_FPS = 64
        private const val PRODUCER_PID = 68
        private const val TEXTURE_NAME = 72
        private const val TEXTURE_NAME_MAX = 64
        private const val SCREEN = 136

        /** Null when the magic or the version differ. */
        fun parse(bytes: ByteBuffer): BridgeStatus? {
            val b = BridgeLayout.view(bytes)
            if (b.getInt(MAGIC) != BridgeLayout.STATUS_MAGIC) return null
            if (b.getShort(VERSION).toInt() and 0xFFFF != BridgeLayout.VERSION) return null
            return BridgeStatus(
                state = b.getInt(STATE),
                generation = b.getInt(GENERATION),
                width = b.getInt(WIDTH),
                height = b.getInt(HEIGHT),
                safe = AreaRect(b.getInt(SAFE), b.getInt(SAFE + 4), b.getInt(SAFE + 8), b.getInt(SAFE + 12)),
                dxgiFormat = b.getInt(DXGI_FORMAT),
                adapterLuid = (b.getInt(LUID_HIGH).toLong() shl 32) or (b.getInt(LUID_LOW).toLong() and 0xFFFFFFFFL),
                frameCounter = b.getLong(FRAME_COUNTER),
                streamFps = b.getInt(STREAM_FPS),
                producerPid = b.getInt(PRODUCER_PID),
                textureName = BridgeLayout.readText(b, TEXTURE_NAME, TEXTURE_NAME_MAX),
                screen = b.getInt(SCREEN),
            )
        }
    }
}
