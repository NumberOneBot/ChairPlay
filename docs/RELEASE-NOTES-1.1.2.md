# ChairPlay 1.1.2

This update makes the download smaller, stops ChairPlay from unpacking video libraries into your user profile, and makes Get started ask you to remove a forgotten iPhone from Windows before pairing it again.

## What's changed

- **Smaller download.** The installer is 70 MB instead of 93 MB, and the portable zip 85 MB instead of 104 MB. They leave out the FFmpeg libraries and the BouncyCastle classes ChairPlay never loads.
- **Nothing left in your profile.** Earlier versions unpacked 50 MB of FFmpeg libraries into `%USERPROFILE%\.javacpp\cache` at their first start and kept an extra copy after each update. ChairPlay now loads them from its own folder. You can delete the `ffmpeg-*` and `javacpp-*` folders in `%USERPROFILE%\.javacpp\cache`.
- **Time zone note.** With USB GPS, the GPS page says that the iPhone's clock follows the time zone of the car's position, and how to keep it: turn off the automatic time zone in **Settings › General › Date & Time** on the iPhone.
- **Fixed.** Updating ChairPlay while it ran no longer stops with a "GetMessage() failed" message and an "Access is denied" error on ChairPlay.exe.
- **Fixed.** **Forget** now ends the CarPlay session and drops the iPhone's pairing at once. Before, music kept playing, and the pairing stayed until ChairPlay restarted.
- **Fixed.** After **Forget**, Get started showed the iPhone as paired while Windows still listed it, and ChairPlay kept calling an iPhone that no longer accepted that pairing. **Pair the iPhone** now says to remove the iPhone in Windows' Bluetooth settings and to forget the car on the iPhone, then pair them again.

## Install or update

Download **ChairPlay-1.1.2-setup.exe** from the [Releases page](https://github.com/NumberOneBot/ChairPlay/releases) and run it. Installation does not require administrator rights. Updates preserve settings, iPhone pairing, and MFi files. For the portable build, download **ChairPlay-1.1.2-portable.zip**: close ChairPlay, delete the **app**, **runtime**, and **helper** folders, and unpack the new zip over the old one.

For problems, use **About > Report a problem** to include the app version in a new issue.

[Full changelog](https://github.com/NumberOneBot/ChairPlay/blob/v1.1.2/CHANGELOG.md) · [Changes since v1.1.1](https://github.com/NumberOneBot/ChairPlay/compare/v1.1.1...v1.1.2)
