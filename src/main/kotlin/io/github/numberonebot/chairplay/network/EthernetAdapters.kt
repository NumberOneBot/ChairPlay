package io.github.numberonebot.chairplay.network

import java.net.Inet4Address
import java.net.NetworkInterface
import java.util.Base64
import java.util.concurrent.TimeUnit

/** Stable Windows interface GUID; addresses are refreshed, never stored as the selection. */
data class EthernetAdapter(val id: String, val label: String, val interfaceName: String, val addresses: List<String>)

fun selectEthernet(adapters: List<EthernetAdapter>, selected: String): EthernetAdapter? =
    if (selected.isNotBlank()) adapters.singleOrNull { it.id == selected }
    else adapters.singleOrNull()

/** Windows owns the physical/virtual distinction. Do not guess from localized adapter names. */
object EthernetAdapters {
    fun current(): List<EthernetAdapter> {
        val script = """
            ${'$'}ErrorActionPreference = 'Stop'
            Get-NetAdapter -Physical | Where-Object { ${'$'}_.InterfaceType -eq 6 -and ${'$'}_.Status -eq 'Up' } | ForEach-Object {
                ${'$'}line = "{0}`t{1}`t{2}" -f ${'$'}_.InterfaceGuid, ${'$'}_.ifIndex, ${'$'}_.Name
                [Convert]::ToBase64String([Text.Encoding]::UTF8.GetBytes(${'$'}line))
            }
        """.trimIndent()
        val encoded = Base64.getEncoder().encodeToString(script.toByteArray(Charsets.UTF_16LE))
        val process = ProcessBuilder("powershell.exe", "-NoProfile", "-NonInteractive", "-EncodedCommand", encoded)
            .redirectError(ProcessBuilder.Redirect.DISCARD).start()
        process.outputStream.close()
        try {
            check(process.waitFor(8, TimeUnit.SECONDS)) { "Ethernet adapter lookup timed out" }
            check(process.exitValue() == 0) { "Could not read Ethernet adapters" }
            return process.inputStream.bufferedReader().readLines().filter { it.isNotBlank() }.mapNotNull { line ->
                val parts = String(Base64.getDecoder().decode(line.trim()), Charsets.UTF_8).split('\t', limit = 3)
                if (parts.size != 3) return@mapNotNull null
                val nic = NetworkInterface.getByIndex(parts[1].toInt()) ?: return@mapNotNull null
                val addresses = nic.inetAddresses.toList().filterIsInstance<Inet4Address>()
                    .filterNot { it.isLoopbackAddress || it.isLinkLocalAddress || it.isAnyLocalAddress || it.isMulticastAddress }
                    .map { it.hostAddress }.sorted()
                if (!nic.isUp || addresses.isEmpty()) null
                else EthernetAdapter(parts[0].lowercase(), parts[2], nic.name, addresses)
            }.sortedBy { it.id }
        } finally {
            if (process.isAlive) process.destroyForcibly()
            process.inputStream.close()
            process.errorStream.close()
        }
    }
}
