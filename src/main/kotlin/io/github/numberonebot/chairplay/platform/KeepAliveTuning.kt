package io.github.numberonebot.chairplay.platform

import java.net.Socket

/** TCP keep-alive timing on a connected socket, which the JDK does not offer on every system. */
internal interface KeepAliveTuning {
    /**
     * Probes [socket] after [idleSeconds] of silence, every [intervalSeconds], and gives up after [count] misses or
     * [deadlineSeconds] without an answer. Throws when the system refuses or the socket cannot be reached.
     */
    fun tune(socket: Socket, idleSeconds: Int, intervalSeconds: Int, count: Int, deadlineSeconds: Int)

    /** Idle, interval, count and deadline as the system reports them back; for tests. Throws on failure. */
    fun timing(socket: Socket): List<Int>
}
