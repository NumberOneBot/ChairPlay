package io.github.numberonebot.chairplay.input

import com.shilapi.xcertplay.airplay.AirPlayInfoPlist

/** Independent USB HID keyboard probe; character input modes still come from the touchpad device. */
internal object KeyboardHid {
    const val UUID = "2a2a2a2f"
    val enabled: Boolean = HandwritingHid.enabled && System.getenv("CHAIRPLAY_TEXT_TRANSPORT") == "keyboard"

    fun advertise(info: MutableMap<String, Any?>, experimental: Boolean = enabled) {
        if (!experimental) return
        val devices = info["hidDevices"] as? List<*> ?: return
        info["hidDevices"] = devices + mapOf(
            "uuid" to UUID, "name" to "ChairPlay keyboard probe", "displayUUID" to AirPlayInfoPlist.MAIN_UUID,
            "hidVendorID" to 2L, "hidProductID" to 2L, "hidCountryCode" to 33L, "hidDescriptor" to descriptor(),
        )
    }

    /**
     * A keyboard's report (modifiers, reserved byte, six Keyboard/Keypad usage selectors) in a Consumer Control
     * collection rather than Generic Desktop / Keyboard: iOS counts a declared keyboard as a hardware one and hides the
     * iPhone's own on-screen keyboard while CarPlay runs, but takes these keys in CarPlay's text fields either way.
     */
    internal fun descriptor(): ByteArray {
        val basic = intArrayOf(
        0x05, 0x0c, 0x09, 1, 0xa1, 1,
        0x05, 7, 0x19, 0xe0, 0x29, 0xe7, 0x15, 0, 0x25, 1, 0x75, 1, 0x95, 8, 0x81, 2,
        0x75, 8, 0x95, 1, 0x81, 1,
        0x19, 0, 0x29, 0x65, 0x15, 0, 0x25, 0x65, 0x75, 8, 0x95, 6, 0x81, 0, 0xc0,
    ).map(Int::toByte).toByteArray()
        return if (HostTextHid.enabled) HostTextHid.descriptor(basic) else basic
    }

    /** This probe maps printable ASCII to a US keyboard; it deliberately rejects other layouts. */
    internal fun report(codePoint: Int): ByteArray? {
        if (codePoint !in 32..126) return null
        val character = codePoint.toChar()
        val key = when (character) {
            in 'a'..'z' -> (4 + (character - 'a')) to false
            in 'A'..'Z' -> (4 + (character - 'A')) to true
            in '1'..'9' -> (0x1e + (character - '1')) to false
            '0' -> 0x27 to false
            ' ' -> 0x2c to false
            else -> punctuation.entries.firstOrNull { character in it.value }?.let {
                it.key to (it.value.indexOf(character) == 1)
            } ?: return null
        }
        return ByteArray(8).apply { this[0] = if (key.second) 2 else 0; this[2] = key.first.toByte() }
    }

    internal fun sendCharacter(report: ByteArray, write: (ByteArray) -> Boolean, hostText: Boolean = HostTextHid.enabled): Boolean {
        require(report.size == 8)
        fun send(packet: ByteArray) = write(if (hostText) byteArrayOf(1) + packet else packet)
        val modifiers = ByteArray(8).apply { this[0] = report[0] }
        val modified = report[0] != 0.toByte()
        if (modified && !send(modifiers)) { send(ByteArray(8)); return false }
        var sent = false
        try {
            sent = send(report)
        } finally {
            val keyReleased = send(modifiers)
            val modifiersReleased = if (modified) send(ByteArray(8)) else true
            sent = sent && keyReleased && modifiersReleased
        }
        return sent
    }

    private val punctuation = mapOf(
        0x1e to "1!", 0x1f to "2@", 0x20 to "3#", 0x21 to "4$", 0x22 to "5%",
        0x23 to "6^", 0x24 to "7&", 0x25 to "8*", 0x26 to "9(", 0x27 to "0)",
        0x2d to "-_", 0x2e to "=+", 0x2f to "[{", 0x30 to "]}", 0x31 to "\\|",
        0x33 to ";:", 0x34 to "'\"", 0x35 to "`~", 0x36 to ",<", 0x37 to ".>", 0x38 to "/?",
    )
}
