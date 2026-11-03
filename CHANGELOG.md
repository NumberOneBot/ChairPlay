# Changelog

New features and fixed bugs in ChairPlay.

## 1.0.5 - 2026-11-03

### Added

- Buffered music playback for supported iPhone apps, now selected by default, with stream state and buffered audio shown in seconds and KB. A saved Standard preference is preserved.
- Apple Spatial Audio through APAC 5.1 in Buffered mode, with its decoder included. Output automatically follows the Windows device's 5.1 or stereo configuration, and the buffer status shows 5.1 output or stereo downmix. If the decoder is unavailable, ChairPlay offers Buffered AAC only.
- Optional global keyboard shortcuts on Windows, using the existing bindings while another app has focus, including full screen. The focused app still receives the keys.
- Independent, saved Always on top switches for CarPlay and AltScreen, applied immediately from each screen's Window section.

### Fixed

- Held keyboard controls could remain pressed when a ChairPlay page lost focus or a modifier key was released before the main key.
- Inconsistent Appearance section headings on the CarPlay and AltScreen settings pages.
- Missing LIVI attribution and credit for LuKa's APAC work in the app and README.

[Release notes](docs/RELEASE-NOTES-1.0.5.md) · [Changes since v1.0.4](https://github.com/NumberOneBot/ChairPlay/compare/v1.0.4...v1.0.5)

## 1.0.4 - 2026-11-01

### Added

- H.265 (HEVC) video for CarPlay and AltScreen. Both H.264 and H.265 are offered by default, and the iPhone chooses the stream format. H.264-only mode is available in Settings > CarPlay.
- Hardware video decoding for both screens, enabled by default, with automatic software fallback. Software decoding can also be selected in Settings > CarPlay.
- The active video codec in each window title, after the resolution.

[Release notes](docs/RELEASE-NOTES-1.0.4.md)

## 1.0.3 - 2026-11-01

### Added

- Ethernet connection. A single adapter is selected automatically; multiple adapters can be selected in Get started and Settings > Connection.
- Brief volume and mute feedback in the Controls window title, or the main window title when Controls is hidden.

### Changed

- Update the CarPlay core to DiPlay 0.2.22: bound network control input and binary plist decoding, and restrict secondary connections and timing replies to the connected iPhone.
- Avoid building protocol trace dumps when trace logging is disabled.
- Use smaller PCM microphone packets when needed to fit the network MTU.

[Release notes](docs/RELEASE-NOTES-1.0.3.md) · [Changes since v1.0.2](https://github.com/NumberOneBot/ChairPlay/compare/v1.0.2...v1.0.3)

## 1.0.2 - 2026-10-27

### Added

- MFi files bundled with the installer; no manual certificate setup.
- **F** toggles full screen for CarPlay or AltScreen and restores the previous window state. Shortcut configurable.
- Basic keyboard text input in CarPlay, enabled by default; uses the iPhone's layout, with limited editing support.
- CarPlay presets: **1080×1080**, **2560×1080** (2.5K Wide), and **2560×1440** (2.5K).
- **GPS → Speed units** switches the speedometer and speed readings between **km/h** and **mph**, up to 180 mph.
- Compass arrow in the tachometer when the wheel follows steering input.

### Changed

- Wheel follows steering input by default; saved modes are preserved.
- CarPlay streams wider than 1280 pixels can shrink to **1280 screen pixels wide**, regardless of Windows scaling.

[Release notes](docs/RELEASE-NOTES-1.0.2.md) · [Changes since v1.0.1](https://github.com/NumberOneBot/ChairPlay/compare/v1.0.1...main)

## 1.0.1 - 2026-10-27

### Added

- Experimental **Send satellites in view** predicts GPS satellites from the virtual car's position and time.
- Background almanac refresh and offline cache; unavailable data does not interrupt position or speed updates.

### Changed

- Get started skips the MFi step when both files are present and restores it if either disappears.
- Longer sixth gear: about 268 km/h with the default car, a 280 km/h limiter, and a speedometer up to 300 km/h.

### Notes

- Satellite reports are off by default; reconnect after enabling. Signal levels are simulated; no proven range increase.

[Release notes](docs/RELEASE-NOTES-1.0.1.md) · [Changes since v1.0.0](https://github.com/NumberOneBot/ChairPlay/compare/v1.0.0...main)

## 1.0.0 — 2026-10-26

First public release:

- Wireless Apple CarPlay on Windows 10 and 11, with mouse and keyboard controls.
- Music, calls, and Siri through selectable PC audio devices.
- Get started checks for MFi files, firewall access, Bluetooth pairing, and Wi-Fi configuration.
- Experimental AltScreen with a map, turn card, and HUD with animated 3D maneuvers.
- Experimental GPS spoofing with a virtual car controlled by keyboard or gamepad.
- Per-user Windows installer that preserves settings, pairing, and MFi files on update.

[Published release](https://github.com/NumberOneBot/ChairPlay/releases/tag/v1.0.0)
