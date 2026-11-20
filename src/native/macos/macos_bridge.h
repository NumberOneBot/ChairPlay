#ifndef MACOS_BRIDGE_H
#define MACOS_BRIDGE_H

#include <stdint.h>

#ifdef __cplusplus
extern "C" {
#endif

typedef struct {
    char name[256];
    char address[32];
    uint32_t class_of_device;
} CpBtDevice;

typedef struct {
    char interface_name[64];
    char ssid[128];
    char bssid[32];
    int channel;
    int power_on;
    int service_active;
    int wifi_hidden;
} CpWifiInfo;

// Bluetooth
int cp_bt_radio_address(char *out_buf, int buf_len, char *err_buf, int err_len);
int cp_bt_paired_devices(CpBtDevice *out_devices, int max_devices, char *err_buf, int err_len);
int64_t cp_bt_connect_rfcomm(const char *address, const uint8_t *uuid16, char *err_buf, int err_len);
int cp_bt_read(int64_t handle, uint8_t *buf, int len, char *err_buf, int err_len);
int cp_bt_write(int64_t handle, const uint8_t *buf, int len, char *err_buf, int err_len);
void cp_bt_close(int64_t handle);

// Network
int cp_net_wifi_info(CpWifiInfo *out_info, char *err_buf, int err_len);

// Shell
int cp_shell_apps_dark(void);
void cp_shell_show_error(const char *title, const char *message);

// Window
void cp_win_paint_titlebar(int64_t ns_window, int dialog);
int64_t cp_win_watch_close(int64_t ns_window);
int cp_win_user_closed(int64_t watch);
void cp_win_unwatch_close(int64_t watch);
void cp_win_drag_by_picture(int64_t ns_window);
void cp_win_flash(int64_t ns_window, int on);

// Audio
int cp_audio_default_devices(char *out_name, int out_len, char *in_name, int in_len);

#ifdef __cplusplus
}
#endif

#endif // MACOS_BRIDGE_H
