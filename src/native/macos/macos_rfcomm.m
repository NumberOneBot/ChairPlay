#import "macos_rfcomm.h"
#import "macos_bridge.h"
#include <string.h>

@implementation CpRfcommDelegate

- (instancetype)init {
    self = [super init];
    if (self) {
        head = tail = count = 0;
        closed = 0;
        pthread_mutexattr_t attr;
        pthread_mutexattr_init(&attr);
        pthread_mutexattr_settype(&attr, PTHREAD_MUTEX_RECURSIVE);
        pthread_mutex_init(&mutex, &attr);
        pthread_mutexattr_destroy(&attr);
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

int cp_bt_read(int64_t handle, uint8_t *buf, int len, char *err_buf, int err_len) {
    if (handle == 0 || !buf || len <= 0) {
        cp_bt_set_err(err_buf, err_len, "Invalid read arguments");
        return -1;
    }
    CpRfcommDelegate *delegate = (__bridge CpRfcommDelegate *)(void *)(intptr_t)handle;
    pthread_mutex_lock(&delegate->mutex);
    while (delegate->count == 0 && !delegate->closed) {
        pthread_cond_wait(&delegate->cond, &delegate->mutex);
    }
    if (delegate->count == 0 && delegate->closed) {
        pthread_mutex_unlock(&delegate->mutex);
        return 0; // Clean EOF
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
        cp_bt_set_err(err_buf, err_len, "Invalid write arguments");
        return -1;
    }
    if (len == 0) return 0;
    CpRfcommDelegate *delegate = (__bridge CpRfcommDelegate *)(void *)(intptr_t)handle;
    IOBluetoothRFCOMMChannel *ch = delegate->channel;
    if (!ch || delegate->closed) {
        cp_bt_set_err(err_buf, err_len, "RFCOMM channel closed");
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
            cp_bt_set_err(err_buf, err_len, [msg UTF8String]);
            return -1;
        }
        sent += chunkSize;
    }
    return sent;
}

void cp_bt_close(int64_t handle) {
    if (handle == 0) return;
    CpRfcommDelegate *delegate = (CpRfcommDelegate *)CFBridgingRelease((void *)(intptr_t)handle);
    IOBluetoothRFCOMMChannel *ch = nil;
    pthread_mutex_lock(&delegate->mutex);
    delegate->closed = 1;
    pthread_cond_broadcast(&delegate->cond);
    ch = delegate->channel;
    delegate->channel = nil;
    pthread_mutex_unlock(&delegate->mutex);

    // Close channel outside the delegate mutex on the Bluetooth owning loop
    if (ch) {
        cp_bt_post_to_loop(^{
            [ch closeChannel];
        });
    }
}
