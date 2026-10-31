<p align="center">
  <img src="packaging/icon/chairplay-1024.png" alt="ChairPlay app icon" width="128" height="128">
</p>

<h1 align="center">ChairPlay</h1>

<p align="center"><strong>Wireless Apple CarPlay on your Windows PC.</strong></p>

<p align="center">
  <a href="https://github.com/NumberOneBot/ChairPlay/releases/latest"><img src="https://img.shields.io/github/v/release/NumberOneBot/ChairPlay?style=flat&amp;label=Latest%20release" alt="Latest release"></a>
  <a href="https://github.com/NumberOneBot/ChairPlay/releases"><img src="https://img.shields.io/github/downloads/NumberOneBot/ChairPlay/total?style=flat&amp;label=Downloads" alt="Total downloads"></a>
  <a href="#what-you-need"><img src="https://img.shields.io/badge/Windows-10%20%2F%2011%20%C2%B7%20x64-0078D4?style=flat" alt="Windows 10 / 11 · x64"></a>
  <a href="LICENSE"><img src="https://img.shields.io/badge/License-GPL--3.0-blue?style=flat" alt="License: GPL-3.0"></a>
</p>

<p align="center">
  <a href="https://github.com/NumberOneBot/ChairPlay/releases/latest"><img src="https://img.shields.io/badge/Download-0078D4?style=for-the-badge" alt="Download"></a>
</p>

Listen to music, use navigation, take calls and talk to Siri through
your PC's speakers and microphone. Control CarPlay with your mouse and keyboard.

![ChairPlay on Windows: the AltScreen instrument cluster over a map, the CarPlay home screen in the ChairPlay window, and the on-screen car controls](docs/images/chairplay.jpg)

Useful for building and testing CarPlay apps and navigation without a car, for demos, or just CarPlay on a desk.

## What you need

- **An iPhone** with CarPlay.
- **A Windows 10 or 11 PC (64-bit)** with Bluetooth and Wi-Fi. A USB Bluetooth adapter works if it supports classic
  Bluetooth, not only Bluetooth Low Energy.
- **One Wi-Fi network for the PC and the iPhone.** The PC must be on Wi-Fi, not only on a cable. The network must let
  devices see each other: most home networks do, guest networks usually don't. It must use WPA2 or no password;
  WPA3-only networks don't work yet.

## Get started

1. **Install.** Download the latest `ChairPlay-…-setup.exe` from the
   [Releases page](https://github.com/NumberOneBot/ChairPlay/releases) and run it. No administrator rights needed.
   If Windows SmartScreen warns about an unrecognized app, choose **More info → Run anyway**.
2. **Pair your iPhone** with the PC once, in Windows Settings → Bluetooth & devices, as you would headphones.
3. **Open ChairPlay.** The **Get started** screen checks the firewall, your paired iPhone, and your Wi-Fi network.
   The firewall step is omitted when Windows Firewall is off.
4. **Connect.** When the checks are complete, keep your iPhone unlocked and nearby and press **Connect**.
   Accept the CarPlay prompt on the iPhone. CarPlay comes up.

After that, open ChairPlay with your iPhone unlocked and nearby, and CarPlay starts by itself.

A newer installer updates ChairPlay and keeps your settings, pairing and MFi files. Uninstalling asks whether to
delete them too.

See the [changelog](CHANGELOG.md) for changes between versions and the
[1.0.2 release notes](docs/RELEASE-NOTES-1.0.2.md) for the upcoming update.

## Features

- CarPlay in a resizable window; the mouse works as touch.
- Full screen for CarPlay and the AltScreen: focus either window and press **F** to enter or leave full screen.
  Leaving full screen restores the window's previous size, position, and maximized state.
- Keyboard controls like a car's knob and buttons (see [Controls](#controls)), and on-screen car controls from the
  toolbar's Controls button.
- Basic keyboard text input in CarPlay fields, using the iPhone's keyboard layout.
- Sound and microphone on the devices you choose, with a volume separate from Windows'.
- **AltScreen** (experimental): a car's instrument cluster as a second CarPlay screen in its own window, with the
  map, the turn card or both, in a preset part of the cluster or a rectangle you draw. It can also show a **HUD**: the
  next turn with animated 3D maneuvers, the distance, the arrival time and the song.
- **GPS spoofing** (experimental): the iPhone navigates by a virtual car you drive with the keyboard or a gamepad.
  See the [GPS guide](docs/GPS.md) for details and optional satellite reports.

Settings are in the app and save as you change them. Tested on Windows 11 with an iPhone 15 (iOS 18.6).

## Controls

Click to touch, use **Arrow keys** to move the focus, **Enter** or **Space** to select, and **Backspace** or **Esc**
to go back. Press **Home** for the home screen, **F** for full screen, or **Ctrl+R** to reconnect.

<details>
<summary>All keyboard shortcuts</summary>

| Key                                       | Does                                                           |
| ----------------------------------------- | -------------------------------------------------------------- |
| Mouse                                     | touch                                                          |
| Arrows                                    | tilt the knob: move the focus                                  |
| Tab / Shift+Tab, mouse wheel              | turn the knob                                                  |
| Enter, Space                              | select                                                         |
| Backspace, Esc                            | back                                                           |
| Home                                      | home screen                                                    |
| Ctrl+R                                    | reconnect                                                      |
| S                                         | Siri                                                           |
| F3 / F4                                   | answer / end a call                                            |
| F7 / F8 / F9                              | previous track / play or pause / next track                    |
| F10                                       | drive the GPS car with the arrows (GPS spoofing on)            |
| Shift+F10                                 | large or small gauges for the GPS car                          |
| F11                                       | AltScreen: switch between the HUD and the map                  |
| Shift+F11                                 | AltScreen: move the arrival time and turn card                 |
| F12                                       | save screenshots of CarPlay and the AltScreen                  |
| F                                         | toggle full screen for the focused CarPlay or AltScreen window |
| Ctrl+Up / Ctrl+Down / Ctrl+M              | ChairPlay's volume up / down / mute                            |
| Mouse wheel over the AltScreen, + / −     | zoom the AltScreen map                                         |
| The car button on the CarPlay home screen | open the settings; the CarPlay button or Esc returns           |

</details>

<br/>

**Typing in CarPlay:** **Keyboard → Text input → Type in CarPlay** is on by default; reconnect after changing it.
When CarPlay requests text input, letter shortcuts pause so you can type. Text follows the iPhone's layout,
not the Windows layout. Shift, Caps Lock, Home/End, and text-selection shortcuts do not currently work in the
tested CarPlay fields.

## Support the project

ChairPlay is a hobby project I build and test on real hardware in my spare time. It’s free and fully open source
under GPL-3.0, and donations are the only way I earn anything from this work. If it saves you a trip to the car,
helps you build CarPlay apps, or makes your desk a little more fun, you can buy me a coffee.

<a href="https://www.buymeacoffee.com/alexstrelets"><img src="https://cdn.buymeacoffee.com/buttons/v2/default-yellow.png" alt="Buy Me A Coffee" width="217"></a>

## Good to know

- Close ChairPlay with its window. If it's killed mid-session, from Task Manager for example, the iPhone may ignore
  new connections until you restart the iPhone.
- It works over Wi-Fi only, not over USB or a hotspot shared by the PC.
- Calls sound best with headphones. Echo cancellation for speakers is experimental and off by default.
- Music comes over Wi-Fi as AAC; the iPhone doesn't send lossless audio this way.
- The iPhone's volume buttons don't change CarPlay's volume. Use ChairPlay's volume or Windows'.

## Report a problem

Open an [issue](https://github.com/NumberOneBot/ChairPlay/issues/new): About → **Report a problem** fills in the
version for you. For a connection problem, set **Log level** to **Info** on the Experiments page, try again, and
attach the log: the **Logs** button on that page opens its folder. ChairPlay keeps your Wi-Fi password out of the log,
but look it over before sending.

Ideas and fixes are welcome too: open an issue or a pull request, or write to me at
[alex.strelets@gmail.com](mailto:alex.strelets@gmail.com).

## Build it yourself

You need JDK 17; the build downloads the rest.

```
git clone --recursive https://github.com/NumberOneBot/ChairPlay.git
cd ChairPlay
gradlew deploy
```

This builds the app into `dist\ChairPlay`. [docs/SETUP.md](docs/SETUP.md) covers the build, every setting, moving to
a newer DiPlay, and the git hooks that keep keys out of commits.

## Licence and credits

GPL-3.0, like DiPlay and xcertplay; see [LICENSE](LICENSE). The CarPlay protocol work is DiPlay's and xcertplay's.

ChairPlay is built on [DiPlay](https://github.com/shihabal3amri/DiPlay) by shihabal3amri, a CarPlay receiver for
Android car head units, which is based on [xcertplay](https://github.com/shilapi/xcertplay) by shilapi.
ChairPlay is a separate project, not affiliated with their authors.

The HUD's 3D maneuvers and destination flag are LuKa's, from
[mib2q-carplay-rgi](https://github.com/luka-dev/mib2q-carplay-rgi) (GPL-3.0 or later);
[native/maneuver/README.md](native/maneuver/README.md) lists what was taken and changed. The notices of the bundled
libraries are in `THIRD_PARTY_NOTICES` and come with the app.

CarPlay, iPhone, AirPlay and Siri are trademarks of Apple Inc. ChairPlay is not affiliated with Apple.
No Apple MFi identity is stored in this repository.
