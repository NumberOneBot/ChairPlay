# Changelog

New features and fixed bugs in ChairPlay.

## 1.1.0 RC1 - 2026-11-11

### Added

- **USB GPS** (**Settings > GPS > GPS method > USB**) sets the iPhone's location over a USB cable while CarPlay stays on Wi-Fi, with no 80 km limit around the iPhone's real place. It needs iOS 17.4 or later. A method chosen while CarPlay is connected applies at the next connection.
- **USB setup** gets an iPhone ready for USB GPS once, step by step, checking each step itself and going on when it is done:
  1. **Connect iPhone** asks for a data-capable USB cable and an unlocked iPhone. It checks for Apple's USB drivers: without them it opens **Apple Devices** in the Microsoft Store (iTunes works too); with Apple Devices installed but not running, it opens it.
  2. **Trust this computer** waits for **Trust** and the passcode on the iPhone.
  3. **Turn on Developer Mode**: on recent iOS versions **Show Developer Mode** makes it appear in **Settings > Privacy & Security** on the iPhone. The iPhone then restarts, and after it **Turn On** confirms it.
  4. **Install developer tools** downloads the developer disk image for the iPhone's iOS version from the public DeveloperDiskImage repository on GitHub, keeps it in the app folder's `cache\developer-tools`, and mounts it on the iPhone. A package already downloaded is reused.
  5. **Finish setup** starts USB GPS, or just ends the setup while the simulation is off.

  ChairPlay talks to the iPhone through Apple Mobile Device Service, which Apple Devices or iTunes installs, and a helper in the app's `helper` folder. Nothing else is installed on the PC.
- **City presets** put the USB GPS car on a busy road in one of ten cities.
- **Track playback** (**Settings > GPS**, with USB GPS) opens a GPX recording and plays it as the GPS car: Play, Pause, Stop, and the recording's time and distance. While it plays, the arrow keys and the gamepad don't move the car, and with **Drive mode** on the gauges show the recording's speed.
- **Settings > Application > Tint** gives the settings a shade: Slate, the new default, Black, as before, Graphite, or Teal. `ui.tint.controls=true` in settings.properties gives it to the controls window too.
- The mouse's back button goes back in the CarPlay and AltScreen windows.

### Changed

- **Application** takes the place of **Experiments** and holds the app's own settings: both windows' **Always on top** and **Bring to front on a call**, **Tint**, the settings browser, logs, integrations, and the HUD demo.
- A new install asks the iPhone for H.264 video. **Settings > CarPlay > Video codec** can still offer H.265, and settings from an earlier version keep their codec.
- Global shortcuts leave Enter and Escape to the app that has the focus.
- The GPS page no longer has the **Updates** row or the speed and heading switches. CarPlay GPS sends the position 4 times a second with the speed and heading. `gps.rate` and `gps.usb.rate` take any number from 1 to 20 as a hand edit, and `gps.wheel.speed` and `gps.heading.send` stay in settings.properties.
- Every gauge style shows the heading arrow, whatever the wheel shows.

[Release notes](docs/RELEASE-NOTES-1.1.0.md) · [Changes since v1.0.11](https://github.com/NumberOneBot/ChairPlay/compare/v1.0.11...v1.1.0-rc1)

## 1.0.11 - 2026-11-10

### Added

- **Settings > GPS > Gauge style** chooses the GPS car's dials: German, as before, Italian, or British ([compare them](docs/images/gauge-styles.png)).
- **Experiments > HUD demo > Gauges** runs the gauges up and down on the AltScreen in the style set, without an iPhone or the GPS.
- An app driving the GPS car can send the throttle, the brake, the steering wheel's angle, and the car's top speed. The gauges show the pedals and the wheel, and the speedometer's scale runs to the top speed. See [Integrations](docs/INTEGRATIONS.md).

### Changed

- An app's red line and top speed stay on the gauges after it stops sending, until the gamepad or keys drive the GPS car again.
- The GPS car parks and stops its engine after standing 5 seconds instead of 10.
- Setup and the uninstaller close a running ChairPlay themselves and end its CarPlay session as closing the window does.

### Removed

- **Send satellites in view**, with its almanac download and cache. The iPhone showed no difference with it.

[Release notes](docs/RELEASE-NOTES-1.0.11.md) · [Changes since v1.0.10](https://github.com/NumberOneBot/ChairPlay/compare/v1.0.10...v1.0.11)

## 1.0.10 - 2026-11-09

A maintenance release: little changes on the outside, much on the inside. The code that calls Windows is now kept apart from the rest of ChairPlay, which makes the next large features and releases simpler to build.

### Changed

- Switching from CarPlay to the settings no longer flickers, and the settings show sooner.
- ChairPlay does less work in the background. It reads the PC's network addresses and sound devices once as it starts, and the logs and screenshots folder sizes as **Experiments** opens. It no longer looks for a game's telemetry on every frame.

### Fixed

- A saved change that applies at the next connection was marked only next to its field. The toolbar now shows it too.
- A change's mark stayed after its value was set back.

[Release notes](docs/RELEASE-NOTES-1.0.10.md) · [Changes since v1.0.9](https://github.com/NumberOneBot/ChairPlay/compare/v1.0.9...v1.0.10)

## 1.0.9 - 2026-11-09

### Added

- The settings open in any browser on this PC at http://localhost:7529 (7529 spells PLAY on a phone keypad), alongside ChairPlay's window. **Settings > Connection > Settings browser** changes the port, opens the browser, and with **Allow access from the local network** opens the settings to a phone or another PC at the addresses it lists.
- **Experiments > Integrations**, for other apps on this PC: **CarPlay picture** publishes the CarPlay screen as a shared texture another app can show, and **GPS car** lets another app drive the GPS car with its position, heading, and speed, its rpm, gear, and red line shown on the gauges. The interface is described in [Integrations](docs/INTEGRATIONS.md).
- When the iPhone stops answering, both windows' titles say **iPhone not responding** after 5 seconds, until it answers again or ChairPlay reconnects. The picture used to stand still with no sign, while buffered music played on.
- **Settings > Audio** shows **Spatial** in the Buffer row while music plays as spatial audio.

### Changed

- The connection, the screens, the controls, and the iPhone's data are restructured into shared parts, ahead of support for Android Auto phones in a later version.

### Fixed

- No sound in Buffered mode from apps that send no audio cookie at all, such as YouTube on recent iOS.
- Music that was playing as ChairPlay connected stayed silent until stop and play.
- After a reconnect, music sometimes stayed silent until stop and play.

[Release notes](docs/RELEASE-NOTES-1.0.9.md) · [Changes since v1.0.8](https://github.com/NumberOneBot/ChairPlay/compare/v1.0.8...v1.0.9)

## 1.0.8 - 2026-11-06

### Added

- An incoming call flashes the CarPlay window's taskbar button while it rings. Turn on **CarPlay > Window > Bring to front on a call** to also show the window and bring it to the front.

### Changed

- The GPS car's speed is limited only by the end of the speedometer, 300 km/h or 180 mph, with a seventh gear. It used to stop near 268 km/h.
- Only one copy of ChairPlay runs at a time. A second copy started from another folder, installed or portable, says so and closes.

### Fixed

- When Windows hides the PC's Wi-Fi network from desktop apps (location off, on Windows 11 24H2 and later), ChairPlay said to connect a network cable. It now says to turn on location for desktop apps and opens the location settings.

[Release notes](docs/RELEASE-NOTES-1.0.8.md) · [Changes since v1.0.7](https://github.com/NumberOneBot/ChairPlay/compare/v1.0.7...v1.0.8)

## 1.0.7 - 2026-11-06

### Fixed

- GPS simulation turned on with no coordinates started at 0, 0 and sent the iPhone no position. It now starts where Windows locates the PC, and saves that place as the start. Coordinates already set are kept.

[Release notes](docs/RELEASE-NOTES-1.0.7.md) · [Changes since v1.0.6](https://github.com/NumberOneBot/ChairPlay/compare/v1.0.6...v1.0.7)

## 1.0.6 - 2026-11-05

### Added

- A portable build, ChairPlay-1.0.6-portable.zip: unpack it to any folder you can write to and run ChairPlay.exe. Its data stays in that folder.
- Update checks. At startup ChairPlay looks for a newer release and offers to open the Releases page. Turn it off, or check at any time, in About.
- The Wi-Fi step of Get started can be reopened after it's done, from the pencil next to the network name, to correct the network or password.
- A hint next to the Wi-Fi fields in Get started and Settings > Connection: use an existing network that both the PC and the iPhone can reach.
- Settings > Audio shows which device Windows default currently plays on and records from.

### Changed

- The iPhone keeps its own on-screen keyboard while CarPlay is connected. Typing in CarPlay from the PC keyboard works as before.
- ChairPlay no longer runs PowerShell. It reads the firewall, network adapters and location through Windows directly, and adds the firewall rule with one administrator prompt.
- The window opens sooner: the firewall is read after it opens, and spatial audio is checked at the connection.
- The firewall check counts ChairPlay's rule only for this copy of the app. Adding the rule also removes ChairPlay's rules left for a copy that was moved or removed.
- The WebView2 cache is kept in the app folder with the other data. The installer removes the old one.

### Fixed

- A black screen after reconnecting at 1920×1080, while touches still reached the iPhone.
- No sound in Buffered mode from YouTube and some other apps, with YouTube's video holding still on the iPhone.
- Replacing a shown Wi-Fi password: the field showed the old one again.
- Letters typed as symbols (÷, ©, ≠) after switching the Windows keyboard layout with Alt+Shift.
- ChairPlay could refuse to start over an unplugged audio device saved in the settings, which now gives way to the Windows default, or a damaged pairing file, now set aside. A settings file it can't read is now named in the error.

[Release notes](docs/RELEASE-NOTES-1.0.6.md) · [Changes since v1.0.5](https://github.com/NumberOneBot/ChairPlay/compare/v1.0.5...v1.0.6)

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
