# ChairPlay 1.0.10

A maintenance release: little changes on the outside, much on the inside. The code that calls Windows is now kept apart from the rest of ChairPlay, which makes the next large features and releases simpler to build.

## What's changed

- **Smoother settings.** Switching from CarPlay to the settings no longer flickers, and the settings show sooner.
- **Less background work.** ChairPlay reads the PC's network addresses and sound devices once as it starts, and the logs and screenshots folder sizes as **Experiments** opens. It no longer looks for a game's telemetry on every frame.
- **Change marks.**
  - A saved change that applies at the next connection is now marked in the toolbar too, not only next to its field.
  - Setting a value back clears its mark.

## Install or update

Download **ChairPlay-1.0.10-setup.exe** from the [Releases page](https://github.com/NumberOneBot/ChairPlay/releases) and run it. Installation does not require administrator rights. Updates preserve settings, iPhone pairing, and MFi files. For the portable build, download **ChairPlay-1.0.10-portable.zip**: close ChairPlay, delete the **app** and **runtime** folders, and unpack the new zip over the old one.

For problems, use **About > Report a problem** to include the app version in a new issue.

[Full changelog](https://github.com/NumberOneBot/ChairPlay/blob/v1.0.10/CHANGELOG.md) · [Changes since v1.0.9](https://github.com/NumberOneBot/ChairPlay/compare/v1.0.9...v1.0.10)
