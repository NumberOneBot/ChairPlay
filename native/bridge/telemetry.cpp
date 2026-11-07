// chairplay_bridge.dll, telemetry: stubs returning -1 until Local\ChairPlay.Telemetry.v1 is read here.
#include "bridge.h"

CPB_API int cpb_telemetry_open(void) { return -1; }

CPB_API int cpb_telemetry_read(void*) { return -1; }

CPB_API void cpb_telemetry_close(void) {}
