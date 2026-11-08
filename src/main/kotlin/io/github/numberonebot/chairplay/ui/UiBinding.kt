package io.github.numberonebot.chairplay.ui

import com.sun.net.httpserver.HttpServer
import java.net.InetAddress
import java.net.InetSocketAddress
import java.net.SocketException

/**
 * The UI's HTTP server on [address] and [port] (ui.port), not started. When another program has the port, or Windows
 * keeps it for itself (an excluded port range refuses the bind without anyone listening), it takes a free one for this
 * run and [log] says which; a failure on a free port goes up.
 */
internal fun openHttpServer(port: Int, address: InetAddress, log: (String) -> Unit): HttpServer =
    try {
        HttpServer.create(InetSocketAddress(address, port), 0)
    } catch (error: SocketException) {
        // BindException is one; Windows' excluded ports give a plain SocketException.
        HttpServer.create(InetSocketAddress(address, 0), 0).also {
            log("UI port $port is in use; this run uses ${it.address.port}")
        }
    }
