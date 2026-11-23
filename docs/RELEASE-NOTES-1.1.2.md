# ChairPlay 1.1.2

This update makes the download smaller and stops ChairPlay from unpacking video libraries into your user profile.

## What's changed

- **Smaller download.** The installer is 77 MB instead of 93 MB, and the portable zip 92 MB instead of 104 MB. They leave out the FFmpeg libraries ChairPlay never loads.
- **Nothing left in your profile.** Earlier versions unpacked 50 MB of FFmpeg libraries into `%USERPROFILE%\.javacpp\cache` at their first start and kept an extra copy after each update. ChairPlay now loads them from its own folder. You can delete the `ffmpeg-*` and `javacpp-*` folders in `%USERPROFILE%\.javacpp\cache`.

## Install or update

Download **ChairPlay-1.1.2-setup.exe** from the [Releases page](https://github.com/NumberOneBot/ChairPlay/releases) and run it. Installation does not require administrator rights. Updates preserve settings, iPhone pairing, and MFi files. For the portable build, download **ChairPlay-1.1.2-portable.zip**: close ChairPlay, delete the **app**, **runtime**, and **helper** folders, and unpack the new zip over the old one.

For problems, use **About > Report a problem** to include the app version in a new issue.

[Full changelog](https://github.com/NumberOneBot/ChairPlay/blob/v1.1.2/CHANGELOG.md) · [Changes since v1.1.1](https://github.com/NumberOneBot/ChairPlay/compare/v1.1.1...v1.1.2)
