# Building and setting up ChairPlay

The README gives the short version; this page has every step and every setting.

## Get the source

DiPlay is a git submodule, so clone with it:

```
git clone --recursive <this repository>
```

In a clone made without `--recursive`, run `git submodule update --init`. The submodule is pinned to the DiPlay version this build is tested with. To move to a newer DiPlay: `git -C upstream fetch`, `git -C upstream checkout <commit or tag>`, build and run the tests, then commit the new `upstream` pointer.

For each update, audit the patches in `gradle/upstream.gradle.kts` and the desktop replacements. Remove local workarounds when upstream provides equivalent behavior, retaining regression tests. Do not edit the submodule's sources. The current core is DiPlay v0.2.22 (`28d6002b`). Remaining integration differences include:

- Keyboard input and per-view safe areas extend the upstream AirPlay configuration and commands.
- The iAP2 catalog now comes from upstream; only destination and maneuver description limits are added locally.
- Desktop identification retains GPS and per-link capability controls and the MHI2Q navigation limits.
- Desktop audio ducking keeps the iPhone's volume and ramp parameters, which upstream's Boolean callback omits.
- Mouse move coalescing and bounded Bonjour replies remain in the desktop implementations; their Android counterparts are not used by ChairPlay.

After automated tests, check Wi-Fi pairing/reconnect, main video and AltScreen, keyboard input, music, navigation ducking, and Siri/calls with an iPhone. In v0.2.22, secondary connections must come from the control session's IP.

`scripts\mirror-upstream.cmd <mirror-url>` copies DiPlay into a repository you control (create it empty and private first) and points the submodule at it, so the build survives DiPlay's repository going away. With `--local` only your clone uses the mirror and `.gitmodules` keeps DiPlay's URL for everyone else. Once the submodule uses a mirror, run the script again before each `git -C upstream fetch`.

Turn on the repository's git hooks once per clone: `git config core.hooksPath .githooks`. They refuse a commit that would add keys, the MFi identity, pairings or the app's settings, and a push of history that is not this project's (DiPlay's tags, for example). Git runs hooks with its own bash, whatever shell the commit comes from. `scripts\test-repo-tools.cmd` tests the hooks and the mirror script.

## Build

Needs JDK 17 (the only version this build is tested with). From the repository root:

```
gradlew jpackageImage
```

The result is `build\jpackage\ChairPlay\ChairPlay.exe`, a self-contained folder with its own runtime; no installer or WiX is needed. The folder also carries the licence notices (`LICENSE`, `LICENSE-concentus`, `LICENSE-speexdsp`, `THIRD_PARTY_NOTICES`, `licenses\`). Only the `windows-x86_64` native libraries are bundled.

The call echo canceller is DiPlay's copy of SpeexDSP, which is C. The build compiles it into `speex_echo.dll` (next to `ChairPlay.exe`) with the C compiler of [zig](https://ziglang.org/), so no Visual Studio or MinGW is needed. The first build downloads zig 0.16.0 (about 100 MB, checked against its SHA-256) into `%USERPROFILE%\.gradle\caches\chairplay\zig` and reuses it after that; it needs internet access once.

The settings and Get started are a web page (React and TypeScript in `webui\`, built by Vite) that the app shows in WebView2 inside its window. The build needs no Node.js on the machine: it downloads Node.js 24 (checked against its SHA-256) into `%USERPROFILE%\.gradle\caches\chairplay\node`, installs the page's packages from `webui\package-lock.json` (npm checks each against its integrity hash, and runs no install scripts), type-checks and builds the page into the jar, and runs its Vitest tests with `gradlew check` (report in `build\test-results\webui`), which runs the Kotlin tests too; `gradlew test` runs the Kotlin tests alone. The web view host, `chairplay_webview.dll`, is the [webview](https://github.com/webview/webview) library with a few calls of our own (`src\native\webview`), built by zig against the headers of Microsoft's WebView2 SDK; both are downloaded once, pinned and checked, and the SDK's `WebView2Loader.dll` ships next to `ChairPlay.exe`.

The app keeps its settings, keys and pairings in the folder of its exe. To run it from a folder of its own that rebuilds never wipe, deploy it there:

```
gradlew deploy
```

This builds the image and syncs it into `dist\ChairPlay` in the project, replacing the program and keeping `settings.properties`, `controls.properties`, `carplay-icon.png`, `window.properties`, `identity.properties`, `pairings\`, `offline-mfi\`, `screenshots\` and `logs\`. It is outside `build\`, which `gradlew clean` wipes, and git ignores it.

To install somewhere else, give the folder as the `chairplay.home` property: once with `gradlew deploy -Pchairplay.home=C:\Apps\ChairPlay`, or for good in `%USERPROFILE%\.gradle\gradle.properties`:

```
chairplay.home=C:/Apps/ChairPlay
```

`gradlew launchApp` starts the app from the same folder, and `gradlew listDevices` lists the Bluetooth and sound devices with it: the app has no console, so the list is written to `logs\devices-<date-time>.log` in that folder, and `listDevices` prints the newest one.

## In VS Code

The repository recommends the Gradle for Java extension, whose side panel lists every Gradle task (`test`, `jpackageImage`, `deploy`, ...). Terminal > Run Task adds the rest: Deploy and run, Run ChairPlay, List devices and Stop Gradle daemon. The app has no console: stop it by closing the CarPlay window, never with Terminate Task, which kills it hard.

## Set up

The files below live in the app folder, next to `ChairPlay.exe`.

1. **Pair the iPhone** in Windows Settings > Bluetooth & devices. `ChairPlay.exe devices` lists the radio and the paired devices (it connects to nothing). It has no console, so the list is written to `logs\devices-<date-time>.log` in the app folder; `gradlew listDevices` runs it and prints that file.
2. **MFi identity files.** The wireless handshake needs an Apple MFi identity (certificate and key files). This repository contains none and the app image never contains them. Put your own files in `offline-mfi\`.
3. **Settings.** `deploy` (or the first start, for a folder filled by hand) writes `settings.properties` with every setting at its default and a comment on each, together with `controls.properties` and `carplay-icon.png`; files already there are kept, and a settings file from an older version gets the settings it lacks added at its end. The settings change most of them, and Get started the Wi-Fi network (see [The settings and Get started](#the-settings-and-get-started)); every one is described here:
   - `wifi.ssid` and `wifi.passphrase`: the Wi-Fi network shared by the PC and the iPhone. Nothing connects without it; while it is blank Get started asks for it;
   - `wifi.security`: `NONE`, `WEP`, `WPA_WPA2` (default), `WPA3_TRANSITION` or `WPA3_ONLY`;
   - `ui.autoconnect` (default `true`): connect to the iPhone as the app starts; `false` waits for Connect in the toolbar;
   - `ui.autoshow` (default `true`): show CarPlay over the settings as soon as the iPhone connects; `false` stays on the settings, whose CarPlay button then shows it;
   - `ui.controls.keys` (default `false`): `true` shows each controls window button's key on it, not only while Alt is held (Show keys on the buttons, on the Keyboard page);
   - `ui.accent` (default `#0a84ff`, iOS's blue): the accent colour of the settings, the controls window, and the HUD, written `#rrggbb`; every lighter, darker, or fainter shade of it is made from it;
   - `ui.port` (default `7529`, PLAY on a phone keypad): the port of the settings' address in a browser, `http://localhost:7529`. When another program has it at start, the app takes a free port for that run, as the log and the Connection page say;
   - `ui.network` (default `false`): `true` lets phones and other PCs on the local network open the settings at this PC's address, `http://<address>:7529`, which the Connection page lists as links (Allow access from the local network; in ChairPlay's window a link opens the default browser); the app then listens on every address and serves any device that reaches the port. Applies at the next start;
   - `iphone.address`: the iPhone's Bluetooth address, only needed when several phones are paired;
   - `iphone.join.seconds` (default 30): how long an iPhone that paired before has to join the Wi-Fi after the Bluetooth start before the app starts over and gives it the network again. It joins within a second or two, so this only matters when it lost the Wi-Fi meanwhile; a new iPhone always has five minutes, to accept CarPlay. Ctrl+R (`key.reconnect`) starts over at once, ending a running session as closing the window does;
   - `device.name`: the name the iPhone shows for the PC, in its CarPlay settings and on the network (default ChairPlay, at most 63 bytes); the window title stays ChairPlay;
   - `car.label` and `car.icon`: the label and the image of the car button on the CarPlay home screen (default `ChairPlay` and `carplay-icon.png`, ChairPlay's armchair written into the app folder on the first start). Replace the image with any square PNG, JPEG, BMP or GIF; it is sent at 192 pixels at most;
   - `audio.output` and `audio.input`: the sound device CarPlay plays on and the microphone, `default` (the Windows default, as before) or a part of a device name; `ChairPlay.exe devices` lists the names;
   - `audio.music.mode` (default `buffered`): `buffered` lets supported iPhone apps send music ahead; `standard` uses ordinary playback. A saved `standard` choice is preserved. Reconnect after changing it. See [Music playback](#music-playback) for formats, output, and buffer limits;
   - `audio.echo.cancellation` (experimental, default `false`): `true` runs DiPlay's SpeexDSP echo canceller on calls, so the other side does not hear itself when CarPlay plays through speakers. It takes out of the microphone what the call stream played, as DiPlay does on head units. Siri and headphones do not need it; a canceller that fails stops for the rest of the call and logs why;
   - `log.carplay.data` (default `false`): `true` writes every message the iPhone sends about the route, the song, calls and its battery to a file in `logs\` (see [The CarPlay data log](#the-carplay-data-log));
   - `display.width` and `display.height`: the CarPlay screen size in pixels, positive even numbers (default 1280 x 720), and `display.fps`, 30 to 60 (default 60). The size alone decides how iOS draws: at @3x when both sides are at least about 1280 x 720, at @2x below that (the switch lies between 1200 x 676 and 1280 x 720; 960 x 720 and 1280 x 540 are still @2x). Above the switch more pixels give room for more, not sharper: at 1920 x 1080 the home screen holds 5 x 3 icons of the same size instead of 4 x 2. Tested on iOS 18.6;
   - the screen's physical size: either `display.physical.width.mm` and `display.physical.height.mm`, or a pixel density `display.ppi` that the app turns into millimetres. Millimetres win over `display.ppi`; a width alone takes the height from the aspect ratio. Unset, the screen is 200 mm wide, as on DiPlay's head units. On iOS 18.6 the size itself changes nothing (100 and 400 mm wide draw the same picture), but iOS takes the ratio of width to height in millimetres for the shape of a pixel: when it differs from the ratio in pixels, iOS lays out for that shape and the picture comes out squeezed or stretched in the window. Leave them blank unless you are testing; they are kept for later iOS versions, where the physical size may matter;
   - `window.width` and `window.height`: the size of the window on the PC (default: the display size). The picture is scaled into the window, so this changes only how big it looks, not what the iPhone draws. The app also remembers where you leave the window, its size and whether it is maximized, in `window.properties`, and opens it there the next time; changing these two settings (or the display size they follow) replaces the remembered size. A window left on a monitor that is no longer connected opens where Windows puts it. Delete `window.properties` to forget the place;
   - `window.controls` (default `false`): `true` opens a window of on-screen car controls beside CarPlay's: the knob (select in the middle, tilts around it, turns above its corners, the wheel over it turns it), back, home, Siri, the media keys, the volume, the call keys, and the AltScreen's zoom and modes. A button held with the mouse holds its control until the release, as a held key does, so CarPlay tells a long press, and the keys of `controls.properties` work while the window has the focus; holding Alt shows each button its key. The window is a page of the web UI (WebView2) and keeps its place under `controls.` in `window.properties`;
   - `window.keep.aspect` (default `true`): the windows resize only with the shape of their picture, the main window's that of `display.width` x `display.height` and the AltScreen's that of its size; a maximized window still fills the screen. `false` lets them take any shape;
   - `window.always.on.top` and `altscreen.always.on.top` (both default `false`): keep CarPlay or AltScreen above other windows independently. Each switch is in its screen's Window section and applies immediately;
   - `altscreen.enabled` (experimental, default `false`): `true` declares a second CarPlay screen, the car's instrument cluster, and shows it in a window of its own (place and size remembered under `altscreen.` in `window.properties`). The iPhone learns of it when it connects, so a change applies at the next connection: the window appears, hides, or takes its new size then. `altscreen.width` and `altscreen.height` set its size in pixels (default 1440 x 540, Audi's Virtual Cockpit panel), and `altscreen.content` what it starts with: `map`, `card` (the turn card) or `map-card`. F11 (`key.altscreen.content`) switches between them without reconnecting, and the mouse wheel over the window or + and − on either keyboard (`key.altscreen.zoom.in`/`out`) zoom the map, as a car's zoom control does. `altscreen.cards` places the navigation app's ETA and turn card on `map-card`: `auto` (default, the driver's side `display.right.hand.drive` declares, at the left of a left-hand drive car's cluster and at the right of a right-hand drive one's, as seen with Google Maps), `left`, `right`, or `top`. Neither Google Maps nor Apple Maps hides them for an "off", so there is none. Shift+F11 (`key.altscreen.cards`) moves them on the fly on `map-card`, and so does a change on the AltScreen page. The AltScreen opens on the content and layout F11 and Shift+F11 left it on (kept in `window.properties`), unless these two settings changed since. `altscreen.safe.top`/`bottom`/`left`/`right` (default 0) are pixels at each edge the iPhone keeps its map attribution and car marker out of, for an overlay of our own; all zero uses the whole panel, since nothing covers a window on a PC;
   - `display.theme`: `windows` (default: follows the Windows dark mode, also while running), `light`, `dark`, or `phone` (the iPhone decides by itself); a change on the CarPlay page applies at once, as a car's headlights do;
   - `display.right.hand.drive` (`true` puts the dock and controls on the right), `display.dock` (`auto`, `driver` or `bottom`), `display.safe.top`/`bottom`/`left`/`right` (pixels CarPlay keeps its controls out of) with `display.safe.draw.outside`, and `display.corners` (`square`, the default, fills the window to its corners; `rounded` keeps CarPlay's rounded corners);
   - `display.touch`: the touchscreen CarPlay is told of. `none` (the default, for a mouse) has CarPlay leave out the scroll buttons in its lists; clicks and drags still reach it as taps, and the keys and the controls window move its focus. `swipe` (a touchscreen that swipes) and `tap` (one that only taps) have CarPlay show them. The knob is always declared.

   The settings apply at the next start (the Wi-Fi network with the next connection, `ui.autoshow` and `ui.controls.keys` at once); the log (`logs\chairplay-<date-time>.log` in the app folder) shows what the iPhone was told, for example `display 1280x720 px, 200x113 mm (163 ppi), 60 fps; window 1280x720`.
4. **Firewall.** Windows Firewall drops the iPhone's inbound connections. Add the rule, in Get started and on the Connection page, adds these rules with the administrator's consent (Windows asks once); or add them yourself, once, from Terminal (Admin) (the paths are those of the packaged exe; adjust them if you move the folder). At start the app writes the same commands into its log as warnings, which a release's default log level keeps, when no rule named `ChairPlay CarPlay inbound` exists, unless the firewall is off for the networks the PC is on, which needs no rule:

   ```
   netsh advfirewall firewall add rule name="ChairPlay CarPlay inbound" dir=in action=allow program="<folder>\ChairPlay.exe" protocol=TCP localport=7000-7010,49152-65535
   netsh advfirewall firewall add rule name="ChairPlay CarPlay inbound" dir=in action=allow program="<folder>\ChairPlay.exe" protocol=UDP localport=49152-65535
   netsh advfirewall firewall add rule name="ChairPlay CarPlay inbound" dir=in action=allow program="<folder>\ChairPlay.exe" protocol=UDP localport=5353
   ```

   Replace `<folder>` with the full path of `ChairPlay`, e.g. `C:\Apps\ChairPlay`. With `ui.network` on, the rule also opens the settings' port; a rule added before that lets the iPhone in but not other devices to the settings, and the log and the Connection page say so until it is added again:

   ```
   netsh advfirewall firewall add rule name="ChairPlay CarPlay inbound" dir=in action=allow program="<folder>\ChairPlay.exe" protocol=TCP localport=7529
   ```

   If Windows asks at the first start whether ChairPlay may use the network, answering Cancel creates a rule that blocks it, and a block rule wins over the ones above. At start the app names every enabled inbound block rule for its exe; delete those, or set them to Allow, in Windows Defender Firewall > Advanced settings > Inbound Rules. The check uses PowerShell's firewall cmdlets and finds rules whose program path is written out in full, not through an environment variable such as `%ProgramFiles%`.

## Music playback

**Audio → Music → Playback mode** offers Buffered and Standard. Buffered is the default and lets supported iPhone apps send music ahead to cover brief network interruptions. An existing Standard preference is preserved. Reconnect after changing the mode.

Buffered supports AAC stereo and APAC 5.1 for Apple Spatial Audio. The iPhone chooses the format from those ChairPlay offers. APAC is offered only when the decoder at `apac/chairplay_apac.dll` and Windows output are available; a missing or incompatible DLL leaves Buffered AAC available. The selected Windows audio device determines whether APAC plays as 5.1 or is mixed down to stereo. Standard does not offer APAC.

The **Buffer** row shows stream state and unplayed audio in seconds and KB, including audio already queued for output. During APAC playback it also shows **5.1** or **5.1 → stereo**; AAC has no multichannel label. In Buffered mode, this row replaces **Startup silence**, even when there is no stream. Startup silence applies only to standard streams.

The buffer is limited to 120 seconds and a transport-specific byte capacity: 8 MiB for legacy AAC transport or 21,168,000 bytes for APAP. The first limit reached takes effect.

## Releases

A release is a tag `vX.Y.Z` on `main`. Create it and push it:

`git tag v0.1.0` then `git push origin v0.1.0`

The workflow in `.github/workflows/release.yml` runs the tests, builds `ChairPlay-X.Y.Z-setup.exe` with `gradlew installer`, and drafts a GitHub release with the installer attached. Write the release notes, then publish the draft. Versions that start with 0 are marked as pre-releases. A tag that is not `vX.Y.Z` fails the workflow before it builds anything. To run the workflow again for the same tag, delete its draft release first: the workflow does not replace one.

For a dry run without a tag, run the workflow from the Actions tab (Run workflow): it runs the same tests and builds `ChairPlay-0.0.0-setup.exe`, kept as the run's artifact, and drafts no release.

## Running

```
ChairPlay.exe
```

With no arguments the launcher runs `run`. Passing any argument replaces that default, so `ChairPlay.exe devices` and `ChairPlay.exe probe-bt [iphone-address]` run those commands instead. The app has no console: it logs to `logs\chairplay-<date-time>.log` in the app folder, and the CarPlay screen opens in its own window, titled ChairPlay.

The iPhone lists the PC as **ChairPlay** (or `device.name`). The CarPlay home screen shows ChairPlay's armchair (`car.icon`) where a car shows its maker's; tapping it opens the settings over CarPlay and leaves CarPlay running.

Besides touch with the mouse, the keyboard and the mouse wheel work as a car's controls. These are the defaults; the Keyboard page changes them, and so does `controls.properties` in the app folder, written on the first start with every action documented, which rebinds them (`key.siri=Ctrl+S, F10`; empty turns an action off) and sets the wheel (`wheel.action=knob` or `none`, `wheel.invert`, `wheel.notches.per.step`) and `key.repeat.every`. Escape always goes back and Enter always selects, whatever the file says; a file that binds either to another action, or has any other mistake, stops the start and names the setting. Delete the file to get the defaults back.

`global.enabled` in `controls.properties` (default `false`) enables the existing shortcuts while another Windows app has focus, including full screen. **Keyboard > Global shortcuts** changes it immediately. The focused app also receives the keys. Background text input stays disabled, and global shortcuts pause while a ChairPlay settings or Controls page has focus.

F12 (`key.screenshot`) saves the CarPlay screen, and the AltScreen when it is on, as PNGs at the size the iPhone draws them, not the windows', in `screenshots` in the app folder; the log names the files. For unattended runs, the environment variable `CHAIRPLAY_SCREENSHOT_AFTER=<seconds>` saves one screenshot that many seconds after the first picture. It saves CarPlay's picture only, never the settings.

## The settings and Get started

Before an iPhone has connected once, and again after Forget this iPhone, the window shows **Get started**: two to four steps the app checks every few seconds while the page shows, the first one not done open with what to do:

- **MFi files** in `offline-mfi\`, with a button that opens the folder. This step is omitted when both `identity.pk8` and `certificate.p7b` are present. It returns if either file disappears;
- **Firewall**: Add the rule runs the netsh commands above with the administrator's consent, or copy them into Terminal (Admin); a rule that blocks ChairPlay is left to you, with a button that opens the firewall's rules. The firewall is read again at most every ten seconds, and only while it is wrong. While the firewall is off for the networks the PC is on, the step is left out, as no rule is needed;
- **Pair the iPhone**: done once Windows lists a paired phone (the one `iphone.address` names, when it is set);
- **Wi-Fi network**: filled in with the network the PC is on, and saved with its password. It says so when the network is not the PC's, and opens again when the iPhone answered over Bluetooth but did not join, since the password is then the likely cause.

The visible steps are numbered consecutively, and the total adjusts when a step is omitted. Once all checks pass, You're all set says what comes next and the toolbar's Connect lights up. The first CarPlay session marks the setup done in `window.properties` (an install with pairing records from before counts as done). Skip to settings leaves the guide until the next start; Show again, on the Connection and About pages, brings it back.

Otherwise, whenever CarPlay is not on screen, the window shows the **settings**: a toolbar over a list of pages.

- The **toolbar**: CarPlay (or Esc) shows CarPlay, AltScreen and Controls show and hide those windows, all three with a session only and, from a browser, in the app's own windows, never in the browser. At its right end: what waits for the next connection or a restart, where the connection stands (*Wi-Fi network needed*, *Not connected*, *Starting*, *Waiting for the iPhone*, *Connecting*, *Connected*, or *Trying again*, with more in its tooltip), and Connect, or Reconnect (what Ctrl+R does) and Disconnect once connecting has begun. Disconnect ends the session gently and leaves it ended until Connect, a retry that was waiting included.
- **Connection**: what is wrong, above everything (the last error with its time and what to do, missing MFi files, the firewall, no paired iPhone), then the Wi-Fi network and the iPhone: the paired iPhone with Forget, which deletes ChairPlay's pairing records in `pairings\` and opens Get started (Windows' pairing stays, and the running app keeps the pairing it loaded until it restarts), its name on the iPhone, its address, the time it has to join, and the two switches. The stored password, in `settings.properties` as written, shows only when the eye beside it is clicked, which asks the app for it; the page's state and the log only say whether one is set.
- **CarPlay**: the screen's size, with Actual size while the window was resized, to bring it back to one screen pixel per picture pixel; frame rate, physical size, theme, dock, driving side, what CarPlay draws outside the safe area, corners, and safe area. The theme applies at once; the iPhone asks how its screen looks at each connection, so the rest apply at the next one (Reconnect gets there at once), and the window takes the new size's shape.
- **AltScreen**, **Audio** (the devices Java Sound lists, or a saved one marked as not found), **Keyboard**, and **About**: the build and the DiPlay core it has, the credits, and where to get help. New issue on GitHub and Copy details carry the build, the core, and the Windows version, never a setting.

A field saves on Enter or when it loses the focus, a choice at once. Each change goes into `settings.properties` or `controls.properties` in place, keeping the comments and every other line, after the same checks the app makes when it starts; a refused value stays in red with the reason. A changed setting that applies at the next connection is marked ⟳ in its row while a connection is made or runs (without one, the next connection reads it as it is), one that applies after a restart with the power sign, and the toolbar shows the same sign, its tooltip naming the pages. A setting set back to the value in force loses its mark, and the toolbar its sign once nothing else waits; the next connection, or the restart, clears them too.

The **Keyboard** page lists every action with its keys: click a key to press another in its place, + adds one (up to three), right-click removes one, and Reset puts back an action's defaults. A key another action has moves at once, Esc cancels the wait, and the locked keys are Escape on Back and Enter on Select. Changes apply at once.

While the page covers the window, the keyboard and the mouse are the page's; while CarPlay shows, the page is hidden and every key works as before.

The page runs in Microsoft Edge WebView2, which Windows 11 and an updated Windows 10 carry. Without it (or without `chairplay_webview.dll`) the app opens the page in the default browser, says so in the log, and the car button minimizes the window as before. WebView2 keeps its profile (cache) in the `webview2` folder next to `ChairPlay.exe`, with the app's other data.

The page talks to the app over HTTP, on `127.0.0.1` only by default, on port 7529 (`ui.port`, which Settings browser on the Connection page changes for the next start, or a free one when that is taken), with no key; the log has the address from the start (`UI at http://localhost:<port>`), and any browser on the PC can open it, for example to look at the screens from a second monitor. With `ui.network` on, it listens on every address, and a phone or another PC opens it at this PC's address (Settings browser on the Connection page lists them), the Wi-Fi password included. A request naming another host than `127.0.0.1`, `localhost` or, with `ui.network` on, one of this PC's IPv4 addresses, with that port, is refused, so a web site cannot reach the app through a name of its own that leads to this PC.

The settings can be open in ChairPlay's window and in any number of browser tabs at once; each page follows the others' saves. Open, after the This PC address in Settings browser on the Connection page, opens the default browser there; a browser tab has no such button.

### Changing the screens

The page's sources are in `webui\src`. To work on them with Vite's dev server, which reloads the page on every save:

1. Start the app (`ChairPlay.exe` in the app folder). Without an iPhone at hand, put `wifi.ssid=TestNet` and `iphone.address=02:00:00:00:00:01` in a test folder's `settings.properties` (`gradlew deploy -Pchairplay.home=<test folder>`), so it connects to nothing.
2. In `webui\`, start the dev server with the build's own Node.js (run `gradlew npmCi` once first):

   ```
   set PATH=%USERPROFILE%\.gradle\caches\chairplay\node\node-v24.22.1-win-x64;%PATH%
   npm run dev
   ```

   Its proxy sends `/api` to `127.0.0.1:7529`, the app's port, unless `CHAIRPLAY_UI_PORT` names another; set before `ChairPlay.exe` starts, the same variable moves the app to that port too, in place of `ui.port`.
3. Open `http://localhost:5173/` in a browser.

`npm test` (or `gradlew webuiTest`) runs the page's tests, and `gradlew webuiBuild` builds it as the app ships it. `CHAIRPLAY_UI_DEBUG=1` turns on the developer tools (F12) and the context menu inside the app's window.

The AltScreen's HUD (`altscreen.content=hud`) draws the next maneuver animated, with the maneuver renderer of LuKa's MHI2Q CarPlay project (`chairplay_maneuver.dll` next to the exe). To watch it without an iPhone, `CHAIRPLAY_HUD_DEMO=1` plays a made-up route of every kind of maneuver, without lanes, into the HUD in place of the iPhone's guidance, beside the iPhone's song while a session plays one (`CHAIRPLAY_HUD_DEMO=media` with a made-up song beside it), and `CHAIRPLAY_HUD_DEMO_SHOTS=1` saves three screenshots of each maneuver of its first lap (`altscreen-demo-*` in `screenshots`). The HUD's parts rise from the dark and fade back into it; `CHAIRPLAY_HUD_DEMO=cycle` plays these animations over and over (a song, guidance coming beside it and going, nothing), and with `CHAIRPLAY_HUD_DEMO_SHOTS=1` its first cycle saves a screenshot half way through each. `CHAIRPLAY_HUD_DEMO=lanes` holds one exit 1 km ahead and switches the lanes under it every 2.5 seconds: the recommended lane moves, a lane is added, the row grows past the frame's five cells, goes and comes back; with `CHAIRPLAY_HUD_DEMO_SHOTS=1` it saves one screenshot of each.

## The CarPlay data log

Besides the picture and the sound, the iPhone tells the car about the drive and the phone over iAP2: route guidance (the next maneuver, its distance, the road, the arrival time, the remaining time and distance), now playing (title, artist, album, position, playback state) and its artwork, calls, the phone's network and its battery. The app collects them for screens to come, and asks for more of the song (album artist, genre, track and disc, queue, shuffle and repeat), of calls (the number's label, FaceTime, conferences) and of the network (carrier, registration, airplane mode, voicemail) than DiPlay does. Lane guidance and the phone's lists (recent calls, favourites) would need DiPlay's identification to declare them, so they do not arrive. With `log.carplay.data=true` it also writes each of these messages, as it arrives, to `logs\carplay-data-<yyyy-MM-dd-HHmmss>.jsonl` in the app folder: one file per start, made with the first message, and one JSON object per line, for example

```
{"time":"2026-10-22T14:03:07.412+02:00","link":"wifi","id":"0x5201","name":"RouteGuidanceUpdate","fields":{"routeGuidanceState":"ROUTE_SET","currentRoadName":"Main Street","distanceToNextManeuver":150,"currentManeuverList":[3,4]}}
```

`link` says which iAP2 link carried it (`bluetooth` while CarPlay starts, `wifi` once it runs over Wi-Fi). A parameter the app does not know yet is listed under `unknown` as `"<id>:<hex>"`, and a message it cannot decode is written as `hex`. Only these data messages are written: the Wi-Fi passphrase, the MFi certificate and its challenges never reach the file. The file grows by a line or two a second while music plays, and holds the names and numbers of callers; delete it when it is no longer needed.

## Keyboard bindings

See the table in the README for the defaults, and the Keyboard page to change them. `controls.properties` lists every action with its keys and a description; each line is `action = keys`, keys separated by commas, with `Shift+`, `Ctrl+` and `Alt+` modifiers, key names after their place on a US keyboard (so letters work in any layout) or a raw GLFW code such as `#290`.
