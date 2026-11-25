# Integrations with other apps

ChairPlay offers two experimental interfaces to other apps on the same PC, such as a game mod, a plugin, or a telemetry tool:

- **CarPlay picture:** ChairPlay publishes the main CarPlay screen as a named Direct3D 11 shared texture, with a status file that says whether a picture is there, its size, the texture's current name, and whether the car's display should show CarPlay or its own screen. Another app can show it on a screen of its own, for example a car's center display in a racing game.
- **Telemetry from other apps:** another app writes into a telemetry file CarPlay's knob and buttons, the car's position, heading, and speed (and optionally rpm, gear, red line, the pedals, the steering wheel, and the top speed), or both. The knob and buttons work CarPlay as the keys do; with GPS spoofing on, ChairPlay moves its GPS car to the position, and the iPhone navigates by it.

Both work through Windows shared memory and a DXGI shared resource. The other app needs no ChairPlay code; everything it needs is the names and byte layouts below. The C definitions are in [`native/bridge/bridge_layout.h`](../native/bridge/bridge_layout.h).

This is interface version 1. It is experimental: the names and layouts stay as described while the version is 1 (see [Compatibility](#compatibility)), but the behavior around them may still change.

## Turn them on

Both switches are in the settings, on the **Experiments** page under **Integrations**. Both are off by default; off, ChairPlay creates no device, texture, or thread for them. The picture's [status file](#status-file) is there whenever ChairPlay runs.

| Row | Switch | Setting | Needs |
|---|---|---|---|
| CarPlay picture | Publish as a shared texture | `bridge.picture` | nothing else |
| Telemetry | Receive from other apps | `bridge.telemetry` | nothing else; the position moves the GPS car only with GPS spoofing on (**Enable spoofing** on the GPS page, `gps.enabled`) |

Both apply at once. The iPhone learns of the spoofed GPS when it connects, so after turning GPS spoofing on, reconnect (Ctrl+R).

## Common rules

- All objects are in the user's session namespace (`Local\`). The other app runs in the same Windows session as ChairPlay, and at the same privilege level: a file created by an elevated (administrator) process may not open from a normal one.
- Only one ChairPlay runs at a time, so the names are fixed.
- Both files are 256 bytes. All numbers are little-endian. Every field sits at an offset that is a multiple of its size, so a plain C struct (or a LuaJIT FFI struct) matches the layout without packing.
- Text is UTF-8 in a fixed-size field, ended by a zero byte.
- Each file has one writer and protects its records with a seqlock in `seq` (offset 8): the writer makes `seq` odd, writes the fields, then makes `seq` even again. A reader copies the record and keeps the copy only if `seq` was even before the copy and did not change during it; otherwise it tries again or skips this read.
- A reader checks `magic` and `version` first and ignores a record whose values differ.

## CarPlay picture

### Names

| Object | Name |
|---|---|
| Status file (file mapping) | `Local\ChairPlay.Bridge.v1` |
| Texture | `ChairPlay.Main.v1.<generation>`, for example `ChairPlay.Main.v1.1`; always take the current name from the status file's `textureName` |

The texture name has no `Local\` prefix: pass it as it is to `OpenSharedResourceByName`. In Lua for Assetto Corsa's Custom Shaders Patch, both `ui.SharedTexture(name)` and `ac.readMemoryMappedFile(name, layout)` take the names without `Local\`.

### Status file

ChairPlay creates `Local\ChairPlay.Bridge.v1` as it starts, with **Publish as a shared texture** on or off, keeps it while it runs, and is its only writer. Open it read-only with `OpenFileMappingW(FILE_MAP_READ, FALSE, L"Local\\ChairPlay.Bridge.v1")`. If it does not exist, ChairPlay is not running, or it is a version before 1.1.3, which creates the file only while sharing is on; try again later, for example once a second.

| Offset | Type | Field | Meaning |
|---:|---|---|---|
| 0 | u32 | `magic` | `0x52425043` ("CPBR" in memory) |
| 4 | u16 | `version` | 1 |
| 6 | u16 | `size` | Bytes of defined fields: 160 in version 1 (136 before `screen`, 140 before `uiPort`) |
| 8 | u32 | `seq` | Seqlock: odd while ChairPlay writes |
| 12 | u32 | `state` | 0 no picture, 1 waiting for the iPhone, 2 picture flowing, 3 picture still |
| 16 | u32 | `generation` | Goes up each time ChairPlay makes a new texture |
| 20 | u32 | `width` | Texture width, px |
| 24 | u32 | `height` | Texture height, px |
| 28 | i32 | `safeX` | Safe area left edge, px |
| 32 | i32 | `safeY` | Safe area top edge, px |
| 36 | i32 | `safeW` | Safe area width, px |
| 40 | i32 | `safeH` | Safe area height, px |
| 44 | u32 | `dxgiFormat` | 87, `DXGI_FORMAT_B8G8R8A8_UNORM` |
| 48 | u32 | `adapterLuidLow` | `LUID.LowPart` of the GPU the texture is on |
| 52 | i32 | `adapterLuidHigh` | `LUID.HighPart` of that GPU |
| 56 | u64 | `frameCounter` | Goes up by one with each new picture in the texture |
| 64 | u32 | `streamFps` | Pictures in the last second |
| 68 | u32 | `producerPid` | ChairPlay's process ID |
| 72 | char[64] | `textureName` | The current texture's name, UTF-8, zero-ended; empty when there is none |
| 136 | u32 | `screen` | What the car's display shows: 0 CarPlay (the texture), 1 the car's own screen; see [The car's own screen](#the-cars-own-screen) |
| 140 | u32 | `uiPort` | The port of ChairPlay's settings page and its local HTTP API, at `http://127.0.0.1:<uiPort>/` |
| 144 | char[16] | `appVersion` | ChairPlay's version, such as `1.1.3`, UTF-8, zero-ended; empty for a development build |
| 160 | — | reserved | Zero, up to byte 256 |

`uiPort` and `appVersion` are for an app that starts or works ChairPlay, such as a launcher; read them in a record whose `size` is 160 or more. The API takes requests that name `127.0.0.1:<uiPort>` or `localhost:<uiPort>` in their `Host` header, and a POST only with `Content-Type: application/json`.

### States

| `state` | Meaning | Show |
|---:|---|---|
| 0 | No picture: sharing is off, or it failed | Your own picture |
| 1 | Waiting: sharing is on, but no CarPlay session is running, or the GPU device is being made again | Your own picture |
| 2 | A picture is flowing | The texture |
| 3 | Still: the session runs, but no new picture came for over a second | The texture |

State 3 is not a failure. CarPlay sends no pictures while its screen does not change, and the texture keeps the last one. When sharing is turned on while the screen is still, ChairPlay asks the iPhone for a new picture, so the texture fills at once.

When ChairPlay turns sharing off or closes normally, it writes `state = 0` and an empty `textureName`, and releases the texture; the file stays while ChairPlay runs. If ChairPlay ends without that (it crashed or was killed), the file still says what it said last: treat a `producerPid` that is no longer a running process as state 0, and as ChairPlay not running.

### The car's own screen

ChairPlay decides what the car's display shows as a car's head unit does, and says it in `screen`. Show the texture while `screen` is 0, and the display's own content while it is 1, for example the car's original screen texture.

| Event | `screen` |
|---|---|
| CarPlay's car button (the first app on its home screen, ChairPlay's armchair here), or Back on CarPlay's home screen | 1: the car's own screen |
| Siri or a phone call, while the car's own screen shows | 0 for as long as it lasts, then 1 again, as CarPlay borrows a car's screen |
| The app's CarPlay button (bit 14 of the telemetry's `buttons`, see [Controls](#controls)) | 0: CarPlay |
| ChairPlay's window showing CarPlay again (Esc or **Show CarPlay** over its settings) | 0: CarPlay |

- `screen` is 0 each time ChairPlay creates the file.
- The iPhone keeps sending pictures meanwhile, so the texture is current as soon as you show it again.
- The armchair's picture and name are `car.icon` and `car.label` in ChairPlay's settings file; the iPhone takes them when it connects.

### Opening the texture

1. Read the status. In states 2 and 3, with a non-empty `textureName`, open the texture: `ID3D11Device1::OpenSharedResourceByName(name, DXGI_SHARED_RESOURCE_READ, __uuidof(ID3D11Texture2D), ...)`. D3D12 gets a handle for the name with `ID3D12Device::OpenSharedHandleByName` and opens it with `OpenSharedHandle`; Vulkan and OpenGL go through `VK_KHR_external_memory_win32` and `WGL_NV_DX_interop2`.
2. The texture opens only on the same GPU. ChairPlay makes it on the high-performance adapter (`IDXGIFactory6::EnumAdapterByGpuPreference` with `DXGI_GPU_PREFERENCE_HIGH_PERFORMANCE`). Compare your device's adapter LUID (`DXGI_ADAPTER_DESC::AdapterLuid`) with `adapterLuidLow` and `adapterLuidHigh`; on a laptop with two GPUs, run your app on the high-performance one.
3. When `generation` or `producerPid` changes, release the texture you hold and open the one `textureName` names now.

ChairPlay makes a new texture, under the next generation, when the picture's size changes and when the GPU device is lost (a driver reset). It releases the previous texture on the first new picture at least one second later, so a reader has time to switch. Generations go on across turning sharing off and on and while a reader holds the status file: a name is never used for a second texture.

If the device is lost, the status says state 1 with an empty name until the new device has a picture; it then carries the new device's LUID. If ChairPlay cannot make a new device, it writes state 0 and stops sharing until the switch is turned off and on.

### The picture

- Format `DXGI_FORMAT_B8G8R8A8_UNORM`, alpha 255, one mip level, bound as a shader resource.
- Its size is the CarPlay video stream's (for example 800x480): read it from `width` and `height`, or from the texture.
- It is the iPhone's picture only, with the same colors as in the ChairPlay window and none of ChairPlay's own overlays.
- Only the main CarPlay screen is published, not the AltScreen.
- ChairPlay writes each new picture into a private texture and copies it into the shared one with one `CopyResource`, then flushes. There is no keyed mutex or other synchronization: nobody waits on anyone, and a reader may rarely see a torn picture. `frameCounter` tells a reader when a new picture arrived.

### Safe area

`safeX`, `safeY`, `safeW`, and `safeH` are the part of the picture where CarPlay keeps its controls: the CarPlay display size less the safe area margins set in ChairPlay (`display.safe.top`, `bottom`, `left`, and `right`), scaled to the texture's size. With no margins they cover the whole texture. Show the whole texture or only the safe area, as fits your screen.

## Telemetry

### Name and creation

| Object | Name |
|---|---|
| Telemetry file (file mapping) | `Local\ChairPlay.Telemetry.v1` |

Whoever opens it first creates it: ChairPlay when **Receive from other apps** is on, or the other app. ChairPlay creates it zeroed and never writes to it. Create or open it with `CreateFileMappingW(INVALID_HANDLE_VALUE, NULL, PAGE_READWRITE, 0, 256, L"Local\\ChairPlay.Telemetry.v1")` and map it with `FILE_MAP_WRITE`. The file lasts while either side holds it.

One app writes at a time. Two writers at once are not supported.

### Layout

| Offset | Type | Field | Meaning |
|---:|---|---|---|
| 0 | u32 | `magic` | `0x54435043` ("CPCT" in memory); 0 while no app has written |
| 4 | u16 | `version` | 1 |
| 6 | u16 | `size` | Bytes of defined fields: 156 in version 1 (96 before the pedals and the wheel, 108 before the top speed, 112 before the controls, 128 before the car's display, 140 before the display's options, 144 before the touch) |
| 8 | u32 | `seq` | Seqlock: odd while the app writes |
| 12 | u32 | `counter` | The app adds one with each update; ChairPlay judges freshness by its changes, not by a clock |
| 16 | u32 | `flags` | Bit 0 (1): `rpm` is given. Bit 1 (2): `gear` is given. Bit 2 (4): paused, for example in a menu or a replay. Bit 3 (8): `redline` is given. Bit 4 (16): `throttle` is given. Bit 5 (32): `brake` is given. Bit 6 (64): `steer_degrees` is given. Bit 7 (128): `top_speed` is given. Bit 8 (256): the position is given: `speed`, `heading`, `latitude`, and `longitude`. Bit 9 (512): the controls are given: `buttons` and the three steps. Bit 10 (1024): the car's display is given: `display_width`, `display_height`, `display_width_mm`, and `display_options`. Bit 11 (2048): the car's theme is given. Bit 12 (4096): the theme is dark, light without it; read only with bit 11. Bit 13 (8192): the touch is given: `touch_x`, `touch_y`, and `touch_down` |
| 20 | i32 | `gear` | −1 reverse, 0 neutral, 1 and up forward gears |
| 24 | f64 | `speed` | m/s, signed: below 0 is reversing. Required with bit 8 |
| 32 | f64 | `heading` | Degrees clockwise from true north, 0 to 360. Required with bit 8 |
| 40 | f64 | `latitude` | WGS84 degrees, −90 to 90. Required with bit 8 |
| 48 | f64 | `longitude` | WGS84 degrees, −180 to 180. Required with bit 8 |
| 56 | f32 | `rpm` | Engine speed, rpm |
| 60 | f32 | `redline` | The rpm where the tachometer's red zone starts |
| 64 | char[32] | `source` | The app's name, UTF-8, zero-ended, for ChairPlay's log (for example "Assetto Corsa") |
| 96 | f32 | `throttle` | The accelerator pedal, 0 released to 1 fully pressed |
| 100 | f32 | `brake` | The brake pedal, 0 released to 1 fully pressed |
| 104 | f32 | `steer_degrees` | The steering wheel's angle in degrees, below 0 to the left, as far round as the game turns it (±450 on a 900° wheel) |
| 108 | f32 | `top_speed` | The car's top speed, m/s, for the speedometer's scale |
| 112 | u32 | `buttons` | The buttons held now, one bit each; see [Controls](#controls) |
| 116 | i32 | `knob_steps` | The knob's turns so far: add one for each step forward, take one for each step back |
| 120 | i32 | `zoom_steps` | The AltScreen map's zoom steps so far: add one to zoom in, take one to zoom out |
| 124 | i32 | `volume_steps` | ChairPlay's volume steps so far: add one for louder, take one for quieter |
| 128 | u32 | `display_width` | The car's display: CarPlay's picture width, px, even; see [The car's display](#the-cars-display) |
| 132 | u32 | `display_height` | CarPlay's picture height, px, even |
| 136 | u32 | `display_width_mm` | The display's physical width, mm, from which the iPhone picks CarPlay's scale |
| 140 | u32 | `display_options` | Bit 0 (1): the driver sits on the right. Bits 1–2: the dock, 0 (0) auto, 1 (2) driver side, 2 (4) bottom |
| 144 | f32 | `touch_x` | The finger's place across CarPlay's picture, 0 at its left edge to 1 at its right; see [Touch](#touch) |
| 148 | f32 | `touch_y` | The finger's place down the picture, 0 at its top edge to 1 at its bottom |
| 152 | u32 | `touch_down` | 1 while the finger is on the screen, 0 when it is lifted |
| 156 | — | reserved | Write zero, up to byte 256 |

### Writing

For each update: make `seq` odd (an interlocked increment), write the fields, add one to `counter`, then make `seq` even again (a second interlocked increment), with a memory barrier after the first increment and before the second. Never write `seq` as part of copying a whole record. Update at your simulation or frame rate; ChairPlay reads the file 60 times a second while you send the controls, else at least 20 times a second.

ChairPlay does not map game coordinates. The app converts its own world into real latitude, longitude, and heading: a map projection, a track placed at a real place, a chosen starting point. ChairPlay takes position, heading, and speed as they come and does not know which game sent them.

### What ChairPlay reads

- A record with another `magic` or `version` is ignored.
- With bit 8, a record whose `speed` or `heading` is not a finite number, or whose `latitude` or `longitude` is not a finite number in range, is ignored whole. `heading` is taken modulo 360. Without bit 8 these four fields are not read.
- `rpm` counts only with bit 0 and as a finite number; `gear` only with bit 1.
- `redline` counts only with bit 3 and when above 1000 and at most 20000; otherwise it is treated as not given.
- `throttle`, `brake`, and `steer_degrees` count only with their bits and as finite numbers. The pedals are held to 0 to 1; the wheel is taken as it is, past a full turn too.
- `top_speed` counts only with bit 7 and as a finite number above 0.
- `buttons` and the three steps count only with bit 9.
- The `counter` ChairPlay finds when it opens the file is the starting point: the app drives only once `counter` changes after that.

### Controls

With bit 9, the app works CarPlay as ChairPlay's keys do, whether or not GPS spoofing is on. Bind your game's own controls, a steering wheel's buttons, or a rotary encoder to them: nothing needs to be bound in ChairPlay, and no global shortcut is needed while the game has the keyboard.

| Bit | Value | Button | Acts |
|---:|---:|---|---|
| 0 | 1 | Left | Tilts the knob left while held |
| 1 | 2 | Right | Tilts the knob right while held |
| 2 | 4 | Up | Tilts the knob up while held |
| 3 | 8 | Down | Tilts the knob down while held |
| 4 | 16 | Select | Presses the knob while held: a long hold is CarPlay's long press |
| 5 | 32 | Back | Presses Back while held |
| 6 | 64 | Home | Presses Home while held |
| 7 | 128 | Mute | Mutes or unmutes ChairPlay as the bit turns on |
| 8 | 256 | Previous | Previous track as the bit turns on |
| 9 | 512 | Play/pause | Plays or pauses as the bit turns on |
| 10 | 1024 | Next | Next track as the bit turns on |
| 11 | 2048 | Answer | Answers a call as the bit turns on |
| 12 | 4096 | End call | Ends a call as the bit turns on |
| 13 | 8192 | Siri | Siri as the bit turns on |
| 14 | 16384 | CarPlay | Shows CarPlay on the car's display again (`screen` 0 in the status file) as the bit turns on, as a car's CarPlay button does |

- Set a bit while the button is down and clear it when it is let go, every update. ChairPlay sees what is held at each read, so keep a bit set for at least 50 ms: a press shorter than one read can be missed.
- The steps are counters, not presses: on each step add one to the field (or take one), and keep the sum. ChairPlay turns the knob, zooms, or steps the volume by the difference since its last read, so no step is lost however fast an encoder turns. They wrap round as 32-bit integers.
- What the first record ChairPlay reads holds and counts is the starting point: nothing happens for it.
- With the app paused (bit 2) the controls still work: a game's menu is where they are wanted most.

### Pause

Set bit 2 in your menus, while the player chooses a car, and in replays, as if the car were switched off. While it is set, ChairPlay pauses the iPhone's music if it plays, and plays it again when the bit clears, if ChairPlay paused it; music the player starts meanwhile is left as it is. Calls and navigation prompts go on. With bit 8, the GPS car also stops (see below).
- When `counter` stops changing for 0.5 s, or **Receive from other apps** is turned off, ChairPlay lets go of what the app held; the next record is a starting point again.
- The knob the app holds is apart from the keys': letting go of one does not let go of the other.

### The car's display

With bit 10, the app sets the display CarPlay draws for: its picture's size, the display's physical width, from which the iPhone picks CarPlay's scale (its height follows the picture's aspect), the side the driver sits on, and where CarPlay puts its dock. ChairPlay takes them over its own **Display**, **Driver side**, and **Dock** settings, without changing those, until it restarts. CarPlay takes a new display only as it connects, so each change ends the running session and connects again, which takes a few seconds.

- Write the car's display as driving starts, not in menus or while the player looks through cars: each change reconnects CarPlay, a change of the driver side or the dock alone too.
- Keep writing it with every update: ChairPlay acts only on a change.
- The picture's width and height must be even and above 0, the width in millimetres above 0, and the dock 0, 1, or 2; otherwise the display is ignored.
- ChairPlay reads `display_options` only in a record whose `size` is 144 or more. In an older record of 140 bytes, the driver side and the dock stay ChairPlay's own settings.

### The car's theme

With bit 11, the app sets CarPlay's look: dark with bit 12, light without it, as a car's headlights do. ChairPlay takes it over its own **Theme** setting, without changing it, and sends each change to the iPhone at once, without reconnecting. A session that connects meanwhile starts with the app's theme.

- Keep the bits in every update while you set the theme, in menus and while paused too.
- ChairPlay's own **Theme** setting is in force again when a record comes without bit 11, when `counter` stops changing for 0.5 s, and when **Receive from other apps** is turned off.

### Touch

With bit 13, the app touches CarPlay's picture as a click in ChairPlay's window does, for example where the player's hand meets the car's screen. `touch_x` and `touch_y` say where, as fractions of the picture from its top-left corner, and `touch_down` says whether the finger is on the screen. A move while it is down is a drag.

- Keep the bit and the fields in every update while you touch, as for the buttons: ChairPlay sends only the changes, a press, a move, or a release.
- `touch_x` and `touch_y` must be finite numbers from 0 to 1; otherwise the touch is ignored, and the rest of the record stands.
- ChairPlay reads the touch only in a record whose `size` is 156 or more.
- The finger is lifted when a record comes without bit 13, when `counter` stops changing for 0.5 s, and when **Receive from other apps** is turned off. The touch works while the app is paused (bit 2) too.
- One finger at a time: a click in ChairPlay's window lifts the app's finger, and the app's next press or move lifts the window's.
- The status file's `width` and `height` show the picture's new size once the iPhone sends it.

### What ChairPlay does with the position

These hold with bit 8 and GPS spoofing on. Without either, the app's position moves nothing, and the gamepad and keys drive the GPS car.

| Telemetry | GPS car |
|---|---|
| `counter` changed within the last 0.5 s, not paused | Follows it: the car is at the given position, heading, and speed. The gamepad and keys do not drive it |
| Paused, or `counter` unchanged for 0.5 s (stale) | Speed 0, the car stays where the last update put it |
| `counter` unchanged for 5 s (gone) | The app is treated as gone: the car stays where it is, its place is saved as the GPS start (`gps.start`, `gps.heading`), and the gamepad and keys drive it again |
| `counter` never changed since ChairPlay opened the file | The gamepad and keys drive the car |

- The pause flag counts at once, even without a new `counter`. To stop the car right away, set bit 2; to hand the car back, stop updating `counter` for 5 s.
- ChairPlay sends the car to the iPhone as it does in its own GPS simulation: its position, course, and speed, and with `gps.wheel.speed` on (the default) its wheel speed, all of one moment, `gps.rate` times a second over CarPlay and `gps.usb.rate` times over USB, 10 by default.
- With **Receive from other apps** off, ChairPlay does not read the file and closes its handle.
- ChairPlay's log notes the `source` name when an app starts sending, and why the file did not open, if it did not.

### Gauges

In drive mode (F10, or the switch on the GPS page) ChairPlay shows the GPS car's speedometer and tachometer on the AltScreen. While an app drives, they show its speed, and its rpm and gear when given: rpm as is, also past the red line; the gear as R, N, D1, and so on, also past the GPS car's own gears. Without bit 1 the gear shows D1.

With `redline` given, the tachometer is red from it, with a dashed mark 500 rpm below, and its scale runs in thousands up to the next whole thousand at or above `redline` + 500 (7000 → 8, 7500 → 8, 7600 → 9). An update without bit 3 brings back the GPS car's own scale.

With `top_speed` given, the speedometer's numbers run on past its own end, by 40 km/h or 20 mph, up to the first at or above it (95 m/s, 342 km/h → 380); below the own end, the scale stops at that first number. An update without bit 7 brings back the GPS car's own scale.

The app's scales stay through a pause, stale telemetry, and the app gone, until the gamepad or keys first drive the car: from then on the gauges have the GPS car's own scales, and the app's come back with its next update.

With `throttle` and `brake` given, the gauges' pedal bars show them; with `steer_degrees` given and the gauges' wheel set to follow the steering, it turns by the angle the app gives. A value not given shows at rest.

## Examples

These snippets use [`bridge_layout.h`](../native/bridge/bridge_layout.h), whose struct definitions are:

```c
#define CPB_FILE_SIZE 256u
#define CPB_STATUS_MAGIC 0x52425043u
#define CPB_TELEMETRY_MAGIC 0x54435043u
#define CPB_VERSION 1u
#define CPB_DXGI_FORMAT_B8G8R8A8_UNORM 87u

#define CPB_TELEMETRY_RPM 1u
#define CPB_TELEMETRY_GEAR 2u
#define CPB_TELEMETRY_PAUSED 4u
#define CPB_TELEMETRY_REDLINE 8u
#define CPB_TELEMETRY_THROTTLE 16u
#define CPB_TELEMETRY_BRAKE 32u
#define CPB_TELEMETRY_STEER 64u
#define CPB_TELEMETRY_TOP_SPEED 128u
#define CPB_TELEMETRY_POSITION 256u
#define CPB_TELEMETRY_CONTROLS 512u
#define CPB_TELEMETRY_DISPLAY 1024u
#define CPB_TELEMETRY_THEME 2048u
#define CPB_TELEMETRY_DARK 4096u
#define CPB_TELEMETRY_TOUCH 8192u

#define CPB_BUTTON_LEFT 1u
#define CPB_BUTTON_RIGHT 2u
#define CPB_BUTTON_UP 4u
#define CPB_BUTTON_DOWN 8u
#define CPB_BUTTON_SELECT 16u
#define CPB_BUTTON_BACK 32u
#define CPB_BUTTON_HOME 64u
#define CPB_BUTTON_MUTE 128u
#define CPB_BUTTON_PREVIOUS 256u
#define CPB_BUTTON_PLAY_PAUSE 512u
#define CPB_BUTTON_NEXT 1024u
#define CPB_BUTTON_ANSWER 2048u
#define CPB_BUTTON_END_CALL 4096u
#define CPB_BUTTON_SIRI 8192u
#define CPB_BUTTON_CARPLAY 16384u

#define CPB_DISPLAY_RIGHT_HAND_DRIVE 1u
#define CPB_DISPLAY_DOCK_AUTO 0u
#define CPB_DISPLAY_DOCK_DRIVER 2u
#define CPB_DISPLAY_DOCK_BOTTOM 4u

#define CPB_SCREEN_CARPLAY 0u
#define CPB_SCREEN_CAR 1u

struct cpb_status {
    uint32_t magic;             /*   0 */
    uint16_t version;           /*   4 */
    uint16_t size;              /*   6: 160 */
    uint32_t seq;               /*   8: odd while being written */
    uint32_t state;             /*  12: 0 none, 1 waiting, 2 flowing, 3 frozen */
    uint32_t generation;        /*  16 */
    uint32_t width;             /*  20 */
    uint32_t height;            /*  24 */
    int32_t safe_x;             /*  28 */
    int32_t safe_y;             /*  32 */
    int32_t safe_w;             /*  36 */
    int32_t safe_h;             /*  40 */
    uint32_t dxgi_format;       /*  44 */
    uint32_t adapter_luid_low;  /*  48 */
    int32_t adapter_luid_high;  /*  52 */
    uint64_t frame_counter;     /*  56 */
    uint32_t stream_fps;        /*  64 */
    uint32_t producer_pid;      /*  68 */
    char texture_name[64];      /*  72 */
    uint32_t screen;            /* 136: whose screen to show: 0 CarPlay's (the texture), 1 the car's own */
    uint32_t ui_port;           /* 140: ChairPlay's local API, http://127.0.0.1:<ui_port>/api/ */
    char app_version[16];       /* 144: ChairPlay's version, X.Y.Z; empty for a development build */
    uint8_t reserved[96];       /* 160 */
};

struct cpb_telemetry {
    uint32_t magic;             /*   0 */
    uint16_t version;           /*   4 */
    uint16_t size;              /*   6: 156 */
    uint32_t seq;               /*   8 */
    uint32_t counter;           /*  12 */
    uint32_t flags;             /*  16 */
    int32_t gear;               /*  20: -1 R, 0 N, 1... */
    double speed;               /*  24: m/s, negative = reverse */
    double heading;             /*  32: degrees clockwise from true north */
    double latitude;            /*  40 */
    double longitude;           /*  48 */
    float rpm;                  /*  56 */
    float redline;              /*  60: rpm where the red zone starts */
    char source[32];            /*  64 */
    float throttle;             /*  96: 0 released to 1 pressed */
    float brake;                /* 100: 0 released to 1 pressed */
    float steer_degrees;        /* 104: steering wheel angle, negative = left, past a turn too */
    float top_speed;            /* 108: m/s, the speedometer's scale */
    uint32_t buttons;           /* 112: CPB_BUTTON_* held now */
    int32_t knob_steps;         /* 116: the knob's turns so far, + forward */
    int32_t zoom_steps;         /* 120: the AltScreen map's zoom steps so far, + in */
    int32_t volume_steps;       /* 124: ChairPlay's volume steps so far, + up */
    uint32_t display_width;     /* 128: the car's display: CarPlay's picture width, px, even */
    uint32_t display_height;    /* 132: its height, px, even */
    uint32_t display_width_mm;  /* 136: the display's width, mm, for CarPlay's scale */
    uint32_t display_options;   /* 140: CPB_DISPLAY_RIGHT_HAND_DRIVE, one CPB_DISPLAY_DOCK_* */
    float touch_x;              /* 144: the finger, 0 to 1 of CarPlay's picture from its left */
    float touch_y;              /* 148: 0 to 1 from its top */
    uint32_t touch_down;        /* 152: 1 while the finger is on the screen, 0 lifted */
    uint8_t reserved[100];      /* 156 */
};
```

### Reading the status and opening the texture (C++)

```cpp
#include <windows.h>
#include <d3d11_1.h>
#include "bridge_layout.h"

// The status file's handle and view, both kept open; null until ChairPlay has made the file.
static HANDLE status_mapping;
static const volatile cpb_status* status_view;

static bool open_status() {
    if (status_view) return true;
    HANDLE mapping = OpenFileMappingW(FILE_MAP_READ, FALSE, L"Local\\ChairPlay.Bridge.v1");
    if (!mapping) return false;  // ChairPlay is not running: try again later
    status_view = (const volatile cpb_status*)MapViewOfFile(mapping, FILE_MAP_READ, 0, 0, CPB_FILE_SIZE);
    if (!status_view) {
        CloseHandle(mapping);
        return false;
    }
    // Keep the handle: the view alone does not keep the file's name, so a restarted ChairPlay would make a new file.
    status_mapping = mapping;
    return true;
}

// True when out holds a whole copy: seq was even and did not change while copying.
static bool read_status(cpb_status* out) {
    const volatile char* from = (const volatile char*)status_view;
    char* to = (char*)out;
    for (int attempt = 0; attempt < 4; attempt++) {
        uint32_t before = status_view->seq;
        MemoryBarrier();
        for (size_t i = 0; i < sizeof *out; i++) to[i] = from[i];
        MemoryBarrier();
        if ((before & 1) == 0 && status_view->seq == before) return true;
        YieldProcessor();
    }
    return false;
}

static bool process_running(DWORD pid) {
    HANDLE process = OpenProcess(PROCESS_QUERY_LIMITED_INFORMATION, FALSE, pid);
    if (!process) return false;
    DWORD code = 0;
    bool running = GetExitCodeProcess(process, &code) && code == STILL_ACTIVE;
    CloseHandle(process);
    return running;
}

static ID3D11Texture2D* picture;  // the open texture, or null
static uint32_t picture_generation, picture_pid;

// Once a frame: returns the texture to draw, or null to draw your own picture instead.
ID3D11Texture2D* chairplay_picture(ID3D11Device1* device, const LUID& adapter) {
    cpb_status s;
    if (!open_status() || !read_status(&s)) return picture;  // a torn read: keep what was shown
    bool shown = s.magic == CPB_STATUS_MAGIC && s.version == CPB_VERSION && (s.state == 2 || s.state == 3) &&
                 s.texture_name[0] != 0 && s.adapter_luid_low == adapter.LowPart &&
                 s.adapter_luid_high == adapter.HighPart && process_running(s.producer_pid);
    if (picture && (!shown || s.generation != picture_generation || s.producer_pid != picture_pid)) {
        picture->Release();
        picture = nullptr;
    }
    if (shown && !picture) {
        wchar_t name[64];
        s.texture_name[63] = 0;
        MultiByteToWideChar(CP_UTF8, 0, s.texture_name, -1, name, 64);
        if (SUCCEEDED(device->OpenSharedResourceByName(name, DXGI_SHARED_RESOURCE_READ,
                                                       __uuidof(ID3D11Texture2D), (void**)&picture))) {
            picture_generation = s.generation;
            picture_pid = s.producer_pid;
        }
    }
    return picture;
}
```

`adapter` is your device's adapter LUID: `IDXGIDevice::GetAdapter`, then `IDXGIAdapter::GetDesc`. Checking the process once a frame is cheap enough at a display's rate; an app that reads the status more often can check it less often.

### Writing telemetry (C)

```c
#include <windows.h>
#include <stddef.h>
#include <string.h>
#include "bridge_layout.h"

static volatile struct cpb_telemetry* telemetry;

/* Creates the file, or opens the one ChairPlay made. 1 when open. */
int telemetry_open(const char* source) {
    HANDLE mapping = CreateFileMappingW(INVALID_HANDLE_VALUE, NULL, PAGE_READWRITE, 0, CPB_FILE_SIZE,
                                        L"Local\\ChairPlay.Telemetry.v1");
    if (!mapping) return 0;
    telemetry = (volatile struct cpb_telemetry*)MapViewOfFile(mapping, FILE_MAP_WRITE, 0, 0, CPB_FILE_SIZE);
    if (!telemetry) {
        CloseHandle(mapping);
        return 0;
    }
    /* Keep the mapping handle for as long as the app writes; close it with the view. */
    InterlockedIncrement((volatile LONG*)&telemetry->seq);
    MemoryBarrier();
    telemetry->magic = CPB_TELEMETRY_MAGIC;
    telemetry->version = CPB_VERSION;
    telemetry->size = offsetof(struct cpb_telemetry, reserved);
    memset((char*)telemetry->source, 0, sizeof telemetry->source);
    strncpy((char*)telemetry->source, source, sizeof telemetry->source - 1);
    memset((char*)telemetry->reserved, 0, sizeof telemetry->reserved);
    MemoryBarrier();
    InterlockedIncrement((volatile LONG*)&telemetry->seq);
    return 1;
}

/* One update: real-world position and heading in degrees, speed in m/s; rpm, gear, redline, the pedals (0 to 1), the
   steering wheel's angle in degrees, and the top speed in m/s as the game has them. */
void telemetry_write(double latitude, double longitude, double heading, double speed,
                     float rpm, int gear, float redline, float throttle, float brake, float steer_degrees,
                     float top_speed, int paused) {
    volatile struct cpb_telemetry* t = telemetry;
    InterlockedIncrement((volatile LONG*)&t->seq);  /* odd: being written */
    MemoryBarrier();
    t->latitude = latitude;
    t->longitude = longitude;
    t->heading = heading;
    t->speed = speed;
    t->rpm = rpm;
    t->gear = gear;
    t->redline = redline;
    t->throttle = throttle;
    t->brake = brake;
    t->steer_degrees = steer_degrees;
    t->top_speed = top_speed;
    t->flags = CPB_TELEMETRY_POSITION | CPB_TELEMETRY_RPM | CPB_TELEMETRY_GEAR | CPB_TELEMETRY_REDLINE |
               CPB_TELEMETRY_THROTTLE | CPB_TELEMETRY_BRAKE | CPB_TELEMETRY_STEER | CPB_TELEMETRY_TOP_SPEED |
               (paused ? CPB_TELEMETRY_PAUSED : 0);
    t->counter = t->counter + 1;
    MemoryBarrier();
    InterlockedIncrement((volatile LONG*)&t->seq);  /* even: whole */
}
```

Leave out a flag for a value the game does not have; ChairPlay then ignores that field.

## Starting ChairPlay minimized

ChairPlay's main window first shows the way its starter asks in the process' start-up information. An app that starts `ChairPlay.exe` minimized passes `STARTF_USESHOWWINDOW` with `SW_SHOWMINNOACTIVE` in `STARTUPINFOW` to `CreateProcessW` (or `SW_SHOWMINNOACTIVE` as `nShowCmd` to `ShellExecuteW`). From cmd, `start /min "" ChairPlay.exe` does the same, and so does **Run: Minimized** in a shortcut's properties. Pass no arguments: they replace ChairPlay's own. The AltScreen and the controls window show as usual. A main window last closed maximized opens maximized.

## Compatibility

- While the interface is compatible, `version` stays 1 and the names keep `.v1`. A change that breaks readers or writers comes with a new version and new names.
- New fields are only appended, within the 256 bytes, and `size` grows to cover them. Existing fields keep their offsets, types, and meaning.
- Readers ignore fields past what they know, and writers leave bytes they do not know as zero.
