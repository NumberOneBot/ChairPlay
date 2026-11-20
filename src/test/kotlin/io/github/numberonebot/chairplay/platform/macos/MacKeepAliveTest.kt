package io.github.numberonebot.chairplay.platform.macos

import jdk.net.ExtendedSocketOptions
import org.junit.Assert.assertEquals
import org.junit.Test
import java.net.InetAddress
import java.net.ServerSocket
import java.net.Socket

class MacKeepAliveTest {
    @Test
    fun `tune sets ExtendedSocketOptions on connected loopback socket without throwing`() {
        ServerSocket(0, 1, InetAddress.getLoopbackAddress()).use { ss ->
            Socket(InetAddress.getLoopbackAddress(), ss.localPort).use { client ->
                ss.accept().use { server ->
                    MacKeepAlive().tune(client, 10, 3, 3, 20)

                    assertEquals(10, client.getOption(ExtendedSocketOptions.TCP_KEEPIDLE))
                    assertEquals(3, client.getOption(ExtendedSocketOptions.TCP_KEEPINTERVAL))
                    assertEquals(3, client.getOption(ExtendedSocketOptions.TCP_KEEPCOUNT))
                }
            }
        }
    }
}
