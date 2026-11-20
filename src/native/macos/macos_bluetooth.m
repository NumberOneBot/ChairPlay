#import "macos_bridge.h"
#import <Foundation/Foundation.h>
#import <IOBluetooth/IOBluetooth.h>
#include <pthread.h>
#include <string.h>

#define RING_BUF_CAPACITY (128 * 1024)

@interface CpRfcommDelegate : NSObject <IOBluetoothRFCOMMChannelDelegate> {
@public
    uint8_t ring_buf[RING_BUF_CAPACITY];
    size_t head;
    size_t tail;
    size_t count;
    int closed;
    pthread_mutex_t mutex;
    pthread_cond_t cond;
    IOBluetoothRFCOMMChannel *channel;
}
@end

@implementation CpRfcommDelegate
- (instancetype)init {
    self = [super init];
    if (self) {
        head = tail = count = 0;
        closed = 0;
        pthread_mutex_init(&mutex, NULL);
        pthread_cond_init(&cond, NULL);
    }
    return self;
}

- (void)dealloc {
    pthread_mutex_destroy(&mutex);
    pthread_cond_destroy(&cond);
}

- (void)rfcommChannelData:(IOBluetoothRFCOMMChannel *)rfcommChannel data:(void *)dataPointer length:(size_t)dataLength {
    (void)rfcommChannel;
    if (dataLength == 0 || !dataPointer) return;
    pthread_mutex_lock(&mutex);
    const uint8_t *bytes = (const uint8_t *)dataPointer;
    for (size_t i = 0; i < dataLength; i++) {
        if (count < RING_BUF_CAPACITY) {
            ring_buf[tail] = bytes[i];
            tail = (tail + 1) % RING_BUF_CAPACITY;
            count++;
        }
    }
    pthread_cond_broadcast(&cond);
    pthread_mutex_unlock(&mutex);
}

- (void)rfcommChannelClosed:(IOBluetoothRFCOMMChannel *)rfcommChannel {
    (void)rfcommChannel;
    pthread_mutex_lock(&mutex);
    closed = 1;
    pthread_cond_broadcast(&cond);
    pthread_mutex_unlock(&mutex);
}
@end

static void ensure_run_loop(void) {
    static dispatch_once_t onceToken;
    dispatch_once(&onceToken, ^{
        NSThread *thread = [[NSThread alloc] initWithBlock:^{
            NSRunLoop *runLoop = [NSRunLoop currentRunLoop];
            [runLoop addPort:[NSMachPort port] forMode:NSDefaultRunLoopMode];
            while (1) {
                @autoreleasepool {
                    [runLoop runMode:NSDefaultRunLoopMode beforeDate:[NSDate distantFuture]];
                }
            }
        }];
        [thread setName:@"io.github.numberonebot.chairplay.bluetooth"];
        [thread start];
    });
}

static void set_err(char *err_buf, int err_len, const char *msg) {
    if (err_buf && err_len > 0) {
        snprintf(err_buf, (size_t)err_len, "%s", msg ? msg : "unknown error");
    }
}

int cp_bt_radio_address(char *out_buf, int buf_len, char *err_buf, int err_len) {
    @autoreleasepool {
        ensure_run_loop();
        IOBluetoothHostController *hc = [IOBluetoothHostController defaultController];
        if (!hc) {
            set_err(err_buf, err_len, "No default Bluetooth host controller");
            return -1;
        }
        NSString *addr = [hc addressAsString];
        if (!addr || [addr length] == 0) {
            set_err(err_buf, err_len, "Host controller address unavailable");
            return -1;
        }
        snprintf(out_buf, (size_t)buf_len, "%s", [addr UTF8String]);
        return 0;
    }
}

int cp_bt_paired_devices(CpBtDevice *out_devices, int max_devices, char *err_buf, int err_len) {
    @autoreleasepool {
        ensure_run_loop();
        NSArray *devices = [IOBluetoothDevice pairedDevices];
        if (!devices) {
            return 0;
        }
        int count = 0;
        for (IOBluetoothDevice *dev in devices) {
            if (count >= max_devices) break;
            NSString *name = [dev nameOrAddress];
            NSString *addr = [dev addressString];
            snprintf(out_devices[count].name, sizeof(out_devices[count].name), "%s", name ? [name UTF8String] : "");
            snprintf(out_devices[count].address, sizeof(out_devices[count].address), "%s", addr ? [addr UTF8String] : "");
            out_devices[count].class_of_device = (uint32_t)[dev classOfDevice];
            count++;
        }
        (void)err_buf; (void)err_len;
        return count;
    }
}

static BluetoothRFCOMMChannelID find_rfcomm_channel_id(IOBluetoothDevice *device, const uint8_t *uuid16) {
    [device performSDPQuery:nil];
    // Find service matching uuid16
    IOBluetoothSDPUUID *targetUUID = nil;
    if (uuid16) {
        targetUUID = [IOBluetoothSDPUUID uuidWithBytes:uuid16 length:16];
    }
    NSArray *services = [device services];
    for (IOBluetoothSDPServiceRecord *record in services) {
        if (targetUUID && ![record hasServiceFromArray:@[targetUUID]]) {
            continue;
        }
        BluetoothRFCOMMChannelID chId = 0;
        if ([record getRFCOMMChannelID:&chId] == kIOReturnSuccess) {
            return chId;
        }
    }
    // Fallback: search any record with RFCOMM channel ID if uuid16 match wasn't direct
    for (IOBluetoothSDPServiceRecord *record in services) {
        BluetoothRFCOMMChannelID chId = 0;
        if ([record getRFCOMMChannelID:&chId] == kIOReturnSuccess) {
            return chId;
        }
    }
    return 0;
}

int64_t cp_bt_connect_rfcomm(const char *address, const uint8_t *uuid16, char *err_buf, int err_len) {
    @autoreleasepool {
        ensure_run_loop();
        if (!address) {
            set_err(err_buf, err_len, "Missing address");
            return 0;
        }
        NSString *addrStr = [NSString stringWithUTF8String:address];
        IOBluetoothDevice *dev = [IOBluetoothDevice deviceWithAddressString:addrStr];
        if (!dev) {
            set_err(err_buf, err_len, "Device not found for address");
            return 0;
        }

        BluetoothRFCOMMChannelID chId = find_rfcomm_channel_id(dev, uuid16);
        if (chId == 0) {
            set_err(err_buf, err_len, "RFCOMM service channel not found on device");
            return 0;
        }

        CpRfcommDelegate *delegate = [[CpRfcommDelegate alloc] init];
        IOBluetoothRFCOMMChannel *channel = nil;
        IOReturn ret = [dev openRFCOMMChannelSync:&channel withChannelID:chId delegate:delegate];
        if (ret != kIOReturnSuccess || !channel) {
            NSString *msg = [NSString stringWithFormat:@"Failed to open RFCOMM channel %d (IOReturn 0x%x)", chId, ret];
            set_err(err_buf, err_len, [msg UTF8String]);
            return 0;
        }
        delegate->channel = channel;
        // Retain delegate for handle
        return (int64_t)(intptr_t)CFBridgingRetain(delegate);
    }
}

int cp_bt_read(int64_t handle, uint8_t *buf, int len, char *err_buf, int err_len) {
    if (handle == 0 || !buf || len <= 0) {
        set_err(err_buf, err_len, "Invalid read arguments");
        return -1;
    }
    CpRfcommDelegate *delegate = (__bridge CpRfcommDelegate *)(void *)(intptr_t)handle;
    pthread_mutex_lock(&delegate->mutex);
    while (delegate->count == 0 && !delegate->closed) {
        pthread_cond_wait(&delegate->cond, &delegate->mutex);
    }
    if (delegate->count == 0 && delegate->closed) {
        pthread_mutex_unlock(&delegate->mutex);
        return 0; // EOF
    }
    int read_bytes = 0;
    while (delegate->count > 0 && read_bytes < len) {
        buf[read_bytes++] = delegate->ring_buf[delegate->head];
        delegate->head = (delegate->head + 1) % RING_BUF_CAPACITY;
        delegate->count--;
    }
    pthread_mutex_unlock(&delegate->mutex);
    return read_bytes;
}

int cp_bt_write(int64_t handle, const uint8_t *buf, int len, char *err_buf, int err_len) {
    if (handle == 0 || !buf || len < 0) {
        set_err(err_buf, err_len, "Invalid write arguments");
        return -1;
    }
    if (len == 0) return 0;
    CpRfcommDelegate *delegate = (__bridge CpRfcommDelegate *)(void *)(intptr_t)handle;
    IOBluetoothRFCOMMChannel *ch = delegate->channel;
    if (!ch || delegate->closed) {
        set_err(err_buf, err_len, "RFCOMM channel closed");
        return -1;
    }

    BluetoothRFCOMMMTU mtu = [ch getMTU];
    if (mtu == 0) mtu = 1024;

    int sent = 0;
    while (sent < len) {
        int chunkSize = (len - sent > (int)mtu) ? (int)mtu : (len - sent);
        IOReturn ret = [ch writeSync:(void *)(buf + sent) length:(UInt16)chunkSize];
        if (ret != kIOReturnSuccess) {
            NSString *msg = [NSString stringWithFormat:@"RFCOMM writeSync failed (0x%x)", ret];
            set_err(err_buf, err_len, [msg UTF8String]);
            return -1;
        }
        sent += chunkSize;
    }
    return sent;
}

void cp_bt_close(int64_t handle) {
    if (handle == 0) return;
    CpRfcommDelegate *delegate = (CpRfcommDelegate *)CFBridgingRelease((void *)(intptr_t)handle);
    pthread_mutex_lock(&delegate->mutex);
    delegate->closed = 1;
    pthread_cond_broadcast(&delegate->cond);
    if (delegate->channel) {
        [delegate->channel closeChannel];
        delegate->channel = nil;
    }
    pthread_mutex_unlock(&delegate->mutex);
}
