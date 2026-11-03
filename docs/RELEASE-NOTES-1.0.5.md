# ChairPlay 1.0.5

This update adds buffered music playback, global keyboard shortcuts, and independent Always on top controls for CarPlay and AltScreen.

## What's changed

- **Buffered music by default.** Supported iPhone apps can send music ahead to cover Wi-Fi gaps. **Settings > Audio > Playback mode** offers Buffered first, followed by Standard. The Buffer row shows playback state, seconds, and KB. An existing Standard preference is kept; reconnect after changing the mode.
- **Apple Spatial Audio.** The included APAC decoder lets Buffered automatically offer both AAC and APAC 5.1. The selected Windows device determines whether ChairPlay outputs 5.1 or mixes it down to stereo. The Buffer row adds **5.1** or **5.1 → stereo** only while APAC plays. If the decoder is unavailable, ChairPlay offers Buffered AAC only.
- **Global shortcuts.** Enable **Settings > Keyboard > Global shortcuts** to use your existing bindings while another app has focus, including full screen. The other app also receives the keys. The switch applies immediately, is off by default, and does not enable background text input. Global shortcuts pause while a ChairPlay settings or Controls page has focus.
- **Always on top.** CarPlay and AltScreen each have their own switch in **Settings > CarPlay / AltScreen > Window**. Changes apply immediately and are remembered between launches; both switches are off by default.
- **Keyboard release fixes.** Held controls are released when a ChairPlay page loses focus, and shortcut releases remain correct when modifier keys are released first.
- **Consistent settings sections.** Both screen pages use **Appearance** and place **Window** directly after **Display**.
- **Corrected project credits.** The app and README now credit LIVI as a foundation for xcertplay and DiPlay, and LuKa's mib2q-carplay-rgi project for both maneuvers and APAC work.

## Install or update

Download **ChairPlay-1.0.5-setup.exe** from the [Releases page](https://github.com/NumberOneBot/ChairPlay/releases) and run it. Installation does not require administrator rights. Updates preserve settings, iPhone pairing, and MFi files.

For problems, use **About > Report a problem** to include the app version in a new issue.

[Full changelog](https://github.com/NumberOneBot/ChairPlay/blob/v1.0.5/CHANGELOG.md) · [Changes since v1.0.4](https://github.com/NumberOneBot/ChairPlay/compare/v1.0.4...v1.0.5)
