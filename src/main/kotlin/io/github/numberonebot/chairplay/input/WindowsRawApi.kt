package io.github.numberonebot.chairplay.input

import com.sun.jna.Memory
import com.sun.jna.Native
import com.sun.jna.Pointer
import com.sun.jna.platform.win32.WinDef.HWND
import com.sun.jna.ptr.IntByReference
import com.sun.jna.win32.StdCallLibrary
import com.sun.jna.win32.W32APIOptions

/** APIs absent from JNA's User32. UINT_PTR/HRAWINPUT use pointer-sized values on both Windows architectures. */
internal interface WindowsRawApi : StdCallLibrary {
    fun RegisterRawInputDevices(devices: Pointer, count: Int, size: Int): Boolean
    fun GetRegisteredRawInputDevices(devices: Pointer?, count: IntByReference, size: Int): Int
    fun GetRawInputData(input: Pointer, command: Int, data: Pointer?, size: IntByReference, header: Int): Int
    fun SetTimer(window: HWND, id: Pointer, millis: Int, callback: Pointer?): Pointer?
    fun KillTimer(window: HWND, id: Pointer): Boolean

    companion object {
        val instance: WindowsRawApi by lazy { Native.load("user32", WindowsRawApi::class.java, W32APIOptions.DEFAULT_OPTIONS) }
        val deviceSize = 8 + Native.POINTER_SIZE
        val headerSize = 8 + 2 * Native.POINTER_SIZE

        fun device(flags: Int, window: HWND?): Memory = Memory(deviceSize.toLong()).apply {
            clear(); setShort(0, 1); setShort(2, 6); setInt(4, flags); setPointer(8, window?.pointer)
        }

        fun keyboardTarget(): Pointer? {
            val count = IntByReference()
            check(instance.GetRegisteredRawInputDevices(null, count, deviceSize) != -1) { "Cannot inspect Raw Input registration" }
            if (count.value == 0) return null
            require(count.value in 1..4096)
            Memory(count.value.toLong() * deviceSize).use { data ->
                val read = instance.GetRegisteredRawInputDevices(data, count, deviceSize)
                check(read >= 0) { "Cannot read Raw Input registration" }
                for (i in 0 until read) {
                    val offset = i.toLong() * deviceSize
                    if (data.getShort(offset).toInt() == 1 && data.getShort(offset + 2).toInt() == 6) {
                        // A null target still denotes another registration; do not replace it.
                        return data.getPointer(offset + 8) ?: Pointer.createConstant(-1)
                    }
                }
            }
            return null
        }
    }
}

internal interface KeyboardSessionApi : StdCallLibrary {
    fun WTSRegisterSessionNotification(window: HWND, flags: Int): Boolean
    fun WTSUnRegisterSessionNotification(window: HWND): Boolean
    companion object {
        val instance: KeyboardSessionApi by lazy { Native.load("wtsapi32", KeyboardSessionApi::class.java) }
    }
}
