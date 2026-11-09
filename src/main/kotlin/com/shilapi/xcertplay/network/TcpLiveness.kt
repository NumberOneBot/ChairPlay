package com.shilapi.xcertplay.network

import io.github.numberonebot.chairplay.platform.Platforms
import java.net.Socket

/**
 * Desktop stand-in for shared/.../network/TcpLiveness.kt: the same timing, so a vanished iPhone ends
 * the session in about 20 s, without treating a legitimately idle CarPlay screen as a failure.
 *
 * The platform sets the keep-alive options the JDK does not offer on every system. Where it cannot reach the socket,
 * the socket keeps plain keep-alive and the diagnostic says so.
 */
object TcpLiveness {
    fun configure(socket: Socket, diagnostic: (String) -> Unit) {
        socket.keepAlive = true
        try {
            Platforms.current.keepAlive.tune(socket, 10, 3, 3, 20)
            diagnostic("TCP peer health enabled idle=10s interval=3s count=3 deadline=20000ms; no video-idle timeout")
        } catch (failure: Exception) {
            diagnostic("TCP peer health tuning unavailable: ${failure.javaClass.simpleName}: ${failure.message}")
        }
    }

    /** Idle, interval, count and deadline as the system reports them back; for tests. */
    internal fun timing(socket: Socket): List<Int> = Platforms.current.keepAlive.timing(socket)
}
