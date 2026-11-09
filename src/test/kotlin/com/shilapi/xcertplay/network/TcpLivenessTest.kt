package com.shilapi.xcertplay.network

import io.github.numberonebot.chairplay.platform.windows.WinsockKeepAlive
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.net.InetAddress
import java.net.ServerSocket
import java.net.Socket

class TcpLivenessTest {
    @Test
    fun acceptedSocketGetsUpstreamKeepAliveTiming() {
        val loopback = InetAddress.getLoopbackAddress()
        ServerSocket(0, 1, loopback).use { server ->
            Socket(loopback, server.localPort).use {
                server.accept().use { accepted ->
                    val diagnostics = mutableListOf<String>()

                    TcpLiveness.configure(accepted) { diagnostics += it }

                    assertEquals(diagnostics.toString(), 1, diagnostics.size)
                    assertTrue(diagnostics[0], diagnostics[0].startsWith("TCP peer health enabled"))
                    assertTrue(accepted.keepAlive)
                    // Idle 10 s, probes every 3 s, 3 probes, unacknowledged data abandoned after 20 s.
                    assertEquals(listOf(10, 3, 3, 20), WinsockKeepAlive().timing(accepted))
                }
            }
        }
    }
}
