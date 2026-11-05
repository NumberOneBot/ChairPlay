# ChairPlay 1.0.6

This update fixes a black screen at 1920×1080 and silent YouTube audio in Buffered mode, keeps the iPhone's own keyboard available while CarPlay is connected, and adds update checks.

## What's changed

- **Update checks.** At startup ChairPlay looks for a newer release and offers to open the Releases page. **About > Check for updates at startup** turns this off, and **Check now** checks at any time.
- **The iPhone keeps its keyboard.** While CarPlay was connected, the iPhone treated ChairPlay as a hardware keyboard and hid its own on-screen keyboard. It now keeps it. Typing in CarPlay from the PC keyboard works as before.
- **Black screen at 1920×1080 fixed.** After reconnecting at this resolution the screen could stay black while touches still reached the iPhone.
- **YouTube in Buffered mode fixed.** YouTube and some other apps played no sound in Buffered mode, and YouTube's video held still on the iPhone.
- **Get started.** The Wi-Fi step can be reopened from the pencil next to the network name to correct the network or password. A hint next to the Wi-Fi fields, here and in **Settings > Connection**, says to use an existing network that both the PC and the iPhone can reach.
- **Audio devices.** **Settings > Audio** shows which device **Windows default** currently plays on and records from.
- **No PowerShell.** ChairPlay reads the firewall, network adapters and location through Windows directly. Adding the firewall rule still asks for administrator rights once.
- **Faster start.** The window no longer waits for the firewall check, which now runs after it opens, or for the spatial audio check, which now runs at the connection.
- **Firewall rule for this copy.** The firewall check counts ChairPlay's rule only for this copy of the app, so a rule left from a moved or reinstalled copy no longer shows as added. Adding the rule removes such leftover rules.
- **WebView2 cache in the app folder.** The settings window's cache is kept with ChairPlay's other data. The installer removes the old cache.
- **Other fixes.** A shown Wi-Fi password no longer reverts after you replace it. Switching the Windows keyboard layout with Alt+Shift no longer leaves Alt held, which typed letters as symbols. ChairPlay no longer refuses to start over an unplugged audio device saved in the settings, which gives way to the Windows default, or a damaged iPhone pairing file, which is set aside so the iPhone pairs again. A settings file it can't read is named in the error.

## Install or update

Download **ChairPlay-1.0.6-setup.exe** from the [Releases page](https://github.com/NumberOneBot/ChairPlay/releases) and run it. Installation does not require administrator rights. Updates preserve settings, iPhone pairing, and MFi files.

For problems, use **About > Report a problem** to include the app version in a new issue.

Thanks to Vitaliy Pustovit for the bug reports.

[Full changelog](https://github.com/NumberOneBot/ChairPlay/blob/v1.0.6/CHANGELOG.md) · [Changes since v1.0.5](https://github.com/NumberOneBot/ChairPlay/compare/v1.0.5...v1.0.6)
