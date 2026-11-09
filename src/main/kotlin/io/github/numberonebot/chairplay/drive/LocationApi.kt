package io.github.numberonebot.chairplay.drive

import com.sun.jna.Callback
import com.sun.jna.CallbackReference
import com.sun.jna.Memory
import com.sun.jna.Native
import com.sun.jna.Pointer
import com.sun.jna.platform.win32.Guid
import com.sun.jna.platform.win32.Ole32
import com.sun.jna.platform.win32.WTypes
import com.sun.jna.ptr.DoubleByReference
import com.sun.jna.ptr.IntByReference
import com.sun.jna.ptr.PointerByReference
import io.github.numberonebot.chairplay.platform.windows.RELEASE
import io.github.numberonebot.chairplay.platform.windows.comCall
import java.lang.ref.Reference

/**
 * Windows' Location API (locationapi.h) called through its interfaces' tables, as it has no automation object on
 * Windows 11. It is asked for latitude-and-longitude reports, with a listener registered so the location service runs,
 * and the latest report is read until one comes. Only on a thread with COM set up for free threading, as the class is.
 */
internal object LocationApi {
    const val REPORT_NOT_SUPPORTED = 0
    const val REPORT_ERROR = 1
    const val REPORT_ACCESS_DENIED = 2
    const val REPORT_INITIALIZING = 3
    const val REPORT_RUNNING = 4

    private val CLSID_LOCATION = Guid.CLSID("{E5B8E079-EE6D-4E33-A438-C87F2E959254}")
    private val IID_ILOCATION = Guid.IID("{AB2ECE69-56D9-4F28-B525-DE1B0EE44237}")
    private val IID_ILATLONGREPORT = Guid.IID("{7FED806D-0EF8-4F07-80AC-36A0BEAE3134}")
    private val IID_ILOCATIONEVENTS = Guid.IID("{CAE02BBF-798B-4508-A207-35A7906DC73D}")
    private val IID_IUNKNOWN = Guid.IID("{00000000-0000-0000-C000-000000000046}")
    private const val LOCATION_DESIRED_ACCURACY_HIGH = 1
    private const val E_NOINTERFACE = 0x80004002.toInt()

    // Table slots after IUnknown's three: ILocation, then ILocationReport and ILatLongReport.
    private const val REGISTER_FOR_REPORT = 3
    private const val UNREGISTER_FOR_REPORT = 4
    private const val GET_REPORT = 5
    private const val GET_REPORT_STATUS = 6
    private const val SET_DESIRED_ACCURACY = 10
    private const val GET_LATITUDE = 6
    private const val GET_LONGITUDE = 7
    private const val QUERY_INTERFACE = 0

    /** The last report status, and the place once a report came; polls for up to [timeoutMillis]. */
    fun poll(timeoutMillis: Long): Pair<Int, Pair<Double, Double>?> {
        val created = PointerByReference()
        val hr = Ole32.INSTANCE.CoCreateInstance(CLSID_LOCATION, null, WTypes.CLSCTX_INPROC_SERVER, IID_ILOCATION, created)
        if (hr.toInt() != 0) return REPORT_NOT_SUPPORTED to null
        val location = created.value
        val listener = Listener()
        try {
            location.comCall(SET_DESIRED_ACCURACY, IID_ILATLONGREPORT, LOCATION_DESIRED_ACCURACY_HIGH)
            val registered = location.comCall(REGISTER_FOR_REPORT, listener.pointer, IID_ILATLONGREPORT, 0) == 0
            try {
                val deadline = System.nanoTime() + timeoutMillis * 1_000_000
                var status = REPORT_INITIALIZING
                while (System.nanoTime() < deadline) {
                    val reported = IntByReference()
                    if (location.comCall(GET_REPORT_STATUS, IID_ILATLONGREPORT, reported) == 0) status = reported.value
                    if (status == REPORT_ACCESS_DENIED) return status to null
                    if (status == REPORT_RUNNING) latLong(location)?.let { return status to it }
                    Thread.sleep(200)
                }
                return status to null
            } finally {
                if (registered) location.comCall(UNREGISTER_FOR_REPORT, IID_ILATLONGREPORT)
            }
        } finally {
            location.comCall(RELEASE)
            // Windows may call the listener until it is unregistered and the location released.
            Reference.reachabilityFence(listener)
        }
    }

    private fun latLong(location: Pointer): Pair<Double, Double>? {
        val report = PointerByReference()
        if (location.comCall(GET_REPORT, IID_ILATLONGREPORT, report) != 0 || report.value == null) return null
        val latLong = PointerByReference()
        try {
            if (report.value.comCall(QUERY_INTERFACE, IID_ILATLONGREPORT, latLong) != 0 || latLong.value == null) return null
            val latitude = DoubleByReference()
            val longitude = DoubleByReference()
            val read = latLong.value.comCall(GET_LATITUDE, latitude) == 0 && latLong.value.comCall(GET_LONGITUDE, longitude) == 0
            return if (read) latitude.value to longitude.value else null
        } finally {
            latLong.value?.comCall(RELEASE)
            report.value.comCall(RELEASE)
        }
    }

    /**
     * An ILocationEvents that ignores what it hears: registering one is what makes Windows run the location service
     * for this caller. It lives in native memory with its own table, and is never counted: [poll] holds it throughout.
     */
    private class Listener {
        private fun interface QueryInterface : Callback { fun invoke(self: Pointer, iid: Pointer, out: Pointer): Int }
        private fun interface Count : Callback { fun invoke(self: Pointer): Int }
        private fun interface LocationChanged : Callback { fun invoke(self: Pointer, type: Pointer, report: Pointer?): Int }
        private fun interface StatusChanged : Callback { fun invoke(self: Pointer, type: Pointer, status: Int): Int }

        private val known = listOf(IID_IUNKNOWN, IID_ILOCATIONEVENTS).map { it.toGuidString() }
        private val callbacks = listOf<Callback>(
            QueryInterface { self, iid, out ->
                val ours = Guid.GUID(iid).toGuidString() in known
                out.setPointer(0, if (ours) self else null)
                if (ours) 0 else E_NOINTERFACE
            },
            Count { 1 },
            Count { 1 },
            LocationChanged { _, _, _ -> 0 },
            StatusChanged { _, _, _ -> 0 },
        )
        private val table = Memory(Native.POINTER_SIZE.toLong() * callbacks.size).apply {
            callbacks.forEachIndexed { slot, it -> setPointer(slot.toLong() * Native.POINTER_SIZE, CallbackReference.getFunctionPointer(it)) }
        }
        val pointer = Memory(Native.POINTER_SIZE.toLong()).apply { setPointer(0, table) }
    }
}
