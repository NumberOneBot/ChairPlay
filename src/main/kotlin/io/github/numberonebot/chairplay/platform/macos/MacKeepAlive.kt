package io.github.numberonebot.chairplay.platform.macos

import io.github.numberonebot.chairplay.platform.KeepAliveTuning
import jdk.net.ExtendedSocketOptions
import java.net.Socket

internal class MacKeepAlive : KeepAliveTuning {
    override fun tune(socket: Socket, idleSeconds: Int, intervalSeconds: Int, count: Int, deadlineSeconds: Int) {
        socket.setOption(ExtendedSocketOptions.TCP_KEEPIDLE, idleSeconds)
        socket.setOption(ExtendedSocketOptions.TCP_KEEPINTERVAL, intervalSeconds)
        socket.setOption(ExtendedSocketOptions.TCP_KEEPCOUNT, count)
    }
}
