#ifndef MACOS_RFCOMM_H
#define MACOS_RFCOMM_H

#import <Foundation/Foundation.h>
#import <IOBluetooth/IOBluetooth.h>
#include <pthread.h>
#include <stdint.h>

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

void cp_bt_set_err(char *err_buf, int err_len, const char *msg);
void cp_bt_run_on_loop(void (^block)(void));
void cp_bt_post_to_loop(void (^block)(void));

#endif // MACOS_RFCOMM_H
