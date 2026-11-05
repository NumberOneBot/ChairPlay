package io.github.numberonebot.chairplay.media

import com.sun.jna.Function
import com.sun.jna.Memory
import com.sun.jna.Pointer
import com.sun.jna.platform.win32.Guid
import com.sun.jna.platform.win32.Ole32
import com.sun.jna.platform.win32.WTypes
import com.sun.jna.ptr.PointerByReference
import io.github.numberonebot.chairplay.windows.RELEASE
import io.github.numberonebot.chairplay.windows.comCall
import io.github.numberonebot.chairplay.windows.onComThread

/** The names of the devices Windows plays and records with by default, as Sound settings shows them; null for none. */
data class DefaultAudioDevices(val output: String?, val input: String?)

/**
 * Windows' default devices from its device enumerator (mmdeviceapi.h), the console role that Java Sound's "Primary
 * Sound Driver" follows. Read when the settings are asked for, so a default changed in Windows shows on the next look.
 */
object WindowsDefaultAudio {
    private val CLSID_ENUMERATOR = Guid.CLSID("{BCDE0395-E52F-467C-8E3D-C4579291692E}")
    private val IID_ENUMERATOR = Guid.IID("{A95664D2-9614-4F35-A746-DE8DB63617E6}")
    /** PKEY_Device_FriendlyName: "Speakers (Realtek(R) Audio)". */
    private val FRIENDLY_NAME = Guid.GUID("{A45C254E-DF1C-4EFD-8020-67D146A850E0}") to 14
    private const val RENDER = 0
    private const val CAPTURE = 1
    private const val CONSOLE = 0
    private const val GET_DEFAULT_AUDIO_ENDPOINT = 4
    private const val OPEN_PROPERTY_STORE = 4
    private const val GET_VALUE = 5
    private const val STGM_READ = 0
    private const val VT_LPWSTR = 31

    fun read(): DefaultAudioDevices =
        onComThread("default-audio", 5_000) { DefaultAudioDevices(name(RENDER), name(CAPTURE)) }
            ?: DefaultAudioDevices(null, null)

    private fun name(flow: Int): String? {
        val created = PointerByReference()
        val hr = Ole32.INSTANCE.CoCreateInstance(CLSID_ENUMERATOR, null, WTypes.CLSCTX_INPROC_SERVER, IID_ENUMERATOR, created)
        if (hr.toInt() != 0) return null
        val enumerator = created.value
        try {
            val device = PointerByReference()
            if (enumerator.comCall(GET_DEFAULT_AUDIO_ENDPOINT, flow, CONSOLE, device) != 0) return null
            try {
                return friendlyName(device.value)
            } finally {
                device.value.comCall(RELEASE)
            }
        } finally {
            enumerator.comCall(RELEASE)
        }
    }

    private fun friendlyName(device: Pointer): String? {
        val store = PointerByReference()
        if (device.comCall(OPEN_PROPERTY_STORE, STGM_READ, store) != 0) return null
        // PROPERTYKEY (a GUID as laid out in memory, and a number) and a PROPVARIANT (24 bytes on x64), zeroed.
        // The structure's own bytes: GUID.toByteArray() is in the string's order, which no property matches.
        val guid = FRIENDLY_NAME.first.apply { write() }.pointer.getByteArray(0, 16)
        val key = Memory(20).apply { write(0, guid, 0, 16); setInt(16, FRIENDLY_NAME.second) }
        val value = Memory(32).apply { clear() }
        try {
            if (store.value.comCall(GET_VALUE, key, value) != 0) return null
            return if (value.getShort(0).toInt() == VT_LPWSTR) value.getPointer(8)?.getWideString(0) else null
        } finally {
            Function.getFunction("ole32", "PropVariantClear").invokeInt(arrayOf(value))
            store.value.comCall(RELEASE)
        }
    }
}
