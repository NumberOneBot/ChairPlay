/* Byte layouts of the ChairPlay game bridge files (version 1), little-endian.
   Local\ChairPlay.Bridge.v1 is written by ChairPlay; Local\ChairPlay.Telemetry.v1 by a game's source. */
#ifndef CHAIRPLAY_BRIDGE_LAYOUT_H
#define CHAIRPLAY_BRIDGE_LAYOUT_H

#include <assert.h>
#include <stddef.h>
#include <stdint.h>

#define CPB_FILE_SIZE 256u
#define CPB_STATUS_MAGIC 0x52425043u
#define CPB_TELEMETRY_MAGIC 0x54435043u
#define CPB_VERSION 1u
#define CPB_DXGI_FORMAT_B8G8R8A8_UNORM 87u

#define CPB_TELEMETRY_RPM 1u
#define CPB_TELEMETRY_GEAR 2u
#define CPB_TELEMETRY_PAUSED 4u
#define CPB_TELEMETRY_REDLINE 8u
#define CPB_TELEMETRY_THROTTLE 16u
#define CPB_TELEMETRY_BRAKE 32u
#define CPB_TELEMETRY_STEER 64u
#define CPB_TELEMETRY_TOP_SPEED 128u

struct cpb_status {
    uint32_t magic;             /*   0 */
    uint16_t version;           /*   4 */
    uint16_t size;              /*   6: 136 */
    uint32_t seq;               /*   8: odd while being written */
    uint32_t state;             /*  12: 0 none, 1 waiting, 2 flowing, 3 frozen */
    uint32_t generation;        /*  16 */
    uint32_t width;             /*  20 */
    uint32_t height;            /*  24 */
    int32_t safe_x;             /*  28 */
    int32_t safe_y;             /*  32 */
    int32_t safe_w;             /*  36 */
    int32_t safe_h;             /*  40 */
    uint32_t dxgi_format;       /*  44 */
    uint32_t adapter_luid_low;  /*  48 */
    int32_t adapter_luid_high;  /*  52 */
    uint64_t frame_counter;     /*  56 */
    uint32_t stream_fps;        /*  64 */
    uint32_t producer_pid;      /*  68 */
    char texture_name[64];      /*  72 */
    uint8_t reserved[120];      /* 136 */
};

struct cpb_telemetry {
    uint32_t magic;             /*   0 */
    uint16_t version;           /*   4 */
    uint16_t size;              /*   6: 112 */
    uint32_t seq;               /*   8 */
    uint32_t counter;           /*  12 */
    uint32_t flags;             /*  16 */
    int32_t gear;               /*  20: -1 R, 0 N, 1... */
    double speed;               /*  24: m/s, negative = reverse */
    double heading;             /*  32: degrees clockwise from true north */
    double latitude;            /*  40 */
    double longitude;           /*  48 */
    float rpm;                  /*  56 */
    float redline;              /*  60: rpm where the red zone starts */
    char source[32];            /*  64 */
    float throttle;             /*  96: 0 released to 1 pressed */
    float brake;                /* 100: 0 released to 1 pressed */
    float steer_degrees;        /* 104: steering wheel angle, negative = left, past a turn too */
    float top_speed;            /* 108: m/s, the speedometer's scale */
    uint8_t reserved[144];      /* 112 */
};

static_assert(sizeof(struct cpb_status) == 256, "status size");
static_assert(offsetof(struct cpb_status, magic) == 0, "magic");
static_assert(offsetof(struct cpb_status, version) == 4, "version");
static_assert(offsetof(struct cpb_status, size) == 6, "size");
static_assert(offsetof(struct cpb_status, seq) == 8, "seq");
static_assert(offsetof(struct cpb_status, state) == 12, "state");
static_assert(offsetof(struct cpb_status, generation) == 16, "generation");
static_assert(offsetof(struct cpb_status, width) == 20, "width");
static_assert(offsetof(struct cpb_status, height) == 24, "height");
static_assert(offsetof(struct cpb_status, safe_x) == 28, "safe_x");
static_assert(offsetof(struct cpb_status, safe_y) == 32, "safe_y");
static_assert(offsetof(struct cpb_status, safe_w) == 36, "safe_w");
static_assert(offsetof(struct cpb_status, safe_h) == 40, "safe_h");
static_assert(offsetof(struct cpb_status, dxgi_format) == 44, "dxgi_format");
static_assert(offsetof(struct cpb_status, adapter_luid_low) == 48, "adapter_luid_low");
static_assert(offsetof(struct cpb_status, adapter_luid_high) == 52, "adapter_luid_high");
static_assert(offsetof(struct cpb_status, frame_counter) == 56, "frame_counter");
static_assert(offsetof(struct cpb_status, stream_fps) == 64, "stream_fps");
static_assert(offsetof(struct cpb_status, producer_pid) == 68, "producer_pid");
static_assert(offsetof(struct cpb_status, texture_name) == 72, "texture_name");
static_assert(offsetof(struct cpb_status, reserved) == 136, "status reserved");

static_assert(sizeof(struct cpb_telemetry) == 256, "telemetry size");
static_assert(offsetof(struct cpb_telemetry, magic) == 0, "magic");
static_assert(offsetof(struct cpb_telemetry, version) == 4, "version");
static_assert(offsetof(struct cpb_telemetry, size) == 6, "size");
static_assert(offsetof(struct cpb_telemetry, seq) == 8, "seq");
static_assert(offsetof(struct cpb_telemetry, counter) == 12, "counter");
static_assert(offsetof(struct cpb_telemetry, flags) == 16, "flags");
static_assert(offsetof(struct cpb_telemetry, gear) == 20, "gear");
static_assert(offsetof(struct cpb_telemetry, speed) == 24, "speed");
static_assert(offsetof(struct cpb_telemetry, heading) == 32, "heading");
static_assert(offsetof(struct cpb_telemetry, latitude) == 40, "latitude");
static_assert(offsetof(struct cpb_telemetry, longitude) == 48, "longitude");
static_assert(offsetof(struct cpb_telemetry, rpm) == 56, "rpm");
static_assert(offsetof(struct cpb_telemetry, redline) == 60, "redline");
static_assert(offsetof(struct cpb_telemetry, source) == 64, "source");
static_assert(offsetof(struct cpb_telemetry, throttle) == 96, "throttle");
static_assert(offsetof(struct cpb_telemetry, brake) == 100, "brake");
static_assert(offsetof(struct cpb_telemetry, steer_degrees) == 104, "steer_degrees");
static_assert(offsetof(struct cpb_telemetry, top_speed) == 108, "top_speed");
static_assert(offsetof(struct cpb_telemetry, reserved) == 112, "telemetry reserved");

#endif
