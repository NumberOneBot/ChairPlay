package io.github.numberonebot.chairplay.platform.windows

import com.sun.jna.Library
import com.sun.jna.Native
import com.sun.jna.ptr.IntByReference
import io.github.numberonebot.chairplay.platform.KeepAliveTuning
import java.io.FileDescriptor
import java.io.IOException
import java.net.Socket
import java.net.SocketImpl

/**
 * Windows' default keep-alive waits two hours before its first probe, and JDK 17 offers
 * TCP_KEEPIDLE/KEEPINTERVAL/KEEPCOUNT on Linux and macOS only (supportedOptions() on Windows lists
 * SO_KEEPALIVE alone). Winsock has had the same three options since Windows 10 1709, and TCP_MAXRT
 * stands in for Linux's TCP_USER_TIMEOUT, so they are set on the socket's handle directly. Reaching
 * the handle takes reflection into java.net and java.io, which the launchers open.
 */
internal class WinsockKeepAlive : KeepAliveTuning {
    private interface Winsock : Library {
        fun setsockopt(socket: Long, level: Int, name: Int, value: IntByReference, length: Int): Int
        fun getsockopt(socket: Long, level: Int, name: Int, value: IntByReference, length: IntByReference): Int
        fun WSAGetLastError(): Int
    }

    private val winsock by lazy { Native.load("Ws2_32", Winsock::class.java) }

    override fun tune(socket: Socket, idleSeconds: Int, intervalSeconds: Int, count: Int, deadlineSeconds: Int) {
        val handle = handle(socket)
        set(handle, TCP_KEEPIDLE, idleSeconds)
        set(handle, TCP_KEEPINTVL, intervalSeconds)
        set(handle, TCP_KEEPCNT, count)
        // Seconds, where Linux's TCP_USER_TIMEOUT takes milliseconds.
        set(handle, TCP_MAXRT, deadlineSeconds)
    }

    /** Idle, interval, count and deadline as Winsock reports them back; for tests. Throws on failure. */
    fun timing(socket: Socket): List<Int> {
        val handle = handle(socket)
        return listOf(TCP_KEEPIDLE, TCP_KEEPINTVL, TCP_KEEPCNT, TCP_MAXRT).map { name ->
            val value = IntByReference()
            if (winsock.getsockopt(handle, IPPROTO_TCP, name, value, IntByReference(4)) != 0) {
                throw IOException("getsockopt($name) failed: WSA error ${winsock.WSAGetLastError()}")
            }
            value.value
        }
    }

    private fun set(handle: Long, name: Int, value: Int) {
        if (winsock.setsockopt(handle, IPPROTO_TCP, name, IntByReference(value), 4) != 0) {
            throw IOException("setsockopt($name) failed: WSA error ${winsock.WSAGetLastError()}")
        }
    }

    /** A Windows socket's FileDescriptor keeps its SOCKET in `fd`, not `handle`. */
    private fun handle(socket: Socket): Long {
        val impl = Socket::class.java.getDeclaredField("impl").apply { isAccessible = true }.get(socket)
        val descriptor = SocketImpl::class.java.getDeclaredMethod("getFileDescriptor")
            .apply { isAccessible = true }.invoke(impl) as FileDescriptor
        val fd = FileDescriptor::class.java.getDeclaredField("fd").apply { isAccessible = true }.getInt(descriptor)
        if (fd < 0) throw IOException("socket is closed")
        return fd.toLong()
    }

    private companion object {
        // <ws2ipdef.h>
        const val IPPROTO_TCP = 6
        const val TCP_KEEPIDLE = 3
        const val TCP_MAXRT = 5
        const val TCP_KEEPCNT = 16
        const val TCP_KEEPINTVL = 17
    }
}
