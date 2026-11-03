package io.github.numberonebot.chairplay.media.apac

import java.io.IOException
import kotlin.math.floor
import kotlin.math.pow

internal data class SpatialSetup(val index: Int, val soundCheckTargets: Map<Int, Float>)

/** Validate the complete APAP offer before the core retires a working buffered owner. */
internal fun spatialSetup(stream: Map<String, Any?>): SpatialSetup {
    fun invalid(): Nothing = throw IOException("Unsupported spatial audio SETUP")
    val rawIndex = apapInteger(stream["audioFormatIndex"])
    if (rawIndex != 23L && rawIndex != 65L) invalid()
    val index = rawIndex.toInt()
    if (index !in listOf(23, 65) || stream["packetFormatAPAP"] != true ||
        (stream["shk"] as? ByteArray)?.size != 32 || apapInteger(stream["spf"]) != 1024L ||
        stream["audioType"] != "media" || stream["isMedia"]?.let { it != true } == true ||
        stream["audioMode"]?.let { it != "default" } == true ||
        apapInteger(stream["audioFormat"]) != if (index == 23) 0x800000L else 0L) invalid()
    if (stream["ct"]?.let { apapInteger(it) != if (index == 23) 4L else 0L } == true) invalid()
    val targets = (stream["soundCheckMediaKind"] as? Map<*, *>)?.entries?.associate { (key, value) ->
        val kind = key.toString().toIntOrNull() ?: invalid()
        val target = (value as? Number)?.toFloat()?.takeIf { it.isFinite() && it in -100f..0f } ?: invalid()
        kind to target
    }.orEmpty()
    return SpatialSetup(index, targets)
}

/** Pioneer MainLoudnessParameters contract; no inferred RMS normalization. */
internal fun spatialSoundCheck(target: Float?, values: List<Float>?): Double {
    if (target == null || values == null) return 1.0
    require(values.size == 3 && values.all { it.isFinite() } && target.isFinite())
    var desired = (target - values[0]).toDouble()
    if (desired > 0) desired = minOf(desired, maxOf(0.0, -1.0 - values[2]))
    return 10.0.pow(minOf(floor(desired + 0.5), 20.0) / 20.0)
}

internal fun spatialGain(samples: ShortArray, channels: Int, from: Float, to: Float) {
    val frames = samples.size / channels
    for (frame in 0 until frames) {
        val gain = from + (to - from) * frame / frames
        repeat(channels) { channel ->
            val at = frame * channels + channel
            samples[at] = (samples[at] * gain).toInt().coerceIn(-32768, 32767).toShort()
        }
    }
}
