# ChairPlay 1.0.3

Status: prepared for release; not yet published.

This update adds automatic Ethernet setup, updates the CarPlay core, and improves connection setup and volume feedback.

## What's changed

- **Connect over Ethernet.** Connect the PC by cable and the iPhone by Wi-Fi to the same router, then launch
  ChairPlay normally. No command script or manual IP entry is needed. One available Ethernet adapter is selected
  automatically; with several adapters, choose one in Get started or Settings > Connection. The selection follows
  the adapter when its IP address changes. An active PC Wi-Fi connection still takes priority.
- **Clearer connection setup.** Adapter selection appears only when needed. The Wi-Fi network section explains
  that its details are sent to the iPhone during Bluetooth pairing. Network and Security stay on the same row,
  and Starting, Waiting, and Connecting statuses use ellipses.
- **Volume feedback.** Changing volume or muting briefly shows the current level in the Controls window title,
  or the main window title when Controls is hidden.
- **Updated CarPlay core.** DiPlay 0.2.22 adds bounds for network control input and binary plist decoding, limits
  secondary connections and timing replies to the connected iPhone, avoids unused protocol trace dumps, and
  uses smaller PCM microphone packets when needed to fit the network MTU.
- **Support the project.** About now includes a Buy Me a Coffee button beside the author's name.

## Install or update

Once published, download **ChairPlay-1.0.3-setup.exe** from the
[Releases page](https://github.com/NumberOneBot/ChairPlay/releases) and run it. Installation does not require
administrator rights. Updates preserve settings, iPhone pairing, and MFi files.

Bluetooth is still required for CarPlay setup, including when the PC uses Ethernet. Keep the router's Wi-Fi
name, password, and security type configured for the iPhone; connecting without these parameters is not supported
by this release. See [Ethernet setup](ETHERNET.md) for adapter selection and connection details.

Keyboard text input retains the limitations documented in 1.0.2.

For problems, use **About > Report a problem** to include the app version in a new issue.

[Full changelog](https://github.com/NumberOneBot/ChairPlay/blob/main/CHANGELOG.md) ·
[Changes since v1.0.2](https://github.com/NumberOneBot/ChairPlay/compare/v1.0.2...v1.0.3)
