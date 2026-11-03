// Windows' Task Dialog, kept off the GLFW thread so a notice does not pause CarPlay.
#include <windows.h>
#include <commctrl.h>

using ShowTaskDialog = HRESULT(WINAPI *)(const TASKDIALOGCONFIG *, int *, int *, BOOL *);

static HRESULT CALLBACK foreground(HWND window, UINT notification, WPARAM, LPARAM, LONG_PTR) {
    if (notification == TDN_CREATED) SetForegroundWindow(window);
    return S_OK;
}

extern "C" __declspec(dllexport) int cpw_update_dialog(const wchar_t *heading, const wchar_t *text, int offer_open) {
    // java.exe and the jpackage launcher need not select Common Controls v6. Activate our DLL's manifest
    // on this worker thread before loading comctl32 and keep it active until the dialog closes.
    HMODULE module = nullptr;
    if (!GetModuleHandleExW(GET_MODULE_HANDLE_EX_FLAG_FROM_ADDRESS | GET_MODULE_HANDLE_EX_FLAG_UNCHANGED_REFCOUNT,
            reinterpret_cast<LPCWSTR>(&cpw_update_dialog), &module)) return HRESULT_FROM_WIN32(GetLastError());
    ACTCTXW context{};
    context.cbSize = sizeof(context);
    context.dwFlags = ACTCTX_FLAG_HMODULE_VALID | ACTCTX_FLAG_RESOURCE_NAME_VALID;
    context.hModule = module;
    context.lpResourceName = MAKEINTRESOURCEW(2);
    HANDLE activation = CreateActCtxW(&context);
    if (activation == INVALID_HANDLE_VALUE) return HRESULT_FROM_WIN32(GetLastError());
    ULONG_PTR cookie = 0;
    if (!ActivateActCtx(activation, &cookie)) {
        auto error = HRESULT_FROM_WIN32(GetLastError());
        ReleaseActCtx(activation);
        return error;
    }
    HMODULE controls = LoadLibraryW(L"comctl32.dll");
    auto show = controls ? reinterpret_cast<ShowTaskDialog>(GetProcAddress(controls, "TaskDialogIndirect")) : nullptr;
    int pressed = IDCANCEL;
    HRESULT result = E_FAIL;
    if (show) {
        TASKDIALOG_BUTTON open{100, L"Open"};
        TASKDIALOGCONFIG dialog{};
        dialog.cbSize = sizeof(dialog);
        dialog.dwFlags = TDF_ALLOW_DIALOG_CANCELLATION | TDF_SIZE_TO_CONTENT;
        dialog.pszWindowTitle = L"ChairPlay";
        dialog.pszMainInstruction = heading;
        dialog.pszContent = text;
        dialog.pszMainIcon = TD_INFORMATION_ICON;
        dialog.dwCommonButtons = offer_open ? TDCBF_CANCEL_BUTTON : TDCBF_OK_BUTTON;
        dialog.cButtons = offer_open ? 1 : 0;
        dialog.pButtons = offer_open ? &open : nullptr;
        dialog.nDefaultButton = offer_open ? 100 : IDOK;
        dialog.pfCallback = foreground;
        result = show(&dialog, &pressed, nullptr, nullptr);
    }
    if (controls) FreeLibrary(controls);
    DeactivateActCtx(0, cookie);
    ReleaseActCtx(activation);
    return FAILED(result) ? result : pressed;
}
