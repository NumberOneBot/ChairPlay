#import "macos_bridge.h"
#import <CoreAudio/CoreAudio.h>
#import <AudioToolbox/AudioToolbox.h>
#include <string.h>

static int get_device_name(AudioDeviceID devId, char *buf, int buf_len) {
    if (devId == kAudioDeviceUnknown || !buf || buf_len <= 0) {
        return -1;
    }
    CFStringRef devName = NULL;
    UInt32 dataSize = sizeof(devName);
    AudioObjectPropertyAddress addr = {
        kAudioObjectPropertyName,
        kAudioObjectPropertyScopeGlobal,
        kAudioObjectPropertyElementMain
    };
    OSStatus status = AudioObjectGetPropertyData(devId, &addr, 0, NULL, &dataSize, &devName);
    if (status != noErr || !devName) {
        return -1;
    }
    Boolean ok = CFStringGetCString(devName, buf, buf_len, kCFStringEncodingUTF8);
    CFRelease(devName);
    return ok ? 0 : -1;
}

int cp_audio_default_devices(char *out_name, int out_len, char *in_name, int in_len) {
    if (out_name && out_len > 0) {
        memset(out_name, 0, (size_t)out_len);
    }
    if (in_name && in_len > 0) {
        memset(in_name, 0, (size_t)in_len);
    }

    AudioDeviceID outDevId = kAudioDeviceUnknown;
    UInt32 size = sizeof(outDevId);
    AudioObjectPropertyAddress outAddr = {
        kAudioHardwarePropertyDefaultOutputDevice,
        kAudioObjectPropertyScopeGlobal,
        kAudioObjectPropertyElementMain
    };
    if (AudioObjectGetPropertyData(kAudioObjectSystemObject, &outAddr, 0, NULL, &size, &outDevId) == noErr) {
        if (out_name && out_len > 0) {
            get_device_name(outDevId, out_name, out_len);
        }
    }

    AudioDeviceID inDevId = kAudioDeviceUnknown;
    size = sizeof(inDevId);
    AudioObjectPropertyAddress inAddr = {
        kAudioHardwarePropertyDefaultInputDevice,
        kAudioObjectPropertyScopeGlobal,
        kAudioObjectPropertyElementMain
    };
    if (AudioObjectGetPropertyData(kAudioObjectSystemObject, &inAddr, 0, NULL, &size, &inDevId) == noErr) {
        if (in_name && in_len > 0) {
            get_device_name(inDevId, in_name, in_len);
        }
    }

    return 0;
}
