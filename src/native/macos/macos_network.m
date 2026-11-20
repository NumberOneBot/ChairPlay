#import "macos_bridge.h"
#import <Foundation/Foundation.h>
#import <CoreWLAN/CoreWLAN.h>
#import <CoreLocation/CoreLocation.h>
#include <string.h>

static void set_err(char *err_buf, int err_len, const char *msg) {
    if (err_buf && err_len > 0) {
        snprintf(err_buf, (size_t)err_len, "%s", msg ? msg : "unknown error");
    }
}

static void trigger_location_auth_if_needed(void) {
    if (![[NSBundle mainBundle] bundleIdentifier]) {
        return; // Running outside a bundle (e.g. CLI/tests)
    }
    static dispatch_once_t onceToken;
    dispatch_once(&onceToken, ^{
        dispatch_async(dispatch_get_main_queue(), ^{
            CLLocationManager *mgr = [[CLLocationManager alloc] init];
            if ([mgr authorizationStatus] == kCLAuthorizationStatusNotDetermined) {
                [mgr requestWhenInUseAuthorization];
            }
        });
    });
}

int cp_net_wifi_info(CpWifiInfo *out_info, char *err_buf, int err_len) {
    @autoreleasepool {
        if (!out_info) {
            set_err(err_buf, err_len, "Invalid argument");
            return -1;
        }
        memset(out_info, 0, sizeof(CpWifiInfo));

        trigger_location_auth_if_needed();

        CWWiFiClient *client = [CWWiFiClient sharedWiFiClient];
        if (!client) {
            set_err(err_buf, err_len, "CoreWLAN client unavailable");
            return -1;
        }

        CWInterface *inf = [client interface];
        if (!inf) {
            set_err(err_buf, err_len, "No Wi-Fi interface found");
            return -1;
        }

        NSString *ifName = [inf interfaceName];
        if (ifName) {
            snprintf(out_info->interface_name, sizeof(out_info->interface_name), "%s", [ifName UTF8String]);
        }

        out_info->power_on = [inf powerOn] ? 1 : 0;
        out_info->service_active = [inf serviceActive] ? 1 : 0;

        CWChannel *channel = [inf wlanChannel];
        if (channel) {
            out_info->channel = (int)[channel channelNumber];
        }

        NSString *bssid = [inf bssid];
        if (bssid) {
            snprintf(out_info->bssid, sizeof(out_info->bssid), "%s", [bssid UTF8String]);
        }

        NSString *ssid = [inf ssid];
        if (ssid && [ssid length] > 0) {
            snprintf(out_info->ssid, sizeof(out_info->ssid), "%s", [ssid UTF8String]);
        } else {
            // Associated / active but no SSID -> hidden or permission denied
            if (channel != nil || out_info->service_active) {
                out_info->wifi_hidden = 1;
            }
        }

        return 0;
    }
}
