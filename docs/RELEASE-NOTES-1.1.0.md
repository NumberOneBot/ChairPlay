# ChairPlay 1.1.0 RC1

This update sets the iPhone's location over a USB cable while CarPlay stays on Wi-Fi, with a step-by-step setup, city presets, and GPX track playback. The settings window gets a new look and a light theme.

This is a release candidate: it is meant for testing before 1.1.0. Report what does not work with **About > Report a problem**.

## What's changed

- **USB GPS.** **Settings > GPS > GPS method > USB** sets the iPhone's location over a USB cable while CarPlay stays on Wi-Fi, with no 80 km limit around the iPhone's real place. It needs iOS 17.4 or later.
- **USB setup** gets an iPhone ready for USB GPS once, checking each step itself:
  1. **Connect iPhone** asks for a data-capable USB cable and an unlocked iPhone, and opens **Apple Devices** in the Microsoft Store when Apple's USB drivers are missing.
  2. **Trust this computer** waits for **Trust** and the passcode on the iPhone.
  3. **Turn on Developer Mode** shows the option in **Settings > Privacy & Security** on the iPhone; the iPhone restarts, and **Turn On** confirms it.
  4. **Install developer tools** downloads the developer disk image for the iPhone's iOS version and mounts it. A package already downloaded is reused.
  5. **Finish setup** starts USB GPS.

  ChairPlay talks to the iPhone through Apple Mobile Device Service, which Apple Devices or iTunes installs. Nothing else is installed on the PC.
- **City presets** put the USB GPS car on a busy road in one of ten cities.
- **Track playback.** With USB GPS, **Settings > GPS** opens a GPX recording and plays it as the GPS car, with Play, Pause, Stop, and the recording's time and distance.
- **Theme.** **Settings > Application > Theme** gives the settings a light theme, White, or a dark one: Slate, the new default, Black, as before, Graphite, Teal, or Dracula.
- **New look.** The toolbar is translucent and the page scrolls under it, the list of pages floats beside the page, and a page shorter than the window sits in its middle.
- **Application** takes the place of **Experiments** and holds the app's own settings: the windows, **Theme**, the settings browser, logs, integrations, and the HUD demo.
- **Mouse.** The mouse's back button goes back in the CarPlay and AltScreen windows.
- **Media keys.** The keyboard's Previous track, Play/Pause, and Next track keys control the iPhone's music while a ChairPlay window has the focus, and other players don't get them meanwhile. The volume keys stay with Windows.
- **Updates.** **About > Version** offers a newer release that the update check found, with **Download**, until that version is installed. The update dialog's **Open** goes to the latest release's page.
- **Connecting.** **Settings > CarPlay** and **AltScreen** stay open while the iPhone connects; a change the connection has already read is marked to apply at the next one.
- **Video.** A new install asks the iPhone for H.264 video. **Settings > CarPlay > Video codec** can still offer H.265, and settings from an earlier version keep their codec.
- **Global shortcuts** leave Enter and Escape to the app that has the focus.
- **GPS page.** The **Updates** row and the speed and heading switches are gone: CarPlay GPS sends the position 4 times a second with the speed and heading. Every gauge style shows the heading arrow.
- **Fixed.** In full screen, choosing ChairPlay on CarPlay's home screen made the screen blink and left CarPlay showing. The window now leaves full screen and shows the settings; **F** takes it back to full screen.

## Install or update

Download **ChairPlay-1.1.0-rc1-setup.exe** from the [Releases page](https://github.com/NumberOneBot/ChairPlay/releases) and run it. Installation does not require administrator rights. Updates preserve settings, iPhone pairing, and MFi files. For the portable build, download **ChairPlay-1.1.0-rc1-portable.zip**: close ChairPlay, delete the **app**, **runtime**, and **helper** folders, and unpack the new zip over the old one.

For problems, use **About > Report a problem** to include the app version in a new issue.

[Full changelog](https://github.com/NumberOneBot/ChairPlay/blob/v1.1.0-rc1/CHANGELOG.md) · [Changes since v1.0.11](https://github.com/NumberOneBot/ChairPlay/compare/v1.0.11...v1.1.0-rc1)
