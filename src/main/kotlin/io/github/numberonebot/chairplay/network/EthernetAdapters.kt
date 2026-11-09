package io.github.numberonebot.chairplay.network

/** Stable Windows interface GUID; addresses are refreshed, never stored as the selection. */
data class EthernetAdapter(val id: String, val label: String, val interfaceName: String, val addresses: List<String>)

fun selectEthernet(adapters: List<EthernetAdapter>, selected: String): EthernetAdapter? =
    if (selected.isNotBlank()) adapters.singleOrNull { it.id == selected }
    else adapters.singleOrNull()
