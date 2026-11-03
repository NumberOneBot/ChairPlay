// ChairPlay's shared-mode Windows PCM output. Decoder and channel mapping live above this seam.
#define WIN32_LEAN_AND_MEAN
#include <windows.h>
#include <initguid.h>
#include <mmdeviceapi.h>
#include <audioclient.h>
#include <functiondiscoverykeys_devpkey.h>
#include <ks.h>
#include <ksmedia.h>
#include <stdint.h>
#include <new>

#define API extern "C" __declspec(dllexport)
struct Format { uint32_t rate, channels, mask, bits; };

struct Device {
    HRESULT apartment = CoInitializeEx(NULL, COINIT_MULTITHREADED);
    IMMDeviceEnumerator* enumerator = NULL;
    IMMDevice* device = NULL;
    IAudioClient* client = NULL;
    ~Device() {
        if (client) client->Release();
        if (device) device->Release();
        if (enumerator) enumerator->Release();
        if (SUCCEEDED(apartment)) CoUninitialize();
    }
    HRESULT open(const wchar_t* id, int index = -1) {
        if (FAILED(apartment) && apartment != RPC_E_CHANGED_MODE) return apartment;
        HRESULT hr = CoCreateInstance(__uuidof(MMDeviceEnumerator), NULL, CLSCTX_ALL,
                                      __uuidof(IMMDeviceEnumerator), (void**)&enumerator);
        if (FAILED(hr)) return hr;
        if (id && *id) hr = enumerator->GetDevice(id, &device);
        else if (index < 0) hr = enumerator->GetDefaultAudioEndpoint(eRender, eMultimedia, &device);
        else {
            IMMDeviceCollection* devices = NULL;
            hr = enumerator->EnumAudioEndpoints(eRender, DEVICE_STATE_ACTIVE, &devices);
            if (FAILED(hr)) return hr;
            UINT count = 0;
            hr = devices->GetCount(&count);
            if (SUCCEEDED(hr)) hr = (UINT)index < count ? devices->Item((UINT)index, &device) : S_FALSE;
            devices->Release();
        }
        if (hr != S_OK) return hr;
        return device->Activate(__uuidof(IAudioClient), CLSCTX_ALL, NULL, (void**)&client);
    }
    HRESULT format(Format* out) {
        WAVEFORMATEX* wave = NULL;
        HRESULT hr = client->GetMixFormat(&wave);
        if (FAILED(hr)) return hr;
        out->rate = wave->nSamplesPerSec;
        out->channels = wave->nChannels;
        out->bits = wave->wBitsPerSample;
        out->mask = wave->nChannels == 2 ? 3 : 0;
        if (wave->wFormatTag == WAVE_FORMAT_EXTENSIBLE && wave->cbSize >= 22)
            out->mask = ((WAVEFORMATEXTENSIBLE*)wave)->dwChannelMask;
        CoTaskMemFree(wave);
        return S_OK;
    }
};

static HRESULT copyText(wchar_t* out, int capacity, const wchar_t* value) {
    if (!out || capacity < 1 || !value) return E_INVALIDARG;
    const size_t length = wcslen(value);
    if (length >= (size_t)capacity) return HRESULT_FROM_WIN32(ERROR_INSUFFICIENT_BUFFER);
    memcpy(out, value, (length + 1) * sizeof(wchar_t));
    return S_OK;
}

API int cp_audio_device(int index, wchar_t* id, int idCapacity,
                        wchar_t* name, int nameCapacity, Format* format) {
    if (!format) return E_INVALIDARG;
    Device device;
    HRESULT hr = device.open(NULL, index);
    if (hr != S_OK) return hr;
    LPWSTR deviceId = NULL;
    hr = device.device->GetId(&deviceId);
    if (FAILED(hr)) return hr;
    hr = copyText(id, idCapacity, deviceId);
    CoTaskMemFree(deviceId);
    if (FAILED(hr)) return hr;
    IPropertyStore* properties = NULL;
    hr = device.device->OpenPropertyStore(STGM_READ, &properties);
    if (FAILED(hr)) return hr;
    PROPVARIANT value;
    PropVariantInit(&value);
    hr = properties->GetValue(PKEY_Device_FriendlyName, &value);
    if (SUCCEEDED(hr)) hr = value.vt == VT_LPWSTR ? copyText(name, nameCapacity, value.pwszVal) : E_FAIL;
    PropVariantClear(&value);
    properties->Release();
    return FAILED(hr) ? hr : device.format(format);
}

struct Output : Device {
    IAudioRenderClient* render = NULL;
    IAudioClock* clock = NULL;
    HANDLE ready = NULL, cancelled = NULL;
    UINT32 capacity = 0, channels = 0;
    UINT64 frequency = 0;
    bool started = false;
    ~Output() {
        if (started) client->Stop();
        if (clock) clock->Release();
        if (render) render->Release();
        if (ready) CloseHandle(ready);
        if (cancelled) CloseHandle(cancelled);
    }
    HRESULT initialize(const wchar_t* id, int requested, Format* actual) {
        HRESULT hr = open(id);
        if (hr != S_OK) return hr;
        Format mix{};
        hr = format(&mix);
        if (FAILED(hr)) return hr;
        if (mix.rate != 48000) return AUDCLNT_E_UNSUPPORTED_FORMAT;
        const bool surround = mix.channels == 6 && (mix.mask == 0x60f || mix.mask == 0x3f);
        channels = requested == 0 ? (surround ? 6 : 2) : (UINT32)requested;
        if (channels != 2 && channels != 6) return E_INVALIDARG;
        if (channels == 6 && !surround) return AUDCLNT_E_UNSUPPORTED_FORMAT;
        WAVEFORMATEXTENSIBLE wave{};
        wave.Format.wFormatTag = WAVE_FORMAT_EXTENSIBLE;
        wave.Format.nChannels = (WORD)channels;
        wave.Format.nSamplesPerSec = 48000;
        wave.Format.wBitsPerSample = 32;
        wave.Format.nBlockAlign = (WORD)(channels * 4);
        wave.Format.nAvgBytesPerSec = 48000 * wave.Format.nBlockAlign;
        wave.Format.cbSize = 22;
        wave.Samples.wValidBitsPerSample = 32;
        wave.dwChannelMask = channels == 6 ? mix.mask : 3;
        wave.SubFormat = KSDATAFORMAT_SUBTYPE_IEEE_FLOAT;
        DWORD flags = AUDCLNT_STREAMFLAGS_EVENTCALLBACK | AUDCLNT_STREAMFLAGS_NOPERSIST;
        if (channels == 2) flags |= AUDCLNT_STREAMFLAGS_AUTOCONVERTPCM;
        else {
            WAVEFORMATEX* closest = NULL;
            hr = client->IsFormatSupported(AUDCLNT_SHAREMODE_SHARED, &wave.Format, &closest);
            if (closest) CoTaskMemFree(closest);
            if (hr != S_OK) return AUDCLNT_E_UNSUPPORTED_FORMAT;
        }
        hr = client->Initialize(AUDCLNT_SHAREMODE_SHARED, flags, 1400000, 0, &wave.Format, NULL);
        if (FAILED(hr)) return hr;
        ready = CreateEventW(NULL, FALSE, FALSE, NULL);
        cancelled = CreateEventW(NULL, TRUE, FALSE, NULL);
        if (!ready || !cancelled) return HRESULT_FROM_WIN32(GetLastError());
        hr = client->SetEventHandle(ready);
        if (FAILED(hr)) return hr;
        hr = client->GetBufferSize(&capacity);
        if (FAILED(hr)) return hr;
        hr = client->GetService(__uuidof(IAudioRenderClient), (void**)&render);
        if (FAILED(hr)) return hr;
        hr = client->GetService(__uuidof(IAudioClock), (void**)&clock);
        if (FAILED(hr)) return hr;
        hr = clock->GetFrequency(&frequency);
        if (FAILED(hr) || frequency == 0) return FAILED(hr) ? hr : E_FAIL;
        *actual = {48000, channels, wave.dwChannelMask, 32};
        return S_OK;
    }
};

API int cp_audio_open(const wchar_t* id, int channels, Output** out, Format* format) {
    if (!out || !format) return E_INVALIDARG;
    *out = NULL;
    Output* output = new(std::nothrow) Output;
    if (!output) return E_OUTOFMEMORY;
    const HRESULT hr = output->initialize(id, channels, format);
    if (hr != S_OK) { delete output; return hr; }
    *out = output;
    return S_OK;
}

API int cp_audio_write(Output* out, const int16_t* samples, uint32_t frames) {
    if (!out || !samples || frames > 96000) return E_INVALIDARG;
    uint32_t at = 0;
    const ULONGLONG deadline = GetTickCount64() + 5000;
    while (at < frames) {
        if (WaitForSingleObject(out->cancelled, 0) == WAIT_OBJECT_0) return E_ABORT;
        UINT32 padding = 0;
        HRESULT hr = out->client->GetCurrentPadding(&padding);
        if (FAILED(hr)) return hr;
        if (padding > out->capacity) return E_FAIL;
        UINT32 count = out->capacity - padding;
        if (count == 0) {
            HANDLE handles[] = {out->cancelled, out->ready};
            DWORD result = WaitForMultipleObjects(2, handles, FALSE, 20);
            if (result == WAIT_OBJECT_0) return E_ABORT;
            if (result == WAIT_FAILED) return HRESULT_FROM_WIN32(GetLastError());
            if (GetTickCount64() >= deadline) return HRESULT_FROM_WIN32(ERROR_TIMEOUT);
            continue;
        }
        if (count > frames - at) count = frames - at;
        BYTE* buffer = NULL;
        hr = out->render->GetBuffer(count, &buffer);
        if (FAILED(hr)) return hr;
        float* destination = (float*)buffer;
        for (UINT32 i = 0; i < count * out->channels; ++i)
            destination[i] = samples[(size_t)at * out->channels + i] / 32768.0f;
        hr = out->render->ReleaseBuffer(count, 0);
        if (FAILED(hr)) return hr;
        if (!out->started) {
            hr = out->client->Start();
            if (FAILED(hr)) return hr;
            out->started = true;
        }
        at += count;
    }
    return S_OK;
}

API int cp_audio_position(Output* out, uint64_t* frames) {
    if (!out || !frames) return E_INVALIDARG;
    UINT64 position = 0;
    HRESULT hr = out->clock->GetPosition(&position, NULL);
    if (FAILED(hr)) return hr;
    *frames = (position / out->frequency) * 48000 + ((position % out->frequency) * 48000) / out->frequency;
    return S_OK;
}
API void cp_audio_cancel(Output* out) { if (out) SetEvent(out->cancelled); }
API void cp_audio_close(Output* out) { delete out; }
