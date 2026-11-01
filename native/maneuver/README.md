# Maneuver renderer

The HUD's animated maneuvers (3D arrows, roundabouts with their roads, the arrow filling toward the maneuver, lanes) are drawn by `maneuver_render` from LuKa's MHI2Q CarPlay project:

- Project: <https://github.com/luka-dev/mib2q-carplay-rgi>
- Taken from commit `0ae41e348924d9ede89b410c8f3c2544b1099cc0` (2026-10-21), folder `maneuver_render/`
- Licence: GNU GPL v3 or later (the project's `LICENSE` and `NOTICE.md`), Copyright (c) 2026 LuKa (@LuKa_dev). ChairPlay is GPL v3; ChairPlay's changes below are under the same terms.

`gradle/native.gradle.kts` (task `maneuverDll`) compiles these files and ChairPlay's own in `chairplay/` with the pinned zig into `chairplay_maneuver.dll`; `hud/maneuver/` in the Kotlin code drives it.

## The files

Copied unchanged: `maneuver.c`, `maneuver.h`, `route_path.h`, `route_progress.h`, `visible_area.h`, `arrow_progress.h`, `contact_shadow.h`, `lane_guidance.h`, `lane_panel.h`, `maneuver_command.h`, `protocol.h`, `visual_style.h` and `scene/` (`scene.cpp`, `geometry.cpp`, `layout.cpp`, `lane_panel.cpp`, `scene.h`, `scene_internal.hpp`, `style.hpp`).

The destination flag's pictures, `resources/flag_atlas.rgba` (14 frames of 128 x 128 RGBA built from the project's `resources/animated_destination_flag/Flagge65x65_*.png`), ship unchanged as the app's resource `src/main/resources/hud/destination-flag.rgba`; the HUD hands them to the renderer after starting it. Beside it, `destination-flag-chairplay.rgba` keeps the chequered flag ChairPlay drew before taking the project's, in the same shape, to compare (`ManeuverNative.loadFlagAtlas(FLAG_ATLAS_CHAIRPLAY)`).

Changed, each change marked "ChairPlay" in the file:

- `gl_compat.h`: a third platform, `PLATFORM_CHAIRPLAY`, beside macOS and QNX: its GL comes from `chairplay/gl_core.h`, shaders get no precision line, and the depth buffer is a sized 24-bit format.
- `render.c`, `render.h`: `render_upload_flag_atlas` takes the destination flag's frames from memory (`render_load_flag_atlas` now reads the file and calls it).
- `render.c`: `render_sprite_flag_ex` sends the flag's quad already projected (w = 1 under an identity matrix). Drawn in 3D inside ChairPlay's OpenGL 3.3 core context, its texture coordinates reached the fragments divided by w after some frames, and the flag showed the top left quarter of its picture; the cause is not known.
- `route_path.c`: the arrival's disc, which the arrow's head turns into, tags its vertices with their distance along the head, as the arrow's head does, instead of all with the route's end. The disc lies over the end of the shaft in the same plane; tagged with the end, it stayed white while the shaft under it filled, and the two showed in turns row by row, white stripes across the blue as the fill rose into the disc.

The renderer's road masks cover the whole area of a transition at the framebuffer's density: 3.5 times its size each way, twice. At the HUD's default size (a 720 x 398 texture, supersampled 1.6 times) that is two masks of 4033 x 2231 pixels, about 72 MB of video memory, redrawn on every frame of an animation; the HUD caps its texture at 800 pixels wide. Masks at 0.6 of that density were tried and showed stairs on the roundabouts' curves.

Not taken: `main.c` (its engine is in `chairplay/engine.c`), `server.c`/`server.h` (TCP), `platform_*.c`, `platform.h`, `test_harness.c`, the build files, and the rest of `resources/`: the Audi fonts (the renderer draws no text), the background picture and the flag's source PNGs.

## ChairPlay's part (`chairplay/`)

- `engine.c`: `main.c`'s engine (the IDLE / PUSHING / SLIDING_IN state machine, command handling, the fade in after CLEAR, arrow progress and the lane panel), run one frame per call from the HUD and drawing into a texture. Commands are the same 48-byte packets as over the original's TCP port (`protocol.h`). It swaps GL state with the caller around each call, since the renderer expects to own its context.
- `gl_core.h`, `gl_core.c`: the renderer runs inside the HUD window's OpenGL 3.3 core profile context. Its GL use is the GLES 2 subset (shaders, framebuffer objects, no fixed function), so three wrappers are enough: client vertex arrays are copied into a stream buffer object inside a vertex array object of ours, shaders are translated from GLSL 1.00/1.20 to 3.30 core, and entry points past GL 1.1 are loaded with `wglGetProcAddress`.
- `gl_program_cache.h`: stands in for the original's `common/gl_program_cache.h` (a shader binary cache on the head unit's flash); it always compiles, as that header's macOS build does.
- `jni.c`: the entry points of `io.github.numberonebot.chairplay.hud.maneuver.ManeuverNative`.

## Taking a newer version

Copy the files listed above from the new commit's `maneuver_render/`, apply the ChairPlay changes again (search the old files for "ChairPlay"), compare `main.c` with `chairplay/engine.c` for engine changes, update the commit here and in `THIRD_PARTY_NOTICES`, and run the tests: `ManeuverRenderTest` draws every maneuver type into `build/maneuver-renders/`.
