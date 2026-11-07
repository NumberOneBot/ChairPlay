// chairplay_bridge.dll, test hooks: read the status file and open the shared texture as a game would, from outside
// the producer (their own mapping handle, their own device).
#include <windows.h>
#include <d3d11_1.h>

#include "bridge.h"
#include "bridge_layout.h"
#include "frame_internal.h"
#include "frame_state.h"
#include "seqlock.h"

namespace {

// Everything one texture read holds, released in reverse whatever step failed.
struct Reader {
    IDXGIAdapter* adapter = nullptr;
    ID3D11Device* device = nullptr;
    ID3D11DeviceContext* context = nullptr;
    ID3D11Device1* device1 = nullptr;
    ID3D11Texture2D* texture = nullptr;
    ID3D11Texture2D* staging = nullptr;
    ~Reader() {
        cpb_release(staging);
        cpb_release(texture);
        cpb_release(device1);
        cpb_release(context);
        cpb_release(device);
        cpb_release(adapter);
    }
};

}  // namespace

CPB_API int cpb_test_read_status(void* out256) {
    if (!out256) return 0;
    HANDLE mapping = OpenFileMappingW(FILE_MAP_READ, FALSE, CPB_STATUS_MAPPING);
    if (!mapping) return 0;
    void* view = MapViewOfFile(mapping, FILE_MAP_READ, 0, 0, CPB_FILE_SIZE);
    int consistent = view ? cpb_seq_read(view, out256, CPB_FILE_SIZE) : 0;
    if (view) UnmapViewOfFile(view);
    CloseHandle(mapping);
    return consistent;
}

CPB_API void cpb_test_lose_device(int fail_new_device) {
    CpbLock lock;
    cpb_frame_lose_device(fail_new_device);
}

CPB_API void cpb_test_hold_frame_lock(void (*during)(void)) {
    CpbLock lock;
    if (during) during();
}

CPB_API int cpb_test_open_texture(const char* name, int* width, int* height, unsigned* first_pixel) {
    if (!name || !width || !height || !first_pixel) return cpb_fail("cpb_test_open_texture without outputs", E_POINTER);
    CpbLock lock;
    Reader r;
    HRESULT hr = cpb_frame_adapter(&r.adapter);
    if (FAILED(hr)) return cpb_fail("cpb_test_open_texture's adapter", hr);
    hr = cpb_create_device(r.adapter, &r.device, &r.context);
    if (FAILED(hr)) return cpb_fail("D3D11CreateDevice (reader)", hr);
    hr = r.device->QueryInterface(__uuidof(ID3D11Device1), (void**)&r.device1);
    if (FAILED(hr)) return cpb_fail("ID3D11Device1", hr);
    wchar_t wide[64];
    cpb_widen(name, wide, 64);
    hr = r.device1->OpenSharedResourceByName(wide, DXGI_SHARED_RESOURCE_READ, __uuidof(ID3D11Texture2D),
                                             (void**)&r.texture);
    if (FAILED(hr)) return cpb_fail("OpenSharedResourceByName", hr);

    D3D11_TEXTURE2D_DESC desc;
    r.texture->GetDesc(&desc);
    *width = (int)desc.Width;
    *height = (int)desc.Height;
    desc.Usage = D3D11_USAGE_STAGING;
    desc.BindFlags = 0;
    desc.CPUAccessFlags = D3D11_CPU_ACCESS_READ;
    desc.MiscFlags = 0;
    hr = r.device->CreateTexture2D(&desc, nullptr, &r.staging);
    if (FAILED(hr)) return cpb_fail("CreateTexture2D (staging)", hr);
    r.context->CopyResource(r.staging, r.texture);
    D3D11_MAPPED_SUBRESOURCE mapped;
    hr = r.context->Map(r.staging, 0, D3D11_MAP_READ, 0, &mapped);
    if (FAILED(hr)) return cpb_fail("Map (staging)", hr);
    *first_pixel = *(const unsigned*)mapped.pData;
    r.context->Unmap(r.staging, 0);
    return 0;
}
