package io.github.numberonebot.chairplay.platform.windows

// WAVEFORMATEXTENSIBLE speaker bits; a frame holds the mask's channels in bit order.
private const val FRONT = 0xf // left, right, centre, LFE
private const val BACKS = 0x30
private const val SIDES = 0x600

/**
 * Where each of 5.1's channels (L R C LFE Ls Rs) goes in a frame of an output with speaker [mask]: null when the mask
 * has no 5.1, that is the front four and a pair of sides or of backs. The surrounds take the sides when there are
 * both, as Windows and Dolby lay 5.1 into 7.1; the output's other channels stay silent.
 */
internal fun surroundSlots(mask: Int): IntArray? {
    if (mask and FRONT != FRONT) return null
    val pair = when {
        mask and SIDES == SIDES -> SIDES
        mask and BACKS == BACKS -> BACKS
        else -> return null
    }
    val left = pair and -pair
    val bits = listOf(0x1, 0x2, 0x4, 0x8, left, pair xor left)
    return IntArray(6) { Integer.bitCount(mask and (bits[it] - 1)) }
}

/** [samples], 6 channels a frame, spread to [channels] a frame by [slots], the other channels silent. */
internal fun spreadSurround(samples: ShortArray, slots: IntArray, channels: Int): ShortArray {
    val frames = samples.size / 6
    val out = ShortArray(frames * channels)
    for (frame in 0 until frames) for (c in 0 until 6) out[frame * channels + slots[c]] = samples[frame * 6 + c]
    return out
}
