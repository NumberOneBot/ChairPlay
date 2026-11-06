# ChairPlay 1.0.8

This update flags incoming calls on the taskbar, explains a Wi-Fi network hidden by Windows, lets the GPS car run to the end of the speedometer, and keeps to one running copy.

## What's changed

- **Incoming calls on the taskbar.** While a call rings, the CarPlay window's taskbar button flashes until you switch to the window or the call is answered or ends. Turn on **CarPlay > Window > Bring to front on a call** to also show the window and bring it to the front.
- **Wi-Fi hidden by Windows.** On Windows 11 24H2 and later, Windows hides the PC's Wi-Fi network from desktop apps that may not use location. ChairPlay read that as no Wi-Fi and said to connect a network cable. Get started and **Settings > Connection** now say to turn on **Let desktop apps access your location**, with a button to the location settings.
- **Faster GPS car.** The car's speed is limited only by the end of the speedometer, 300 km/h or 180 mph, and it has a seventh gear. It used to stop near 268 km/h. With the default 300 hp, air drag holds it near 283 km/h; more power reaches the end of the scale.
- **One copy at a time.** The installed copy and a portable one are two different cars for the iPhone, and both running at once fought over it. A second copy started from another folder now says so and closes.

## Install or update

Download **ChairPlay-1.0.8-setup.exe** from the [Releases page](https://github.com/NumberOneBot/ChairPlay/releases) and run it. Installation does not require administrator rights. Updates preserve settings, iPhone pairing, and MFi files. For the portable build, download **ChairPlay-1.0.8-portable.zip**: close ChairPlay, delete the **app** and **runtime** folders, and unpack the new zip over the old one.

For problems, use **About > Report a problem** to include the app version in a new issue.

[Full changelog](https://github.com/NumberOneBot/ChairPlay/blob/v1.0.8/CHANGELOG.md) · [Changes since v1.0.7](https://github.com/NumberOneBot/ChairPlay/compare/v1.0.7...v1.0.8)
