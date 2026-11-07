// chairplay_bridge.dll, the picture: a D3D11 device on the high-performance adapter, a private texture the picture
// is uploaded to, the shared texture ChairPlay.Main.v1.<generation> it is copied into, and the status file
// Local\ChairPlay.Bridge.v1 (bridge_layout.h). Nothing here waits on a reader: there is no keyed mutex.
#include <windows.h>
#include <d3d11_1.h>
#include <dxgi1_6.h>
#include <stdio.h>
#include <string.h>

#include "bridge.h"
#include "bridge_layout.h"
#include "frame_internal.h"
#include "seqlock.h"

namespace {

const int MAX_SIDE = 16384;
const int FPS_WINDOW = 256;

struct Shared {
    ID3D11Texture2D* texture = nullptr;
    HANDLE handle = nullptr;
};

void release(Shared& shared) {
    cpb_release(shared.texture);
    if (shared.handle) CloseHandle(shared.handle);
    shared.handle = nullptr;
}

struct Bridge {
    ID3D11Device* device = nullptr;
    ID3D11DeviceContext* context = nullptr;
    HANDLE mapping = nullptr;
    cpb_status* status = nullptr;
    ID3D11Texture2D* scratch = nullptr;  // the private texture UpdateSubresource writes
    Shared current;
    Shared previous;                     // the last generation, kept for readers still on it
    LONGLONG previous_since = 0;
    unsigned width = 0, height = 0, generation = 0;
    char name[64] = {};
    unsigned long long frames = 0;
    LONGLONG stamps[FPS_WINDOW] = {};
    unsigned next_stamp = 0;
    LONGLONG frequency = 1;
};

Bridge bridge;
// The last generation this process shared, kept across close and open: a game holding the status file keeps it
// alive, so a reopened bridge must never give it a name it already holds.
unsigned last_generation = 0;

LONGLONG now() {
    LARGE_INTEGER t;
    QueryPerformanceCounter(&t);
    return t.QuadPart;
}

void close_locked() {
    cpb_release(bridge.scratch);
    release(bridge.current);
    release(bridge.previous);
    if (bridge.context) {
        bridge.context->ClearState();
        bridge.context->Flush();
    }
    cpb_release(bridge.context);
    cpb_release(bridge.device);
    if (cpb_status* s = bridge.status) {
        // A game may hold the file past this close: tell it the picture is gone.
        cpb_seq_begin(s);
        s->state = 0;
        memset(s->texture_name, 0, sizeof s->texture_name);
        cpb_seq_end(s);
        UnmapViewOfFile(s);
    }
    if (bridge.mapping) CloseHandle(bridge.mapping);
    bridge = Bridge();
}

int abandon(int result) {
    close_locked();
    return result;
}

HRESULT create_high_performance_device(ID3D11Device** device, ID3D11DeviceContext** context) {
    IDXGIFactory6* factory = nullptr;
    HRESULT hr = CreateDXGIFactory1(__uuidof(IDXGIFactory6), (void**)&factory);
    if (FAILED(hr)) return cpb_fail("CreateDXGIFactory1", hr);
    IDXGIAdapter* adapter = nullptr;
    hr = factory->EnumAdapterByGpuPreference(0, DXGI_GPU_PREFERENCE_HIGH_PERFORMANCE, __uuidof(IDXGIAdapter),
                                             (void**)&adapter);
    factory->Release();
    if (FAILED(hr)) return cpb_fail("EnumAdapterByGpuPreference", hr);
    hr = cpb_create_device(adapter, device, context);
    adapter->Release();
    return FAILED(hr) ? cpb_fail("D3D11CreateDevice", hr) : S_OK;
}

int open_locked(int warp) {
    if (bridge.device) return 0;
    HRESULT hr = warp ? cpb_create_device(nullptr, &bridge.device, &bridge.context)
                      : create_high_performance_device(&bridge.device, &bridge.context);
    if (FAILED(hr)) return abandon(warp ? cpb_fail("D3D11CreateDevice (WARP)", hr) : hr);
    LUID luid = {};
    hr = cpb_device_luid(bridge.device, &luid);
    if (FAILED(hr)) return abandon(cpb_fail("IDXGIAdapter::GetDesc", hr));

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
    bridge.generation = held > last_generation ? held : last_generation;
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

// A texture pair for a new size, shared under the next generation's name. On failure nothing changes.
int new_generation(int width, int height) {
    D3D11_TEXTURE2D_DESC desc = {};
    desc.Width = (UINT)width;
    desc.Height = (UINT)height;
    desc.MipLevels = 1;
    desc.ArraySize = 1;
    desc.Format = DXGI_FORMAT_B8G8R8A8_UNORM;
    desc.SampleDesc.Count = 1;
    desc.Usage = D3D11_USAGE_DEFAULT;
    desc.BindFlags = D3D11_BIND_SHADER_RESOURCE;
    ID3D11Texture2D* scratch = nullptr;
    HRESULT hr = bridge.device->CreateTexture2D(&desc, nullptr, &scratch);
    if (FAILED(hr)) return cpb_fail("CreateTexture2D (private)", hr);
    desc.MiscFlags = D3D11_RESOURCE_MISC_SHARED | D3D11_RESOURCE_MISC_SHARED_NTHANDLE;
    Shared next;
    hr = bridge.device->CreateTexture2D(&desc, nullptr, &next.texture);
    if (FAILED(hr)) {
        cpb_release(scratch);
        return cpb_fail("CreateTexture2D (shared)", hr);
    }
    unsigned generation = bridge.generation + 1;
    char name[64] = {};  // the status' name field is zero after the name
    wchar_t wide[64];
    snprintf(name, sizeof name, "ChairPlay.Main.v1.%u", generation);
    cpb_widen(name, wide, 64);
    IDXGIResource1* resource = nullptr;
    hr = next.texture->QueryInterface(__uuidof(IDXGIResource1), (void**)&resource);
    if (SUCCEEDED(hr)) {
        hr = resource->CreateSharedHandle(nullptr, DXGI_SHARED_RESOURCE_READ, wide, &next.handle);
        resource->Release();
    }
    if (FAILED(hr)) {
        cpb_release(scratch);
        release(next);
        return cpb_fail("CreateSharedHandle", hr);
    }
    // A third size within the second: the oldest goes now, the one before keeps its second.
    release(bridge.previous);
    cpb_release(bridge.scratch);
    bridge.previous = bridge.current;
    bridge.previous_since = now();
    bridge.current = next;
    bridge.scratch = scratch;
    bridge.width = (unsigned)width;
    bridge.height = (unsigned)height;
    bridge.generation = generation;
    if (generation > last_generation) last_generation = generation;
    memcpy(bridge.name, name, sizeof name);
    return 0;
}

unsigned frames_in_last_second(LONGLONG t) {
    bridge.stamps[bridge.next_stamp++ % FPS_WINDOW] = t;
    unsigned fps = 0;
    for (LONGLONG stamp : bridge.stamps) {
        if (stamp != 0 && t - stamp < bridge.frequency) fps++;
    }
    return fps;
}

int publish_locked(const void* bgra, int width, int height, int stride, int sx, int sy, int sw, int sh) {
    if (!bridge.device) return cpb_fail("cpb_frame_publish before cpb_frame_open", E_UNEXPECTED);
    if (!bgra || width <= 0 || height <= 0 || width > MAX_SIDE || height > MAX_SIDE || stride < width * 4) {
        return cpb_fail("cpb_frame_publish with no picture or a bad size", E_INVALIDARG);
    }
    if ((unsigned)width != bridge.width || (unsigned)height != bridge.height) {
        int result = new_generation(width, height);
        if (result != 0) return result;
    }
    bridge.context->UpdateSubresource(bridge.scratch, 0, nullptr, bgra, (UINT)stride, 0);
    bridge.context->CopyResource(bridge.current.texture, bridge.scratch);
    bridge.context->Flush();
    HRESULT hr = bridge.device->GetDeviceRemovedReason();
    if (FAILED(hr)) return cpb_fail("the D3D11 device was lost", hr);
    LONGLONG t = now();
    if (bridge.previous.texture && t - bridge.previous_since >= bridge.frequency) release(bridge.previous);
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

// The adapter the device runs on, for a test reader to open the texture on the same one.
HRESULT cpb_frame_adapter(IDXGIAdapter** adapter) {
    if (!bridge.device) return E_UNEXPECTED;
    return cpb_device_adapter(bridge.device, adapter);
}
