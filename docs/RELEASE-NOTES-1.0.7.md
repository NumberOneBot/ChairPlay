# ChairPlay 1.0.7

This update starts GPS simulation where Windows locates the PC when no coordinates are set.

## What's changed

- **GPS start from Windows.** GPS simulation turned on with empty **Coordinates**, or ChairPlay opened with it on, used to start at 0, 0 and send the iPhone no position. ChairPlay now asks Windows where the PC is, puts the car there, and saves that place as the start. Coordinates already set are kept and Windows is not asked. If Windows does not share this PC's location, type the coordinates, or turn on location for desktop apps in Windows and choose **Use Windows location**.

## Install or update

Download **ChairPlay-1.0.7-setup.exe** from the [Releases page](https://github.com/NumberOneBot/ChairPlay/releases) and run it. Installation does not require administrator rights. Updates preserve settings, iPhone pairing, and MFi files. For the portable build, download **ChairPlay-1.0.7-portable.zip**: close ChairPlay, delete the **app** and **runtime** folders, and unpack the new zip over the old one.

For problems, use **About > Report a problem** to include the app version in a new issue.

[Full changelog](https://github.com/NumberOneBot/ChairPlay/blob/v1.0.7/CHANGELOG.md) · [Changes since v1.0.6](https://github.com/NumberOneBot/ChairPlay/compare/v1.0.6...v1.0.7)
