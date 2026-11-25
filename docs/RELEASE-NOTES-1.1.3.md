# ChairPlay 1.1.3

This update lets another app on this PC work CarPlay's controls and show CarPlay on a car display of its own, as a real car does.

## What's changed

- **CarPlay controls from another app.** Another app on this PC can work CarPlay's knob, select, back, home, media, calls, and Siri, the AltScreen map's zoom, and ChairPlay's volume through the telemetry file, with nothing bound in ChairPlay. ChairPlay also tells it whether its car display shows CarPlay or the car's own screen, as a real car does: the armchair goes to the car's own screen, and Siri and calls bring CarPlay back while they last. The app can set CarPlay's display size, driver side, and dock for each car; ChairPlay reconnects to take it. It can also turn CarPlay light or dark at once, and touch CarPlay's picture as a click in the window does. While the app says it is paused, the iPhone's music pauses, as in a car switched off. Turn on **Receive from other apps** under **Experiments > Integrations**; it no longer needs GPS spoofing. Details for developers are in [Integrations](https://github.com/NumberOneBot/ChairPlay/blob/v1.1.3/docs/INTEGRATIONS.md).
- **Status file for launchers.** The shared picture's status file is there whenever ChairPlay runs, with picture sharing off too. It gives ChairPlay's version and the port of its local API, so a launcher can tell that ChairPlay runs and work it.
- **Start minimized.** ChairPlay's window follows **Run: Minimized** in a shortcut, `start /min`, or an app that starts it minimized.
- **Position, course, and speed together.** With **GPS method > CarPlay**, each update gives the iPhone the car's position, course, speed, and wheel speed of one moment, all at one rate, 4 times a second. Before, the wheel speed went in batches sampled on a timer of its own.
- **Internationalization begins.** The settings page picks its texts that name Windows, and the parts only some systems have, by the system ChairPlay runs on: the groundwork for versions on other systems such as Linux. Nothing changes on Windows.

## Install or update

Download **ChairPlay-1.1.3-setup.exe** from the [Releases page](https://github.com/NumberOneBot/ChairPlay/releases) and run it. Installation does not require administrator rights. Updates preserve settings, iPhone pairing, and MFi files. For the portable build, download **ChairPlay-1.1.3-portable.zip**: close ChairPlay, delete the **app**, **runtime**, and **helper** folders, and unpack the new zip over the old one.

For problems, use **About > Report a problem** to include the app version in a new issue.

[Full changelog](https://github.com/NumberOneBot/ChairPlay/blob/v1.1.3/CHANGELOG.md) · [Changes since v1.1.2](https://github.com/NumberOneBot/ChairPlay/compare/v1.1.2...v1.1.3)
