// Windows' Open dialog (IFileOpenDialog), kept off the GLFW thread like update_dialog.cc.
#include <windows.h>
#include <shobjidl.h>

// 1 chosen (path written), 0 cancelled, < 0 HRESULT
extern "C" __declspec(dllexport) int cpw_open_file(const wchar_t *title, const wchar_t *filter_name,
        const wchar_t *filter_spec, wchar_t *path, int path_chars) {
    HRESULT init = CoInitializeEx(nullptr, COINIT_APARTMENTTHREADED);
    // RPC_E_CHANGED_MODE: the thread is already a multithreaded apartment, which the dialog works in too.
    if (FAILED(init) && init != RPC_E_CHANGED_MODE) return init;
    HRESULT result = E_FAIL;
    int chosen = 0;
    IFileOpenDialog *dialog = nullptr;
    result = CoCreateInstance(CLSID_FileOpenDialog, nullptr, CLSCTX_INPROC_SERVER, IID_PPV_ARGS(&dialog));
    if (SUCCEEDED(result)) {
        COMDLG_FILTERSPEC filter{filter_name, filter_spec};
        DWORD options = 0;
        dialog->GetOptions(&options);
        dialog->SetOptions(options | FOS_FORCEFILESYSTEM | FOS_FILEMUSTEXIST | FOS_PATHMUSTEXIST);
        dialog->SetTitle(title);
        result = dialog->SetFileTypes(1, &filter);
        if (SUCCEEDED(result)) dialog->SetFileTypeIndex(1);
        if (SUCCEEDED(result)) result = dialog->Show(nullptr);
        if (result == HRESULT_FROM_WIN32(ERROR_CANCELLED)) {
            result = S_OK;
        } else if (SUCCEEDED(result)) {
            IShellItem *item = nullptr;
            result = dialog->GetResult(&item);
            if (SUCCEEDED(result)) {
                PWSTR name = nullptr;
                result = item->GetDisplayName(SIGDN_FILESYSPATH, &name);
                if (SUCCEEDED(result)) {
                    if (wcslen(name) < static_cast<size_t>(path_chars)) {
                        wcscpy(path, name);
                        chosen = 1;
                    } else {
                        result = HRESULT_FROM_WIN32(ERROR_INSUFFICIENT_BUFFER);
                    }
                    CoTaskMemFree(name);
                }
                item->Release();
            }
        }
        dialog->Release();
    }
    if (SUCCEEDED(init)) CoUninitialize();
    return FAILED(result) ? result : chosen;
}
