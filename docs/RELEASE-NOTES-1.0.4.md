# ChairPlay 1.0.4

This update adds H.265 video and hardware decoding for CarPlay and AltScreen, and shows the active codec in window titles.

## What's changed

- **H.265 (HEVC) video.** CarPlay and AltScreen now support HEVC. In **Settings > CarPlay > Video codec**, choose **H265** to offer both codecs or **H264** to offer only H.264. H265 is selected by default; the iPhone chooses which codec to use.
- **Hardware decoding.** Both screens use the GPU by default and automatically fall back to software if hardware decoding is unavailable or fails. You can also select **Software** in **Settings > CarPlay > Decoding**. Reconnect after changing the codec or decoding mode.
- **Active codec in window titles.** CarPlay and AltScreen show their stream's codec after the resolution.

## Install or update

Download **ChairPlay-1.0.4-setup.exe** from the [Releases page](https://github.com/NumberOneBot/ChairPlay/releases) and run it. Installation does not require administrator rights. Updates preserve settings, iPhone pairing, and MFi files.

For problems, use **About > Report a problem** to include the app version in a new issue.

[Full changelog](https://github.com/NumberOneBot/ChairPlay/blob/v1.0.4/CHANGELOG.md) · [Changes since v1.0.3](https://github.com/NumberOneBot/ChairPlay/compare/v1.0.3...v1.0.4)
