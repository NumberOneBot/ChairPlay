# ChairPlay 1.0.1

Status: unreleased. These notes describe the changes prepared since 1.0.0.

This update refreshes the settings interface, shortens setup when MFi files are already available, and adds position-aware satellite reports for experimental GPS spoofing.

## What's changed

- **Refreshed settings and controls.** iOS-inspired switches, rounded controls, clearer selection highlights, and Reddit Sans. The GPS page puts **Use Windows location** above the coordinates.
- **A shorter Get started guide.** When both MFi files are present, their step is hidden entirely and the remaining steps are renumbered. With Windows Firewall off as well, the guide has two steps: pairing and Wi-Fi.
- **HUD font fallback.** Distances, calls, and the waiting message use Roboto Flex Wide SemiBold, with Inter Bold for characters it lacks. The rest of the HUD keeps Inter.
- **GPS satellite reports.** Turn on **Send satellites in view** and reconnect to try predicted satellites for the virtual car's position and current time. The app downloads an almanac in the background and caches it for offline use. If usable almanac data is unavailable, position and wheel-speed updates continue without satellite reports.
- **A longer top gear.** The default virtual car reaches about 268 km/h, and the speedometer now goes to 300 km/h.

Satellite reports remain experimental and off by default. Signal strengths are simulated, and the feature has not been shown to extend the distance over which the iPhone accepts a spoofed position. [Read about satellite reports](https://github.com/NumberOneBot/ChairPlay/blob/main/docs/GPS.md).

## Install or update

Once 1.0.1 is published, download **ChairPlay-1.0.1-setup.exe** from the [Releases page](https://github.com/NumberOneBot/ChairPlay/releases) and run it. No administrator rights are needed for installation. The update keeps your settings, pairing, and MFi files.

For first-time setup, pair your iPhone with Windows, open ChairPlay, and follow **Get started**. When the checks are complete, keep the iPhone unlocked and nearby, press **Connect**, and accept the CarPlay prompt on the phone.

You still need an iPhone with CarPlay, a Windows 10 or 11 PC with Bluetooth and Wi-Fi, and a shared WPA2 or open Wi-Fi network that allows the devices to reach each other. A working MFi identity is required: supply your own certificate and key. [Setup guide](https://github.com/NumberOneBot/ChairPlay/blob/main/docs/SETUP.md).

## Known limitations

- CarPlay still uses Wi-Fi; USB CarPlay and a hotspot shared by the PC are not supported.
- Close ChairPlay through its window. Force-closing an active session may require restarting the iPhone before reconnecting.
- Calls work best with headphones. Speaker echo cancellation remains experimental and off by default.

For problems, use **About → Report a problem** to include the app version in a new issue.

[Full changelog](https://github.com/NumberOneBot/ChairPlay/blob/main/CHANGELOG.md)
