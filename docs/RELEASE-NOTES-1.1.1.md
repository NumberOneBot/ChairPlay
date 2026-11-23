# ChairPlay 1.1.1

This update fixes CarPlay not reconnecting while USB GPS runs, and asks you to open Apple Devices, with a button for it, when USB GPS can't reach the iPhone because Apple's USB service isn't running. It also halves the size of the USB GPS helper.

## What's changed

- **Fixed.** With USB GPS running, **Reconnect** or a lost connection left CarPlay calling the iPhone over Bluetooth forever: the iPhone refuses that call while USB GPS uses the cable. ChairPlay now pauses USB GPS until the iPhone answers, then resumes it.
- **USB GPS.** When Apple's USB service isn't running, for example after Windows restarts and before Apple Devices has been opened, **Settings > GPS** now says to open Apple Devices and connect the iPhone, and offers **Open Apple Devices**. Before, it asked for a USB cable even when the iPhone was connected. USB GPS resumes by itself once the service answers, and Apple Devices can be closed after that.
- **Smaller download.** The installer is 93 MB instead of 104 MB, and the portable zip 104 MB instead of 120 MB. The USB GPS helper in the `helper` folder leaves out packages it never runs and now takes 52 MB in 175 files instead of 98 MB in about 6,400.

## Install or update

Download **ChairPlay-1.1.1-setup.exe** from the [Releases page](https://github.com/NumberOneBot/ChairPlay/releases) and run it. Installation does not require administrator rights. Updates preserve settings, iPhone pairing, and MFi files. For the portable build, download **ChairPlay-1.1.1-portable.zip**: close ChairPlay, delete the **app**, **runtime**, and **helper** folders, and unpack the new zip over the old one.

For problems, use **About > Report a problem** to include the app version in a new issue.

[Full changelog](https://github.com/NumberOneBot/ChairPlay/blob/v1.1.1/CHANGELOG.md) · [Changes since v1.1.0](https://github.com/NumberOneBot/ChairPlay/compare/v1.1.0...v1.1.1)
