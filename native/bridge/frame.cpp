// chairplay_bridge.dll, the picture: the frame calls and the status file Local\ChairPlay.Bridge.v1 (bridge_layout.h);
// the device and the textures are frame_device.cpp's. Nothing here waits on a reader: there is no keyed mutex.
#include <windows.h>
#include <d3d11.h>
#include <string.h>

#include "bridge.h"
#include "bridge_layout.h"
#include "frame_internal.h"
#include "frame_state.h"
#include "seqlock.h"

CpbBridge cpb_bridge;
unsigned cpb_last_generation = 0;

namespace {

const int MAX_SIDE = 16384;

CpbBridge& bridge = cpb_bridge;

// Test hook state (cpb_test_lose_device).
bool lose_device = false;
bool fail_new_device = false;

void close_locked() {
    cpb_release_gpu();
    if (cpb_status* s = bridge.status) {
        // A game may hold the file past this close: tell it the picture is gone.
        cpb_seq_begin(s);
        s->state = 0;
        memset(s->texture_name, 0, sizeof s->texture_name);
        cpb_seq_end(s);
        UnmapViewOfFile(s);
    }
    if (bridge.mapping) CloseHandle(bridge.mapping);
    bridge = CpbBridge();
}

int abandon(int result) {
    close_locked();
    return result;
}

int open_locked(int warp) {
    if (bridge.device) return 0;
    LUID luid = {};
    int result = cpb_make_device(warp, &bridge.device, &bridge.context, &luid);
    if (result != 0) return abandon(result);
    bridge.warp = warp;

    bridge.mapping = CreateFileMappingW(INVALID_HANDLE_VALUE, nullptr, PAGE_READWRITE, 0, CPB_FILE_SIZE,
                                        CPB_STATUS_MAPPING);
    if (!bridge.mapping) return abandon(cpb_fail("CreateFileMappingW", HRESULT_FROM_WIN32(GetLastError())));
    bool existed = GetLastError() == ERROR_ALREADY_EXISTS;
    bridge.status = (cpb_status*)MapViewOfFile(bridge.mapping, FILE_MAP_WRITE, 0, 0, CPB_FILE_SIZE);
    if (!bridge.status) return abandon(cpb_fail("MapViewOfFile", HRESULT_FROM_WIN32(GetLastError())));
    LARGE_INTEGER frequency;
    QueryPerformanceFrequency(&frequency);
    bridge.frequency = frequency.QuadPart;

    cpb_status* s = bridge.status;
    // Goes on from the newest generation the file (when a game still holds it) or this process has seen: a game
    // may still hold the last texture by name after the file went.
    unsigned held = existed && s->magic == CPB_STATUS_MAGIC ? s->generation : 0;
    bridge.generation = held > cpb_last_generation ? held : cpb_last_generation;
    // Readers may be on a held file: clear it inside the seqlock, all but seq. A writer that died mid-write left
    // seq odd, so readers already retry and the write goes on from there.
    if ((*cpb_seq(s) & 1) == 0) cpb_seq_begin(s);
    else MemoryBarrier();
    memset(s, 0, offsetof(cpb_status, seq));
    memset((char*)s + offsetof(cpb_status, state), 0, CPB_FILE_SIZE - offsetof(cpb_status, state));
    s->magic = CPB_STATUS_MAGIC;
    s->generation = bridge.generation;
    s->version = CPB_VERSION;
    s->size = offsetof(cpb_status, reserved);
    s->state = 1;
    s->dxgi_format = CPB_DXGI_FORMAT_B8G8R8A8_UNORM;
    s->adapter_luid_low = luid.LowPart;
    s->adapter_luid_high = luid.HighPart;
    s->producer_pid = GetCurrentProcessId();
    cpb_seq_end(s);
    return 0;
}

unsigned frames_in_last_second(LONGLONG t) {
    bridge.stamps[bridge.next_stamp++ % CPB_FPS_WINDOW] = t;
    unsigned fps = 0;
    for (LONGLONG stamp : bridge.stamps) {
        if (stamp != 0 && t - stamp < bridge.frequency) fps++;
    }
    return fps;
}

// The picture into the shared texture, a new one for a new size; *lost says a failure was the device's loss.
int upload_locked(const void* bgra, int width, int height, int stride, bool* lost) {
    if ((unsigned)width != bridge.width || (unsigned)height != bridge.height) {
        int result = cpb_new_generation(width, height);
        if (result != 0) {
            *lost = FAILED(bridge.device->GetDeviceRemovedReason());
            return result;
        }
    }
    bridge.context->UpdateSubresource(bridge.scratch, 0, nullptr, bgra, (UINT)stride, 0);
    bridge.context->CopyResource(bridge.current.texture, bridge.scratch);
    bridge.context->Flush();
    HRESULT hr = lose_device ? DXGI_ERROR_DEVICE_REMOVED : bridge.device->GetDeviceRemovedReason();
    lose_device = false;
    *lost = FAILED(hr);
    return *lost ? cpb_fail("the D3D11 device was lost", hr) : 0;
}

// A lost device (a GPU driver reset, TDR): the textures and the device go, the status file stays, saying "waiting"
// with no name until a picture comes, and a new device takes over, its adapter's LUID in the status. The next upload
// makes a new texture, its generation going on. With no new device the bridge closes (state 0).
int recover_locked() {
    cpb_release_gpu();
    cpb_status* s = bridge.status;
    cpb_seq_begin(s);
    s->state = 1;
    memset(s->texture_name, 0, sizeof s->texture_name);
    cpb_seq_end(s);
    LUID luid = {};
    int result = fail_new_device ? cpb_fail("D3D11CreateDevice (test)", DXGI_ERROR_DEVICE_REMOVED)
                                 : cpb_make_device(bridge.warp, &bridge.device, &bridge.context, &luid);
    fail_new_device = false;
    if (result != 0) return abandon(result);
    cpb_seq_begin(s);
    s->adapter_luid_low = luid.LowPart;
    s->adapter_luid_high = luid.HighPart;
    cpb_seq_end(s);
    return 0;
}

int publish_locked(const void* bgra, int width, int height, int stride, int sx, int sy, int sw, int sh) {
    if (!bridge.device) return cpb_fail("cpb_frame_publish before cpb_frame_open", E_UNEXPECTED);
    if (!bgra || width <= 0 || height <= 0 || width > MAX_SIDE || height > MAX_SIDE || stride < width * 4) {
        return cpb_fail("cpb_frame_publish with no picture or a bad size", E_INVALIDARG);
    }
    bool lost = false;
    int result = upload_locked(bgra, width, height, stride, &lost);
    // One new device per loss, and this picture again on it; no device to be had, or a second loss, fails.
    if (lost) {
        result = recover_locked();
        if (result == 0) result = upload_locked(bgra, width, height, stride, &lost);
    }
    if (result != 0) return result;
    LONGLONG t = cpb_now();
    if (bridge.previous.texture && t - bridge.previous_since >= bridge.frequency) cpb_release_shared(bridge.previous);
    unsigned fps = frames_in_last_second(t);

    cpb_status* s = bridge.status;
    cpb_seq_begin(s);
    s->state = 2;
    s->generation = bridge.generation;
    s->width = bridge.width;
    s->height = bridge.height;
    s->safe_x = sx;
    s->safe_y = sy;
    s->safe_w = sw;
    s->safe_h = sh;
    memcpy(s->texture_name, bridge.name, sizeof s->texture_name);
    s->frame_counter = ++bridge.frames;
    s->stream_fps = fps;
    cpb_seq_end(s);
    return 0;
}

}  // namespace

CPB_API int cpb_frame_open(int warp) {
    CpbLock lock;
    return open_locked(warp);
}

CPB_API int cpb_frame_publish(const void* bgra, int width, int height, int stride,
                              int safe_x, int safe_y, int safe_w, int safe_h) {
    CpbLock lock;
    return publish_locked(bgra, width, height, stride, safe_x, safe_y, safe_w, safe_h);
}

CPB_API void cpb_frame_state(int state) {
    CpbLock lock;
    cpb_status* s = bridge.status;
    if (!s) return;
    cpb_seq_begin(s);
    s->state = (uint32_t)state;
    cpb_seq_end(s);
}

CPB_API void cpb_frame_close(void) {
    CpbLock lock;
    close_locked();
}

void cpb_frame_lose_device(int fail) {
    lose_device = true;
    fail_new_device = fail != 0;
}

// The adapter the device runs on, for a test reader to open the texture on the same one.
HRESULT cpb_frame_adapter(IDXGIAdapter** adapter) {
    if (!bridge.device) return E_UNEXPECTED;
    return cpb_device_adapter(bridge.device, adapter);
}
