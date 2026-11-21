// A Windows toast for an unpackaged app: the app's ID is registered under HKCU (its name and icon), then Windows'
// toast notifier shows the XML. The WinRT interfaces are declared here from the Windows SDK's headers
// (windows.ui.notifications.h, windows.data.xml.dom.h), which zig's MinGW lacks; combase.dll is loaded at run time.
#include <windows.h>
#include <inspectable.h>
#include <roapi.h>
#include <winstring.h>
#include <initializer_list>

namespace {

struct IXmlDocumentIO : IInspectable {
    virtual HRESULT STDMETHODCALLTYPE LoadXml(HSTRING xml) = 0;
};
struct IToastNotifier : IInspectable {
    virtual HRESULT STDMETHODCALLTYPE Show(IInspectable *notification) = 0;
};
struct IToastNotificationFactory : IInspectable {
    virtual HRESULT STDMETHODCALLTYPE CreateToastNotification(IInspectable *content, IInspectable **notification) = 0;
};
struct IToastNotificationManagerStatics : IInspectable {
    virtual HRESULT STDMETHODCALLTYPE CreateToastNotifier(IToastNotifier **notifier) = 0;
    virtual HRESULT STDMETHODCALLTYPE CreateToastNotifierWithId(HSTRING id, IToastNotifier **notifier) = 0;
};

const IID IID_XmlDocumentIO = {0x6cd0e74e, 0xee65, 0x4489, {0x9e, 0xbf, 0xca, 0x43, 0xe8, 0x7b, 0xa6, 0x37}};
const IID IID_XmlDocument = {0xf7f3a506, 0x1e87, 0x42d6, {0xbc, 0xfb, 0xb8, 0xc8, 0x09, 0xfa, 0x54, 0x94}};
const IID IID_ToastFactory = {0x04124b20, 0x82c6, 0x4229, {0xb1, 0x09, 0xfd, 0x9e, 0xd4, 0x66, 0x2b, 0x53}};
const IID IID_ToastManager = {0x50ac103f, 0xd235, 0x4598, {0xbb, 0xef, 0x98, 0xfe, 0x4d, 0x1a, 0x3a, 0xd4}};

using Initialize = HRESULT(WINAPI *)(RO_INIT_TYPE);
using Uninitialize = void(WINAPI *)();
using Activate = HRESULT(WINAPI *)(HSTRING, IInspectable **);
using Factory = HRESULT(WINAPI *)(HSTRING, REFIID, void **);
using CreateString = HRESULT(WINAPI *)(PCWSTR, UINT32, HSTRING *);
using DeleteString = HRESULT(WINAPI *)(HSTRING);

template <typename T> void release(T *&p) {
    if (p) p->Release();
    p = nullptr;
}

void registerApp(const wchar_t *id, const wchar_t *name, const wchar_t *icon) {
    wchar_t key[256] = L"Software\\Classes\\AppUserModelId\\";
    lstrcatW(key, id);
    RegSetKeyValueW(HKEY_CURRENT_USER, key, L"DisplayName", REG_SZ, name, (lstrlenW(name) + 1) * sizeof(wchar_t));
    if (icon) RegSetKeyValueW(HKEY_CURRENT_USER, key, L"IconUri", REG_SZ, icon, (lstrlenW(icon) + 1) * sizeof(wchar_t));
}

} // namespace

extern "C" __declspec(dllexport) int cpw_toast(const wchar_t *app_id, const wchar_t *name, const wchar_t *icon,
        const wchar_t *xml) {
    if (lstrlenW(app_id) > 128) return E_INVALIDARG;
    registerApp(app_id, name, icon);
    HMODULE combase = LoadLibraryW(L"combase.dll");
    if (!combase) return HRESULT_FROM_WIN32(GetLastError());
    auto init = reinterpret_cast<Initialize>(GetProcAddress(combase, "RoInitialize"));
    auto uninit = reinterpret_cast<Uninitialize>(GetProcAddress(combase, "RoUninitialize"));
    auto activate = reinterpret_cast<Activate>(GetProcAddress(combase, "RoActivateInstance"));
    auto factory = reinterpret_cast<Factory>(GetProcAddress(combase, "RoGetActivationFactory"));
    auto create = reinterpret_cast<CreateString>(GetProcAddress(combase, "WindowsCreateString"));
    auto drop = reinterpret_cast<DeleteString>(GetProcAddress(combase, "WindowsDeleteString"));
    if (!init || !uninit || !activate || !factory || !create || !drop) {
        FreeLibrary(combase);
        return E_NOINTERFACE;
    }
    // A thread of its own, from Kotlin: this call's apartment is the only one it has.
    HRESULT started = init(RO_INIT_MULTITHREADED);
    auto string = [&](const wchar_t *text) {
        HSTRING s = nullptr;
        create(text, static_cast<UINT32>(lstrlenW(text)), &s);
        return s;
    };
    HSTRING xmlClass = string(L"Windows.Data.Xml.Dom.XmlDocument");
    HSTRING toastClass = string(L"Windows.UI.Notifications.ToastNotification");
    HSTRING managerClass = string(L"Windows.UI.Notifications.ToastNotificationManager");
    HSTRING content = string(xml);
    HSTRING id = string(app_id);
    IInspectable *document = nullptr, *documentXml = nullptr, *toast = nullptr;
    IXmlDocumentIO *loader = nullptr;
    IToastNotificationFactory *toasts = nullptr;
    IToastNotificationManagerStatics *manager = nullptr;
    IToastNotifier *notifier = nullptr;
    HRESULT result = activate(xmlClass, &document);
    if (SUCCEEDED(result)) result = document->QueryInterface(IID_XmlDocumentIO, reinterpret_cast<void **>(&loader));
    if (SUCCEEDED(result)) result = loader->LoadXml(content);
    if (SUCCEEDED(result)) result = document->QueryInterface(IID_XmlDocument, reinterpret_cast<void **>(&documentXml));
    if (SUCCEEDED(result)) result = factory(toastClass, IID_ToastFactory, reinterpret_cast<void **>(&toasts));
    if (SUCCEEDED(result)) result = toasts->CreateToastNotification(documentXml, &toast);
    if (SUCCEEDED(result)) result = factory(managerClass, IID_ToastManager, reinterpret_cast<void **>(&manager));
    if (SUCCEEDED(result)) result = manager->CreateToastNotifierWithId(id, &notifier);
    if (SUCCEEDED(result)) result = notifier->Show(toast);
    release(notifier);
    release(manager);
    release(toast);
    release(toasts);
    release(documentXml);
    release(loader);
    release(document);
    for (HSTRING s : {xmlClass, toastClass, managerClass, content, id}) drop(s);
    if (SUCCEEDED(started)) uninit();
    FreeLibrary(combase);
    return result;
}
