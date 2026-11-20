#import "macos_rfcomm.h"
#import "macos_bridge.h"
#include <string.h>

static NSThread *s_bt_thread = nil;

@interface CpBtRunner : NSObject
+ (instancetype)shared;
- (void)runBlock:(void (^)(void))block;
@end

@implementation CpBtRunner
+ (instancetype)shared {
    static CpBtRunner *instance;
    static dispatch_once_t once;
    dispatch_once(&once, ^{ instance = [[CpBtRunner alloc] init]; });
    return instance;
}
- (void)runBlock:(void (^)(void))block { if (block) block(); }
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
        s_bt_thread = thread;
    });
}

void cp_bt_run_on_loop(void (^block)(void)) {
    ensure_run_loop();
    if ([NSThread currentThread] == s_bt_thread) block();
    else [[CpBtRunner shared] performSelector:@selector(runBlock:) onThread:s_bt_thread withObject:[block copy] waitUntilDone:YES];
}

void cp_bt_post_to_loop(void (^block)(void)) {
    ensure_run_loop();
    [[CpBtRunner shared] performSelector:@selector(runBlock:) onThread:s_bt_thread withObject:[block copy] waitUntilDone:NO];
}

void cp_bt_set_err(char *err_buf, int err_len, const char *msg) {
    if (err_buf && err_len > 0) {
        snprintf(err_buf, (size_t)err_len, "%s", msg ? msg : "unknown error");
    }
}

@interface CpSdpWaiter : NSObject {
@public
    BOOL completed;
    IOReturn status;
    pthread_mutex_t mutex;
    pthread_cond_t cond;
}
@end
@implementation CpSdpWaiter
- (instancetype)init {
    self = [super init];
    if (self) {
        completed = NO;
        status = kIOReturnError;
        pthread_mutex_init(&mutex, NULL);
        pthread_cond_init(&cond, NULL);
    }
    return self;
}
- (void)dealloc {
    pthread_mutex_destroy(&mutex);
    pthread_cond_destroy(&cond);
}
- (void)sdpQueryComplete:(IOBluetoothDevice *)device status:(IOReturn)inStatus {
    (void)device;
    pthread_mutex_lock(&mutex);
    status = inStatus;
    completed = YES;
    pthread_cond_broadcast(&cond);
    pthread_mutex_unlock(&mutex);
}
@end

int cp_bt_radio_address(char *out_buf, int buf_len, char *err_buf, int err_len) {
    __block int result = -1;
    cp_bt_run_on_loop(^{
        @autoreleasepool {
            IOBluetoothHostController *hc = [IOBluetoothHostController defaultController];
            if (!hc) {
                cp_bt_set_err(err_buf, err_len, "No default Bluetooth host controller");
                return;
            }
            NSString *addr = [hc addressAsString];
            if (!addr || [addr length] == 0) {
                cp_bt_set_err(err_buf, err_len, "Host controller address unavailable");
                return;
            }
            snprintf(out_buf, (size_t)buf_len, "%s", [addr UTF8String]);
            result = 0;
        }
    });
    return result;
}

int cp_bt_paired_devices(CpBtDevice *out_devices, int max_devices, char *err_buf, int err_len) {
    __block int count = 0;
    cp_bt_run_on_loop(^{
        @autoreleasepool {
            NSArray *devices = [IOBluetoothDevice pairedDevices];
            if (!devices) return;
            for (IOBluetoothDevice *dev in devices) {
                if (count >= max_devices) break;
                NSString *name = [dev nameOrAddress];
                NSString *addr = [dev addressString];
                snprintf(out_devices[count].name, sizeof(out_devices[count].name), "%s", name ? [name UTF8String] : "");
                snprintf(out_devices[count].address, sizeof(out_devices[count].address), "%s", addr ? [addr UTF8String] : "");
                out_devices[count].class_of_device = (uint32_t)[dev classOfDevice];
                count++;
            }
        }
    });
    (void)err_buf; (void)err_len;
    return count;
}

int64_t cp_bt_connect_rfcomm(const char *address, const uint8_t *uuid16, char *err_buf, int err_len) {
    if (!address) {
        cp_bt_set_err(err_buf, err_len, "Missing address");
        return 0;
    }
    ensure_run_loop();
    NSString *addrStr = [NSString stringWithUTF8String:address];
    IOBluetoothDevice *dev = [IOBluetoothDevice deviceWithAddressString:addrStr];
    if (!dev) {
        cp_bt_set_err(err_buf, err_len, "Device not found for address");
        return 0;
    }

    CpSdpWaiter *waiter = [[CpSdpWaiter alloc] init];
    // Initiate SDP query on the Bluetooth run-loop thread asynchronously and return immediately to the loop
    cp_bt_post_to_loop(^{
        IOReturn ret = [dev performSDPQuery:waiter];
        if (ret != kIOReturnSuccess) {
            [waiter sdpQueryComplete:dev status:ret];
        }
    });

    // Caller thread waits on condition while the Bluetooth run loop services the SDP completion callback
    struct timespec ts;
    clock_gettime(CLOCK_REALTIME, &ts);
    ts.tv_sec += 5;
    pthread_mutex_lock(&waiter->mutex);
    while (!waiter->completed) {
        if (pthread_cond_timedwait(&waiter->cond, &waiter->mutex, &ts) != 0) break;
    }
    BOOL completed = waiter->completed;
    IOReturn status = waiter->status;
    pthread_mutex_unlock(&waiter->mutex);
    if (!completed) {
        cp_bt_set_err(err_buf, err_len, "Bluetooth SDP query timed out");
        return 0;
    }
    if (status != kIOReturnSuccess) {
        NSString *msg = [NSString stringWithFormat:@"Bluetooth SDP query failed (IOReturn 0x%x)", status];
        cp_bt_set_err(err_buf, err_len, [msg UTF8String]);
        return 0;
    }

    // SDP completed successfully on the BT thread; now enumerate services and open RFCOMM on the BT thread
    __block int64_t resultHandle = 0;
    cp_bt_run_on_loop(^{
        @autoreleasepool {
            BluetoothRFCOMMChannelID chId = 0;
            IOBluetoothSDPUUID *targetUUID = uuid16 ? [IOBluetoothSDPUUID uuidWithBytes:uuid16 length:16] : nil;
            for (IOBluetoothSDPServiceRecord *record in [dev services]) {
                if (targetUUID && ![record hasServiceFromArray:@[targetUUID]]) continue;
                if ([record getRFCOMMChannelID:&chId] == kIOReturnSuccess) break;
            }
            if (chId == 0) {
                cp_bt_set_err(err_buf, err_len, "RFCOMM service channel not found on device");
                return;
            }
            CpRfcommDelegate *delegate = [[CpRfcommDelegate alloc] init];
            IOBluetoothRFCOMMChannel *channel = nil;
            IOReturn ret = [dev openRFCOMMChannelSync:&channel withChannelID:chId delegate:delegate];
            if (ret != kIOReturnSuccess || !channel) {
                NSString *msg = [NSString stringWithFormat:@"Failed to open RFCOMM channel %d (IOReturn 0x%x)", chId, ret];
                cp_bt_set_err(err_buf, err_len, [msg UTF8String]);
                return;
            }
            delegate->channel = channel;
            resultHandle = (int64_t)(intptr_t)CFBridgingRetain(delegate);
        }
    });
    return resultHandle;
}
