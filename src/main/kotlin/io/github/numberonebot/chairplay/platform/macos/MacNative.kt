package io.github.numberonebot.chairplay.platform.macos

import com.sun.jna.Library
import com.sun.jna.Native
import com.sun.jna.Structure

internal interface MacNative : Library {

    @Structure.FieldOrder("name", "address", "classOfDevice")
    class CpBtDevice : Structure() {
        @JvmField var name: ByteArray = ByteArray(256)
        @JvmField var address: ByteArray = ByteArray(32)
        @JvmField var classOfDevice: Int = 0
    }

    @Structure.FieldOrder("interfaceName", "ssid", "bssid", "channel", "powerOn", "serviceActive", "wifiHidden")
    class CpWifiInfo : Structure(), Structure.ByReference {
        @JvmField var interfaceName: ByteArray = ByteArray(64)
        @JvmField var ssid: ByteArray = ByteArray(128)
        @JvmField var bssid: ByteArray = ByteArray(32)
        @JvmField var channel: Int = 0
        @JvmField var powerOn: Int = 0
        @JvmField var serviceActive: Int = 0
        @JvmField var wifiHidden: Int = 0
    }

    fun cp_bt_radio_address(outBuf: ByteArray, bufLen: Int, errBuf: ByteArray, errLen: Int): Int
    fun cp_bt_paired_devices(outDevices: CpBtDevice, maxDevices: Int, errBuf: ByteArray, errLen: Int): Int
    fun cp_bt_connect_rfcomm(address: String, uuid16: ByteArray?, errBuf: ByteArray, errLen: Int): Long
    fun cp_bt_read(handle: Long, buf: ByteArray, len: Int, errBuf: ByteArray, errLen: Int): Int
    fun cp_bt_write(handle: Long, buf: ByteArray, len: Int, errBuf: ByteArray, errLen: Int): Int
    fun cp_bt_close(handle: Long)

    fun cp_net_wifi_info(outInfo: CpWifiInfo, errBuf: ByteArray, errLen: Int): Int

    fun cp_shell_apps_dark(): Int
    fun cp_shell_show_error(title: String, message: String)

    fun cp_win_paint_titlebar(nsWindow: Long, dialog: Int)
    fun cp_win_watch_close(nsWindow: Long): Long
    fun cp_win_user_closed(watch: Long): Int
    fun cp_win_unwatch_close(watch: Long)
    fun cp_win_drag_by_picture(nsWindow: Long)
    fun cp_win_flash(nsWindow: Long, on: Int)

    fun cp_audio_default_devices(outName: ByteArray, outLen: Int, inName: ByteArray, inLen: Int): Int

    companion object {
        val INSTANCE: MacNative by lazy {
            val path = io.github.numberonebot.chairplay.platform.NativeLibraries.find("chairplay_macos")?.absolutePath ?: "chairplay_macos"
            Native.load(path, MacNative::class.java)
        }
    }
}
