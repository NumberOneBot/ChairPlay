package io.github.numberonebot.chairplay.ui

// Parts of the state UiController sends the pages, built from the settings and the setup checks.

/** The saved Wi-Fi network: its name, security, and whether a password is set (never the password). */
internal fun wifiState(values: Map<String, String>): Map<String, Any?> = mapOf(
    "ssid" to values["wifi.ssid"].orEmpty(),
    "security" to (values["wifi.security"]?.trim()?.ifEmpty { null } ?: "WPA_WPA2"),
    "passphraseSet" to !values["wifi.passphrase"].isNullOrEmpty(),
)

/** What Get started and Connection show: the MFi folder, the firewall, the paired iPhone, the network. */
internal fun setupState(setup: SetupInfo, checks: SetupChecks?, values: Map<String, String>): Map<String, Any?> = linkedMapOf(
    "mfiFolder" to setup.mfiDirectory.toString(),
    "mfiPresent" to (checks?.snapshot?.mfiPresent ?: false),
    "firewall" to (checks?.snapshot?.firewall ?: setup.firewall).name.lowercase(),
    "pairedIphone" to checks?.snapshot?.pairedIphone,
    "pcWifi" to checks?.snapshot?.pcWifi,
    *networkSetupFields(checks?.snapshot, values["network.ethernet.adapter"].orEmpty()),
    "firewallCommands" to setup.firewallCommands,
    "settingsFile" to setup.settingsFile.toString(),
)
