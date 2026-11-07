// chairplay_bridge.dll: the frame calls' lock, the last error, and the D3D11 device helpers frame.cpp and the test
// hooks use.
#include <windows.h>
#include <d3d11.h>
#include <stdio.h>

#include "bridge.h"
#include "frame_internal.h"

namespace {
SRWLOCK lock = SRWLOCK_INIT;
// Per thread: the frame and telemetry calls run at once on their own locks, and each caller reads its own failure.
thread_local char error_text[256];
}

CpbLock::CpbLock() { AcquireSRWLockExclusive(&lock); }
CpbLock::~CpbLock() { ReleaseSRWLockExclusive(&lock); }

int cpb_fail(const char* what, HRESULT hr) {
    snprintf(error_text, sizeof error_text, "%s failed (0x%08lX)", what, (unsigned long)hr);
    return FAILED(hr) ? (int)hr : -1;
}

CPB_API const char* cpb_last_error(void) { return error_text; }

void cpb_widen(const char* text, wchar_t* out, int size) {
    if (MultiByteToWideChar(CP_UTF8, 0, text, -1, out, size) == 0) out[0] = 0;
}

HRESULT cpb_create_device(IDXGIAdapter* adapter, ID3D11Device** device, ID3D11DeviceContext** context) {
    static const D3D_FEATURE_LEVEL levels[] = {
        D3D_FEATURE_LEVEL_11_1, D3D_FEATURE_LEVEL_11_0, D3D_FEATURE_LEVEL_10_1, D3D_FEATURE_LEVEL_10_0,
    };
    D3D_DRIVER_TYPE type = adapter ? D3D_DRIVER_TYPE_UNKNOWN : D3D_DRIVER_TYPE_WARP;
    return D3D11CreateDevice(adapter, type, nullptr, D3D11_CREATE_DEVICE_BGRA_SUPPORT, levels,
                             sizeof levels / sizeof levels[0], D3D11_SDK_VERSION, device, nullptr, context);
}

HRESULT cpb_device_adapter(ID3D11Device* device, IDXGIAdapter** adapter) {
    IDXGIDevice* dxgi = nullptr;
    HRESULT hr = device->QueryInterface(__uuidof(IDXGIDevice), (void**)&dxgi);
    if (FAILED(hr)) return hr;
    hr = dxgi->GetAdapter(adapter);
    dxgi->Release();
    return hr;
}

HRESULT cpb_device_luid(ID3D11Device* device, LUID* luid) {
    IDXGIAdapter* adapter = nullptr;
    HRESULT hr = cpb_device_adapter(device, &adapter);
    if (FAILED(hr)) return hr;
    DXGI_ADAPTER_DESC desc;
    hr = adapter->GetDesc(&desc);
    adapter->Release();
    if (SUCCEEDED(hr)) *luid = desc.AdapterLuid;
    return hr;
}
