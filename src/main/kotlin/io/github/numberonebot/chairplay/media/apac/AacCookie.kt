package io.github.numberonebot.chairplay.media.apac

/** SETUP's audioFormat bit for AAC-LC, 48 kHz, stereo; DiPlay's AudioStreamCodec reads 0x800000 the same way. */
internal const val AAC_LC_48K_STEREO = 0x800000L

/**
 * The cookie for an APAP stream that gets none (YouTube's stream started with no cookie in its packets and no
 * SETMAGICCOOKIE before them), when its SETUP said plain AAC-LC. It is the ES_Descriptor a real buffered AAC cookie is: ES_Descriptor (tag 3) holding a
 * DecoderConfigDescriptor (tag 4: MPEG-4 audio 0x40, audio stream 0x15) whose DecoderSpecificInfo (tag 5) is the
 * AudioSpecificConfig 0x11 0x90 (AAC-LC, 48 kHz, 2 channels, 1024 frames), then the SLConfigDescriptor (tag 6).
 * Null for any other format: an APAC cookie is never made up.
 */
internal fun apapFallbackCookie(audioFormat: Long): ByteArray? = if (audioFormat != AAC_LC_48K_STEREO) null else
    byteArrayOf(3, 25, 0, 0, 0,
        4, 17, 0x40, 0x15, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0,
        5, 2, 0x11, 0x90.toByte(),
        6, 1, 2)
