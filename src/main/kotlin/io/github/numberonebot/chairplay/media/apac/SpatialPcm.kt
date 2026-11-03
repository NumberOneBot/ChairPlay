package io.github.numberonebot.chairplay.media.apac

import kotlin.math.roundToInt
import kotlin.math.sqrt

/** APAC MPEG_5_1_D (C,L,R,Ls,Rs,LFE) to Windows PCM; no upmix or binaural processing. */
internal fun spatialPcm(pcm: ShortArray, outputChannels: Int): ShortArray {
    require(pcm.size % 6 == 0 && outputChannels in listOf(2, 6))
    val out = ShortArray(pcm.size / 6 * outputChannels)
    val centreGain = 1.0 / sqrt(2.0)
    val headroom = 1.0 / (1.0 + sqrt(2.0))
    val order = intArrayOf(1, 2, 0, 5, 3, 4)
    for (frame in 0 until pcm.size / 6) {
        val source = frame * 6
        val destination = frame * outputChannels
        if (outputChannels == 6) {
            for (channel in order.indices) out[destination + channel] = pcm[source + order[channel]]
        } else {
            for (side in 0..1) {
                val mixed = headroom * (pcm[source + 1 + side] + centreGain *
                    (pcm[source].toInt() + pcm[source + 3 + side].toInt()))
                out[destination + side] = mixed.roundToInt().coerceIn(-32768, 32767).toShort()
            }
        }
    }
    return out
}
