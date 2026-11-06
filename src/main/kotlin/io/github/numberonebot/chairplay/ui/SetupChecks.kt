package io.github.numberonebot.chairplay.ui

import java.io.Closeable
import java.nio.file.Files
import java.nio.file.Path
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import io.github.numberonebot.chairplay.network.EthernetAdapter
import io.github.numberonebot.chairplay.network.WifiHiddenException

/** The MFi files the iAP2 authentication signs with, which Get started asks for. */
private val MFI_FILES = listOf("identity.pk8", "certificate.p7b")

/** Whether both MFi files are in [dir]. */
internal fun mfiFilesPresent(dir: Path): Boolean = MFI_FILES.all { Files.isRegularFile(dir.resolve(it)) }

/**
 * What Get started and the Connection page check on the PC: the paired iPhone's name, the PC's network, the firewall,
 * and whether the MFi files are in their folder.
 */
data class SetupSnapshot(
    val pairedIphone: String?, val pcWifi: String?, val firewall: FirewallStatus, val mfiPresent: Boolean = false,
    val ethernet: List<EthernetAdapter> = emptyList(), val networkError: Boolean = false,
    /** The PC is on Wi-Fi, but Windows hides the network from ChairPlay ([WifiHiddenException]). */
    val wifiHidden: Boolean = false,
)

internal fun networkSetupFields(snapshot: SetupSnapshot?, selected: String): Array<Pair<String, Any?>> = arrayOf(
    "ethernet" to snapshot?.ethernet.orEmpty().map {
        mapOf("id" to it.id, "label" to it.label, "addresses" to it.addresses)
    },
    "ethernetSelected" to io.github.numberonebot.chairplay.network.selectEthernet(snapshot?.ethernet.orEmpty(), selected)?.id,
    "networkError" to (snapshot?.networkError ?: false),
    "wifiHidden" to (snapshot?.wifiHidden ?: false),
)

/**
 * The setup as it is now, read again every few seconds so the steps tick themselves as the PC changes: an iPhone
 * paired, the PC on a network, a firewall rule added, the MFi files put in place. Nothing is read while the page is not
 * shown ([active]). The firewall's rules take about a second to walk, so they are read again only while they are wrong (not
 * while the firewall is off), and at most every [FIREWALL_EVERY_SECONDS]. A reader that fails reads as nothing found.
 */
class SetupChecks(
    private val pairedIphone: () -> String?,
    private val pcWifi: () -> String?,
    private val firewall: () -> FirewallStatus,
    /** The firewall as the start read it. */
    startFirewall: FirewallStatus,
    private val active: () -> Boolean,
    private val now: () -> Long = System::nanoTime,
    private val mfiPresent: () -> Boolean = { false },
    private val ethernet: () -> List<EthernetAdapter> = { emptyList() },
    private val log: (String) -> Unit = {},
) {
    @Volatile
    var snapshot = SetupSnapshot(null, null, startFirewall)
        private set

    private var firewallRead = now()

    /** One round of reading; true when something changed. */
    @Synchronized
    fun check(): Boolean {
        if (!active()) return false
        val before = snapshot
        var firewallNow = before.firewall
        val time = now()
        val settled = firewallNow == FirewallStatus.PRESENT || firewallNow == FirewallStatus.OFF
        if (!settled && time - firewallRead >= TimeUnit.SECONDS.toNanos(FIREWALL_EVERY_SECONDS)) {
            firewallRead = time
            firewallNow = runCatching(firewall).getOrDefault(firewallNow)
        }
        val wifiRead = runCatching(pcWifi)
        val wifi = wifiRead.getOrNull()
        val hidden = wifiRead.exceptionOrNull() is WifiHiddenException
        if (hidden && !before.wifiHidden) log("PC Wi-Fi network hidden by Windows: location is off for desktop apps")
        val adapters = if (wifi == null) runCatching(ethernet) else Result.success(emptyList())
        val after = SetupSnapshot(
            runCatching(pairedIphone).getOrNull(), wifi, firewallNow,
            runCatching(mfiPresent).getOrDefault(false),
            adapters.getOrDefault(emptyList()), adapters.isFailure, hidden,
        )
        snapshot = after
        return after != before
    }

    /** The firewall as a read elsewhere found it, as the start's, made after the windows open; true when it changed. */
    @Synchronized
    fun firewallChecked(status: FirewallStatus): Boolean {
        firewallRead = now()
        val before = snapshot
        snapshot = before.copy(firewall = status)
        return snapshot != before
    }

    /** Reads the firewall now, as after Add the rule, whatever it was. */
    @Synchronized
    fun firewallNow() {
        firewallRead = now()
        snapshot = snapshot.copy(firewall = runCatching(firewall).getOrDefault(snapshot.firewall))
    }

    /** Reads every [EVERY_SECONDS] on a thread of its own, calling [onChange] after a round that changed something. */
    fun start(onChange: () -> Unit): Closeable {
        val executor = Executors.newSingleThreadScheduledExecutor { Thread(it, "setup-checks").apply { isDaemon = true } }
        executor.scheduleWithFixedDelay({ runCatching { if (check()) onChange() } }, 0, EVERY_SECONDS, TimeUnit.SECONDS)
        return Closeable { executor.shutdownNow() }
    }

    companion object {
        const val EVERY_SECONDS = 3L
        const val FIREWALL_EVERY_SECONDS = 10L
    }
}
