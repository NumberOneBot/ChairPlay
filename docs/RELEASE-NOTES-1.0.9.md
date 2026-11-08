# ChairPlay 1.0.9

This update opens the settings in a browser, shares the CarPlay picture and the GPS car with other apps, says when the iPhone stops answering, and fixes buffered music that stayed silent.

## What's changed

- **Settings in a browser.** Open http://localhost:7529 in any browser on this PC (7529 spells PLAY on a phone keypad). The settings work there and in ChairPlay's window at the same time, each following the other's changes. **Settings > Connection > Settings browser** changes the port and has **Open** for the default browser. Turn on **Allow access from the local network** and restart ChairPlay to open the settings from a phone or another PC at the address listed there.
- **Integrations for other apps.** On **Settings > Experiments > Integrations**, **CarPlay picture** publishes the CarPlay screen as a shared texture another app on this PC can show. **GPS car** lets another app drive the GPS car with its own position, heading, and speed; the gauges show its rpm, gear, and red line when it sends them. Both are for apps built to use them: [Integrations](INTEGRATIONS.md) describes the interface.
- **iPhone not responding.** When the iPhone's Wi-Fi drops, the connection takes about 20 seconds to give up, and the picture used to stand still while buffered music played on. The windows' titles now say **iPhone not responding** after 5 seconds, until it answers again or ChairPlay reconnects.
- **Buffered music.**
  - YouTube and other apps that send no audio cookie play in Buffered mode.
  - Music that was already playing as ChairPlay connected, or after a reconnect, no longer stays silent until stop and play.
  - **Settings > Audio** shows **Spatial** in the Buffer row while music plays as spatial audio.
- **Groundwork.** The connection, the screens, the controls, and the iPhone's data are restructured into shared parts, ahead of support for Android Auto phones in a later version.

## Install or update

Download **ChairPlay-1.0.9-setup.exe** from the [Releases page](https://github.com/NumberOneBot/ChairPlay/releases) and run it. Installation does not require administrator rights. Updates preserve settings, iPhone pairing, and MFi files. For the portable build, download **ChairPlay-1.0.9-portable.zip**: close ChairPlay, delete the **app** and **runtime** folders, and unpack the new zip over the old one.

For problems, use **About > Report a problem** to include the app version in a new issue.

[Full changelog](https://github.com/NumberOneBot/ChairPlay/blob/v1.0.9/CHANGELOG.md) · [Changes since v1.0.8](https://github.com/NumberOneBot/ChairPlay/compare/v1.0.8...v1.0.9)
