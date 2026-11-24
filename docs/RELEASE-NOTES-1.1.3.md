# ChairPlay 1.1.3

This update lets a game work CarPlay's controls and show CarPlay on its car's display as a real car does.

## What's changed

- **CarPlay controls from a game.** A game mod or another app on this PC can work CarPlay's knob, select, back, home, media, calls, and Siri, the AltScreen map's zoom, and ChairPlay's volume through the telemetry file, with nothing bound in ChairPlay. ChairPlay also tells it whether the game car's display shows CarPlay or its own screen, as a real car does: the armchair goes to the car's own screen, and Siri and calls bring CarPlay back while they last. A game can set CarPlay's display size for each car; ChairPlay reconnects to take it. In the game's menus the iPhone's music pauses, as in a car switched off. Turn on **Receive from other apps** under **Experiments > Integrations**; it no longer needs GPS spoofing. Details for developers are in [Integrations](https://github.com/NumberOneBot/ChairPlay/blob/v1.1.3/docs/INTEGRATIONS.md).

## Install or update

Download **ChairPlay-1.1.3-setup.exe** from the [Releases page](https://github.com/NumberOneBot/ChairPlay/releases) and run it. Installation does not require administrator rights. Updates preserve settings, iPhone pairing, and MFi files. For the portable build, download **ChairPlay-1.1.3-portable.zip**: close ChairPlay, delete the **app**, **runtime**, and **helper** folders, and unpack the new zip over the old one.

For problems, use **About > Report a problem** to include the app version in a new issue.

[Full changelog](https://github.com/NumberOneBot/ChairPlay/blob/v1.1.3/CHANGELOG.md) · [Changes since v1.1.2](https://github.com/NumberOneBot/ChairPlay/compare/v1.1.2...v1.1.3)
