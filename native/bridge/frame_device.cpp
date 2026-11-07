// chairplay_bridge.dll, the picture's GPU side: the D3D11 device on the high-performance adapter, the private texture
// the picture is uploaded to, and the shared texture ChairPlay.Main.v1.<generation> it is copied into.
#include <windows.h>
#include <d3d11_1.h>
#include <dxgi1_6.h>
#include <stdio.h>
#include <string.h>

#include "frame_internal.h"
#include "frame_state.h"

namespace {

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

}  // namespace

LONGLONG cpb_now() {
    LARGE_INTEGER t;
    QueryPerformanceCounter(&t);
    return t.QuadPart;
}

void cpb_release_shared(CpbShared& shared) {
    cpb_release(shared.texture);
    if (shared.handle) CloseHandle(shared.handle);
    shared.handle = nullptr;
}

int cpb_make_device(int warp, ID3D11Device** device, ID3D11DeviceContext** context, LUID* luid) {
    HRESULT hr = warp ? cpb_create_device(nullptr, device, context) : create_high_performance_device(device, context);
    if (FAILED(hr)) return warp ? cpb_fail("D3D11CreateDevice (WARP)", hr) : hr;
    hr = cpb_device_luid(*device, luid);
    if (FAILED(hr)) {
        cpb_release(*context);
        cpb_release(*device);
        return cpb_fail("IDXGIAdapter::GetDesc", hr);
    }
    return 0;
}

void cpb_release_gpu() {
    CpbBridge& b = cpb_bridge;
    cpb_release(b.scratch);
    cpb_release_shared(b.current);
    cpb_release_shared(b.previous);
    if (b.context) {
        b.context->ClearState();
        b.context->Flush();
    }
    cpb_release(b.context);
    cpb_release(b.device);
    b.width = b.height = 0;
}

int cpb_new_generation(int width, int height) {
    CpbBridge& b = cpb_bridge;
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
    HRESULT hr = b.device->CreateTexture2D(&desc, nullptr, &scratch);
    if (FAILED(hr)) return cpb_fail("CreateTexture2D (private)", hr);
    desc.MiscFlags = D3D11_RESOURCE_MISC_SHARED | D3D11_RESOURCE_MISC_SHARED_NTHANDLE;
    CpbShared next;
    hr = b.device->CreateTexture2D(&desc, nullptr, &next.texture);
    if (FAILED(hr)) {
        cpb_release(scratch);
        return cpb_fail("CreateTexture2D (shared)", hr);
    }
    unsigned generation = b.generation + 1;
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
        cpb_release_shared(next);
        return cpb_fail("CreateSharedHandle", hr);
    }
    // A third size within the second: the oldest goes now, the one before keeps its second.
    cpb_release_shared(b.previous);
    cpb_release(b.scratch);
    b.previous = b.current;
    b.previous_since = cpb_now();
    b.current = next;
    b.scratch = scratch;
    b.width = (unsigned)width;
    b.height = (unsigned)height;
    b.generation = generation;
    if (generation > cpb_last_generation) cpb_last_generation = generation;
    memcpy(b.name, name, sizeof name);
    return 0;
}
