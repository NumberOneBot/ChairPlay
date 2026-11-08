package io.github.numberonebot.chairplay.media.apac

import com.shilapi.xcertplay.airplay.BufferedAudioStream
import java.math.BigInteger

internal fun spatialOffer(info: MutableMap<String, Any?>) {
    val formats = info["audioFormats"] as? List<*> ?: return
    info["audioFormats"] = formats.map { entry ->
        if ((entry as? Map<*, *>)?.get("type") == 103) linkedMapOf(
            "type" to 103, "audioType" to "media", "audioOutputFormats" to 0x400000L,
            "audioOutputFormatsExtended" to listOf(65, 23)) else entry
    }
}

internal fun spatialAnchor(anchor: Map<String, Any?>?): Map<String, Any?>? = anchor?.plus(mapOf(
    "mediaTimeValue" to anchor["rtpTime"], "mediaTimeScale" to 48000, "status" to 0))

internal fun spatialControl(stream: BufferedAudioStream, method: String, body: Map<String, Any?>,
    now: () -> BigInteger): Map<String, Any?>? = when (method) {
    "SETMAGICCOOKIE" -> { stream.apap!!.remember(body); null }
    "GETANCHOR" -> spatialAnchor(stream.anchor()) ?: mapOf("status" to -6718)
    "SETRATE", "SETRATEANCHORTIME" -> {
        val rate = (body["rate"] as? Number)?.toDouble()
        if (rate != 0.0 && rate != 1.0) throw java.io.IOException("Unsupported spatial playback rate")
        val reply = spatialAnchor(stream.setRate(apapControlTime(body), rate.toInt(), now()))
        if (method == "SETRATEANCHORTIME" && reply != null) reply + mapOf(
            "firstAvailableRTPTime" to reply["rtpTime"], "firstAvailableMediaTimeValue" to reply["mediaTimeValue"],
            "firstAvailableMediaTimeScale" to 48000, "renderStartNetworkTimeSecs" to reply["networkTimeSecs"],
            "renderStartNetworkTimeFrac" to reply["networkTimeFrac"]) else reply
    }
    "FLUSHBUFFERED" -> { stream.flushSpatial(ApapFlushRange.parse(body)); null }
    else -> null
}

/** Inclusive sequence ranges; media-time prefix excludes an AU starting at the until boundary. */
internal data class ApapFlushRange(val fromSequence: Int?, val untilSequence: Int?, val fromTime: Long?, val untilTime: Long?) {
    val prefix get() = fromSequence == null && fromTime == null
    fun contains(sequence: Int, time: Long): Boolean {
        if (untilSequence != null) {
            val distance = (sequence - untilSequence) and 0xffffff
            if (distance != 0 && distance < 0x800000) return false
            if (fromSequence != null && ((sequence - fromSequence) and 0xffffff) >= 0x800000) return false
            return true
        }
        if (untilTime == null) return true
        if (!BufferedAudioStream.before(time, untilTime)) return false
        return fromTime == null || !BufferedAudioStream.before(time, fromTime)
    }
    companion object {
        fun parse(body: Map<String, Any?>): ApapFlushRange {
            fun sequence(name: String): Int? = body[name]?.let {
                val value = apapInteger(it)
                if (value !in 0..0xffffffL) throw java.io.IOException("Invalid APAP flush sequence")
                value.toInt()
            }
            return ApapFlushRange(sequence("flushFromSeq"), sequence("flushUntilSeq"),
                apapControlTime(body, "flushFrom"), apapControlTime(body, "flushUntil"))
        }
    }
}
