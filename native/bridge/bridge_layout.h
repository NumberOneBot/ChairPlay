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
#define CPB_TELEMETRY_POSITION 256u
#define CPB_TELEMETRY_CONTROLS 512u
#define CPB_TELEMETRY_DISPLAY 1024u
#define CPB_TELEMETRY_THEME 2048u
#define CPB_TELEMETRY_DARK 4096u
#define CPB_TELEMETRY_TOUCH 8192u

/* cpb_telemetry.buttons: held while set. The first seven hold CarPlay's knob, the rest act as they turn on. */
#define CPB_BUTTON_LEFT 1u
#define CPB_BUTTON_RIGHT 2u
#define CPB_BUTTON_UP 4u
#define CPB_BUTTON_DOWN 8u
#define CPB_BUTTON_SELECT 16u
#define CPB_BUTTON_BACK 32u
#define CPB_BUTTON_HOME 64u
#define CPB_BUTTON_MUTE 128u
#define CPB_BUTTON_PREVIOUS 256u
#define CPB_BUTTON_PLAY_PAUSE 512u
#define CPB_BUTTON_NEXT 1024u
#define CPB_BUTTON_ANSWER 2048u
#define CPB_BUTTON_END_CALL 4096u
#define CPB_BUTTON_SIRI 8192u
#define CPB_BUTTON_CARPLAY 16384u

/* cpb_telemetry.display_options, read in a record of 144 bytes or more: bit 0 the driver side, bits 1-2 the dock. */
#define CPB_DISPLAY_RIGHT_HAND_DRIVE 1u
#define CPB_DISPLAY_DOCK_AUTO 0u
#define CPB_DISPLAY_DOCK_DRIVER 2u
#define CPB_DISPLAY_DOCK_BOTTOM 4u

/* cpb_status.screen */
#define CPB_SCREEN_CARPLAY 0u
#define CPB_SCREEN_CAR 1u

struct cpb_status {
    uint32_t magic;             /*   0 */
    uint16_t version;           /*   4 */
    uint16_t size;              /*   6: 160 */
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
    uint32_t screen;            /* 136: whose screen to show: 0 CarPlay's (the texture), 1 the car's own */
    uint32_t ui_port;           /* 140: ChairPlay's local API, http://127.0.0.1:<ui_port>/api/ */
    char app_version[16];       /* 144: ChairPlay's version, X.Y.Z; empty for a development build */
    uint8_t reserved[96];       /* 160 */
};

struct cpb_telemetry {
    uint32_t magic;             /*   0 */
    uint16_t version;           /*   4 */
    uint16_t size;              /*   6: 156 */
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
    uint32_t buttons;           /* 112: CPB_BUTTON_* held now */
    int32_t knob_steps;         /* 116: the knob's turns so far, + forward */
    int32_t zoom_steps;         /* 120: the AltScreen map's zoom steps so far, + in */
    int32_t volume_steps;       /* 124: ChairPlay's volume steps so far, + up */
    uint32_t display_width;     /* 128: the car's display: CarPlay's picture width, px, even */
    uint32_t display_height;    /* 132: its height, px, even */
    uint32_t display_width_mm;  /* 136: the display's width, mm, for CarPlay's scale */
    uint32_t display_options;   /* 140: CPB_DISPLAY_RIGHT_HAND_DRIVE, one CPB_DISPLAY_DOCK_* */
    float touch_x;              /* 144: the finger, 0 to 1 of CarPlay's picture from its left */
    float touch_y;              /* 148: 0 to 1 from its top */
    uint32_t touch_down;        /* 152: 1 while the finger is on the screen, 0 lifted */
    uint8_t reserved[100];      /* 156 */
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
static_assert(offsetof(struct cpb_status, screen) == 136, "screen");
static_assert(offsetof(struct cpb_status, ui_port) == 140, "ui_port");
static_assert(offsetof(struct cpb_status, app_version) == 144, "app_version");
static_assert(offsetof(struct cpb_status, reserved) == 160, "status reserved");

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
static_assert(offsetof(struct cpb_telemetry, buttons) == 112, "buttons");
static_assert(offsetof(struct cpb_telemetry, knob_steps) == 116, "knob_steps");
static_assert(offsetof(struct cpb_telemetry, zoom_steps) == 120, "zoom_steps");
static_assert(offsetof(struct cpb_telemetry, volume_steps) == 124, "volume_steps");
static_assert(offsetof(struct cpb_telemetry, display_width) == 128, "display_width");
static_assert(offsetof(struct cpb_telemetry, display_height) == 132, "display_height");
static_assert(offsetof(struct cpb_telemetry, display_width_mm) == 136, "display_width_mm");
static_assert(offsetof(struct cpb_telemetry, display_options) == 140, "display_options");
static_assert(offsetof(struct cpb_telemetry, touch_x) == 144, "touch_x");
static_assert(offsetof(struct cpb_telemetry, touch_y) == 148, "touch_y");
static_assert(offsetof(struct cpb_telemetry, touch_down) == 152, "touch_down");
static_assert(offsetof(struct cpb_telemetry, reserved) == 156, "telemetry reserved");

#endif
