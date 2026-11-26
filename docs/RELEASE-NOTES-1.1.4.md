# ChairPlay 1.1.4

This update lets ChairPlay minimize to the system tray, off the taskbar.

## What's changed

- **Minimize to the system tray.** Turn on **Minimize to the system tray** in the Windows group of the Application page, and ChairPlay keeps its icon in the system tray. Minimizing CarPlay's window, or starting ChairPlay minimized, takes it there with the AltScreen and the controls. A click on the icon shows them, and the next hides them again. The AltScreen minimized alone goes to the taskbar as usual.
- **Controls window without a taskbar button.** The controls window belongs to CarPlay's window: it has no taskbar button of its own, stays above CarPlay's window, and hides while it is minimized.
- **Car button without WebView2.** When the settings page opens in a browser because WebView2 is missing, the car button on CarPlay's home screen no longer minimizes ChairPlay's window.

## Install or update

Download **ChairPlay-1.1.4-setup.exe** from the [Releases page](https://github.com/NumberOneBot/ChairPlay/releases) and run it. Installation does not require administrator rights. Updates preserve settings, iPhone pairing, and MFi files. For the portable build, download **ChairPlay-1.1.4-portable.zip**: close ChairPlay, delete the **app**, **runtime**, and **helper** folders, and unpack the new zip over the old one.

For problems, use **About > Report a problem** to include the app version in a new issue.

[Full changelog](https://github.com/NumberOneBot/ChairPlay/blob/v1.1.4/CHANGELOG.md) · [Changes since v1.1.3](https://github.com/NumberOneBot/ChairPlay/compare/v1.1.3...v1.1.4)
