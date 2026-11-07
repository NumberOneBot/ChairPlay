/* chairplay_bridge.dll: the game bridge's exported C API (BridgeNative.kt mirrors it).
   Every call is serialised by one lock; none waits on a reader. Results: 0 ok, negative failure (cpb_last_error). */
#ifndef CHAIRPLAY_BRIDGE_H
#define CHAIRPLAY_BRIDGE_H

#ifdef __cplusplus
#define CPB_API extern "C" __declspec(dllexport)
#else
#define CPB_API __declspec(dllexport)
#endif

/* Creates the D3D11 device on the high-performance adapter (WARP when warp != 0) and the status file
   Local\ChairPlay.Bridge.v1, zeroed, then state 1. Generations go on past the newest one the file (when a game still
   holds it) or this process has seen; the process' first is 1. Already open: 0, unchanged. */
CPB_API int cpb_frame_open(int warp);

/* Copies one BGRA picture into the shared texture ChairPlay.Main.v1.<generation>. A new size makes a new texture and
   generation; the previous one is released on the first publish at least 1 s later. Then the status: state 2. */
CPB_API int cpb_frame_publish(const void* bgra, int width, int height, int stride,
                              int safe_x, int safe_y, int safe_w, int safe_h);

/* Writes state (0 none, 1 waiting, 2 flowing, 3 frozen) to the status; nothing when closed. */
CPB_API void cpb_frame_state(int state);

/* Writes state 0 and an empty texture name, then releases the device and the textures; the status file goes with
   its last handle. */
CPB_API void cpb_frame_close(void);

/* The last failure, with its HRESULT in hex; empty when none. */
CPB_API const char* cpb_last_error(void);

/* Test hooks. read_status: 1 when a consistent copy of the status file was taken, 0 when it is missing or torn.
   open_texture: opens name on a second device on the producer's adapter and reads its size and pixel 0,0. */
CPB_API int cpb_test_read_status(void* out256);
CPB_API int cpb_test_open_texture(const char* name, int* width, int* height, unsigned* first_pixel);

/* Telemetry, Local\ChairPlay.Telemetry.v1 (256 bytes, written by a game's source). open creates the file zeroed when
   missing or opens the existing one, never clearing it; 0 or negative (cpb_last_error). Independent of the frame
   bridge. read: 1 when a consistent copy was taken into out256, 0 when closed or torn. close releases the mapping. */
CPB_API int cpb_telemetry_open(void);
CPB_API int cpb_telemetry_read(void* out256);
CPB_API void cpb_telemetry_close(void);

/* Test hook: writes in256 into the telemetry file under its seqlock, as a game's source does. 0 or negative. */
CPB_API int cpb_test_write_telemetry(const void* in256);

#endif
