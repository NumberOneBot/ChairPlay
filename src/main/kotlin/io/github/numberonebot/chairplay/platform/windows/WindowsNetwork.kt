package io.github.numberonebot.chairplay.platform.windows

import io.github.numberonebot.chairplay.network.EthernetAdapter
import io.github.numberonebot.chairplay.network.WlanConnection
import io.github.numberonebot.chairplay.platform.PcNetwork
import java.net.InetAddress

/** The PC's networks as Windows reports them: the Native Wifi API and the IP Helper API. */
internal class WindowsNetwork : PcNetwork {
    override fun wifiConnections(): List<WlanConnection> = WlanApi.currentConnections()

    override fun ethernetAdapters(): List<EthernetAdapter> = EthernetAdapters.current()

    override fun physicalAddresses(): List<InetAddress> = physicalInterfaceAddresses()
}
