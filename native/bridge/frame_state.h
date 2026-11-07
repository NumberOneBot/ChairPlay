// The frame bridge's state, shared by frame.cpp (the calls and the status file) and frame_device.cpp (the device and
// the textures). Every function here runs under CpbLock.
#ifndef CHAIRPLAY_FRAME_STATE_H
#define CHAIRPLAY_FRAME_STATE_H

#include <windows.h>
#include <d3d11.h>

#include "bridge_layout.h"

const int CPB_FPS_WINDOW = 256;

// A shared texture and the NT handle its name lives by.
struct CpbShared {
    ID3D11Texture2D* texture = nullptr;
    HANDLE handle = nullptr;
};

struct CpbBridge {
    ID3D11Device* device = nullptr;
    ID3D11DeviceContext* context = nullptr;
    int warp = 0;                        // the device runs on WARP, as cpb_frame_open asked
    HANDLE mapping = nullptr;
    cpb_status* status = nullptr;
    ID3D11Texture2D* scratch = nullptr;  // the private texture UpdateSubresource writes
    CpbShared current;
    CpbShared previous;                  // the last generation, kept for readers still on it
    LONGLONG previous_since = 0;
    unsigned width = 0, height = 0, generation = 0;
    char name[64] = {};
    unsigned long long frames = 0;
    LONGLONG stamps[CPB_FPS_WINDOW] = {};
    unsigned next_stamp = 0;
    LONGLONG frequency = 1;
};

extern CpbBridge cpb_bridge;

// The last generation this process shared, kept across close and open: a game holding the status file or the last
// texture keeps it alive, so a reopened bridge must never give it a name it already holds.
extern unsigned cpb_last_generation;

LONGLONG cpb_now();

void cpb_release_shared(CpbShared& shared);

// A device on the high-performance adapter (WARP when warp != 0) and its adapter's LUID; 0 or negative (cpb_fail).
int cpb_make_device(int warp, ID3D11Device** device, ID3D11DeviceContext** context, LUID* luid);

// Releases the textures, the context and the device; the status file stays.
void cpb_release_gpu();

// A texture pair for a new size, shared under the next generation's name. On failure nothing changes.
int cpb_new_generation(int width, int height);

#endif
