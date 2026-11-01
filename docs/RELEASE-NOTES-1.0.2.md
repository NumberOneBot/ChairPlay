# ChairPlay 1.0.2

Status: unreleased. These notes describe the changes prepared since 1.0.1.

This update adds keyboard text input, full screen, more CarPlay resolutions, and speed units, and simplifies setup.

## What's changed

- **Simpler setup.** The installer includes the MFi files, so you no longer need to add a certificate and key manually.
- **Keyboard text input.** Type in CarPlay fields using the iPhone's layout; letter shortcuts pause while typing. **Keyboard → Text input → Type in CarPlay** is on by default; reconnect after changing it. Shift, Caps Lock, Home/End, and text-selection shortcuts do not currently work in the tested CarPlay fields.
- **Full screen with F.** Toggle full screen independently for the focused CarPlay or AltScreen window; press F again to restore its previous size and position. The shortcut is configurable on the Keyboard page, and existing custom F bindings are preserved.
- **More resolutions.** Added **1080×1080**, **2560×1080** (2.5K Wide), and **2560×1440** (2.5K). Reconnect to apply.
- **Smaller windows.** CarPlay streams wider than 1280 pixels can fit a window **1280 screen pixels wide**, regardless of Windows display scaling. Aspect ratio locking keeps the picture's proportions.
- **km/h or mph.** **GPS → Speed units** immediately changes the scale, needle, central reading, and GPS speed display. The imperial scale goes to **180 mph**, with finer steps at lower speeds.
- **Steering input by default.** The wheel follows steering input, with a separate heading compass in the tachometer. Heading mode remains available, and existing saved wheel modes are preserved.

## Install or update

Once 1.0.2 is published, download **ChairPlay-1.0.2-setup.exe** from the [Releases page](https://github.com/NumberOneBot/ChairPlay/releases) and run it. No administrator rights are needed for installation. The update keeps your settings, pairing, and MFi files.

For first-time setup, pair your iPhone with Windows, open ChairPlay, and follow **Get started**. When the checks are complete, keep the iPhone unlocked and nearby, press **Connect**, and accept the CarPlay prompt on the phone.

For problems, use **About → Report a problem** to include the app version in a new issue.

[Full changelog](https://github.com/NumberOneBot/ChairPlay/blob/main/CHANGELOG.md) · [Changes since v1.0.1](https://github.com/NumberOneBot/ChairPlay/compare/v1.0.1...main)
