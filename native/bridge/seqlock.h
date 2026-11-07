/* The bridge files' seqlock: seq (offset 8) is odd while a record is written. One writer per file. */
#ifndef CHAIRPLAY_SEQLOCK_H
#define CHAIRPLAY_SEQLOCK_H

#include <windows.h>
#include <stddef.h>

#define CPB_SEQ_OFFSET 8
#define CPB_SEQ_TRIES 4

static inline volatile LONG* cpb_seq(void* record) {
    return (volatile LONG*)((char*)record + CPB_SEQ_OFFSET);
}

/* seq turns odd: readers retry until cpb_seq_end. */
static inline void cpb_seq_begin(void* record) {
    InterlockedIncrement(cpb_seq(record));
    MemoryBarrier();
}

/* seq turns even again: the record is whole. */
static inline void cpb_seq_end(void* record) {
    MemoryBarrier();
    InterlockedIncrement(cpb_seq(record));
}

/* Copies size bytes of a record another process writes. 1 when one copy of up to CPB_SEQ_TRIES saw an even seq that
   did not change, 0 when none did (out then holds a torn copy). */
static inline int cpb_seq_read(const volatile void* record, void* out, size_t size) {
    const volatile char* from = (const volatile char*)record;
    const volatile LONG* seq = (const volatile LONG*)(from + CPB_SEQ_OFFSET);
    char* to = (char*)out;
    for (int attempt = 0; attempt < CPB_SEQ_TRIES; attempt++) {
        LONG before = *seq;
        MemoryBarrier();
        for (size_t i = 0; i < size; i++) to[i] = from[i];
        MemoryBarrier();
        if ((before & 1) == 0 && *seq == before) return 1;
        YieldProcessor();
    }
    return 0;
}

#endif
