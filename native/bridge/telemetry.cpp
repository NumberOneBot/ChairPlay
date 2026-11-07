// chairplay_bridge.dll, telemetry: reads the file Local\ChairPlay.Telemetry.v1 (bridge_layout.h) a game's source writes.
// Kept apart from the frame bridge's state: both can be open at once.
#include <windows.h>

#include "bridge.h"
#include "bridge_layout.h"
#include "frame_internal.h"
#include "seqlock.h"

namespace {

HANDLE mapping = nullptr;
const void* view = nullptr;

void close_locked() {
    if (view) UnmapViewOfFile(view);
    if (mapping) CloseHandle(mapping);
    view = nullptr;
    mapping = nullptr;
}

}  // namespace

// Creates the file (zeroed) when no game has, else opens the one that exists: it is never cleared here.
CPB_API int cpb_telemetry_open(void) {
    CpbLock lock;
    if (view) return 0;
    mapping = CreateFileMappingW(INVALID_HANDLE_VALUE, nullptr, PAGE_READWRITE, 0, CPB_FILE_SIZE,
                                 CPB_TELEMETRY_MAPPING);
    if (!mapping) return cpb_fail("CreateFileMappingW (telemetry)", HRESULT_FROM_WIN32(GetLastError()));
    view = MapViewOfFile(mapping, FILE_MAP_READ, 0, 0, CPB_FILE_SIZE);
    if (!view) {
        HRESULT hr = HRESULT_FROM_WIN32(GetLastError());
        close_locked();
        return cpb_fail("MapViewOfFile (telemetry)", hr);
    }
    return 0;
}

CPB_API int cpb_telemetry_read(void* out256) {
    CpbLock lock;
    if (!view || !out256) return 0;
    return cpb_seq_read(view, out256, CPB_FILE_SIZE);
}

CPB_API void cpb_telemetry_close(void) {
    CpbLock lock;
    close_locked();
}

// Test hook: writes a record as a game's source does (the seqlock around everything but seq itself).
CPB_API int cpb_test_write_telemetry(const void* in256) {
    if (!in256) return cpb_fail("cpb_test_write_telemetry without a record", E_POINTER);
    HANDLE file = CreateFileMappingW(INVALID_HANDLE_VALUE, nullptr, PAGE_READWRITE, 0, CPB_FILE_SIZE,
                                     CPB_TELEMETRY_MAPPING);
    if (!file) return cpb_fail("CreateFileMappingW (telemetry writer)", HRESULT_FROM_WIN32(GetLastError()));
    char* record = (char*)MapViewOfFile(file, FILE_MAP_WRITE, 0, 0, CPB_FILE_SIZE);
    if (!record) {
        HRESULT hr = HRESULT_FROM_WIN32(GetLastError());
        CloseHandle(file);
        return cpb_fail("MapViewOfFile (telemetry writer)", hr);
    }
    const char* from = (const char*)in256;
    cpb_seq_begin(record);
    for (size_t i = 0; i < CPB_FILE_SIZE; i++) {
        if (i < CPB_SEQ_OFFSET || i >= CPB_SEQ_OFFSET + sizeof(LONG)) record[i] = from[i];
    }
    cpb_seq_end(record);
    UnmapViewOfFile(record);
    CloseHandle(file);
    return 0;
}
