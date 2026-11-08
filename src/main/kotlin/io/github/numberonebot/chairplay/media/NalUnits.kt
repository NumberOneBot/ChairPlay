package io.github.numberonebot.chairplay.media

import io.github.numberonebot.chairplay.phone.VideoCodecKind

/** What a decoder needs to know of the NAL units of an Annex B stream. */
object NalUnits {
    /**
     * Whether [annexB] holds a picture a decoder can start from: an IDR slice (H.264 type 5), or an IRAP picture (HEVC
     * types 16 to 21: BLA, IDR and CRA). It answers as DiPlay's MediaCodecSupport.isRandomAccess does, the reserved
     * IRAP types 22 and 23 included, which it takes for no keyframe.
     */
    fun isRandomAccess(annexB: ByteArray, codec: VideoCodecKind): Boolean {
        var cursor = 0
        while (cursor + 3 < annexB.size) {
            val prefix = when {
                annexB[cursor] != 0.toByte() || annexB[cursor + 1] != 0.toByte() -> 0
                annexB[cursor + 2] == 1.toByte() -> 3
                annexB[cursor + 2] == 0.toByte() && annexB[cursor + 3] == 1.toByte() -> 4
                else -> 0
            }
            if (prefix > 0 && cursor + prefix < annexB.size) {
                val header = annexB[cursor + prefix].toInt() and 255
                when (codec) {
                    VideoCodecKind.H264 -> if (header and 31 == H264_IDR) return true
                    // HEVC's NAL header has two bytes; the type is in the first.
                    VideoCodecKind.H265 -> if (cursor + prefix + 1 < annexB.size && (header shr 1) and 63 in HEVC_IRAP) return true
                }
                cursor += prefix
            } else cursor++
        }
        return false
    }

    private const val H264_IDR = 5
    private val HEVC_IRAP = 16..21
}
