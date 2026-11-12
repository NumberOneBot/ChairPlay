# Roadmap

What's planned for the next versions of ChairPlay. The order can change, and there are no dates. The [changelog](CHANGELOG.md) lists what's already released.

## 1.1: USB GPS

- **USB GPS:** with the iPhone also plugged into the PC by a USB cable and Developer Mode on, the iPhone takes the GPS car's position as its own location, while CarPlay stays wireless. Navigation apps on the iPhone follow the simulated drive.
- **Track playback:** the GPS car drives a recorded trip from a GPX file, then from VBO, Tesla CSV, and FIT files.

## Game bridge

A separate app, released after 1.1. It shows CarPlay from ChairPlay on a screen in the game's car and drives the GPS car with the game's position, speed, and gauges, so the iPhone navigates the trip in the game.

- First: Assetto Corsa, Euro Truck Simulator 2, and American Truck Simulator.
- Later: Assetto Corsa EVO, BeamNG.drive, and the Forza series.

Other apps can use the same interface: see [Integrations](docs/INTEGRATIONS.md).

## Website

A website for ChairPlay with step-by-step guides to connecting the iPhone, troubleshooting by symptom, and an FAQ.

## 1.2 to 1.4: early versions for new platforms

- **ChairPlay for macOS:** a first working version for Macs with Apple silicon.
- **ChairPlay for Linux:** a first working version for Ubuntu 22.04+, Debian 12+, Linux Mint, and other systems built on them.
- **CouchPlay:** a separate app for Android TV and Google TV. One CarPlay screen with music, navigation, and Siri, controlled with the TV remote, and calls when the TV has a microphone. No AltScreen, HUD, or GPS simulation.
- **Package managers:** install and update ChairPlay with winget, Chocolatey, and Scoop.

## Later: 1.5+

- **USB CarPlay:** CarPlay over a USB cable, besides wireless.
- **Android Auto:** Android phones in the same ChairPlay app.

Ideas and requests are welcome: open an [issue](https://github.com/NumberOneBot/ChairPlay/issues/new).
