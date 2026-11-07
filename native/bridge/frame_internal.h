// What the bridge DLL's files share: the lock every call takes, the last error, and D3D11 device helpers.
#ifndef CHAIRPLAY_FRAME_INTERNAL_H
#define CHAIRPLAY_FRAME_INTERNAL_H

#include <windows.h>
#include <d3d11.h>
#include <stddef.h>

#define CPB_STATUS_MAPPING L"Local\\ChairPlay.Bridge.v1"

// Holds the one SRWLOCK that serialises every exported call.
struct CpbLock {
    CpbLock();
    ~CpbLock();
    CpbLock(const CpbLock&) = delete;
    CpbLock& operator=(const CpbLock&) = delete;
};

template <class T> void cpb_release(T*& object) {
    if (object) object->Release();
    object = nullptr;
}

// Records "<what> failed (0x<hr>)" for cpb_last_error; returns hr when it is a failure, else -1.
int cpb_fail(const char* what, HRESULT hr);

// UTF-8 to UTF-16 into size characters, always terminated (empty when it does not fit).
void cpb_widen(const char* text, wchar_t* out, int size);

// A device on adapter, or on WARP when adapter is null; BGRA support, feature level 11.1 down to 10.0.
HRESULT cpb_create_device(IDXGIAdapter* adapter, ID3D11Device** device, ID3D11DeviceContext** context);
HRESULT cpb_device_adapter(ID3D11Device* device, IDXGIAdapter** adapter);
HRESULT cpb_device_luid(ID3D11Device* device, LUID* luid);

// The open producer device's adapter (frame.cpp); E_UNEXPECTED when closed. Caller holds CpbLock.
HRESULT cpb_frame_adapter(IDXGIAdapter** adapter);

#endif
