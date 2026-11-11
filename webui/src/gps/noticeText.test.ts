// The track-unreadable notice's reason arrives as a code (gps/trackApi.ts) and is worded here; no test pins the words.
import { describe, expect, it } from 'vitest'
import type { GpsNotice } from '../gpsApi'
import type { TrackFormatReason } from './trackApi'
import { noticeText } from './noticeText'

const unreadable = (reason?: string): GpsNotice => ({
  id: 1, tone: 'bad', code: 'track-unreadable', args: reason === undefined ? {} : { reason }, expiresAt: null,
})

const REASONS: TrackFormatReason[] = ['not-gpx', 'no-track', 'no-times', 'too-short']

describe('a file track playback cannot play', () => {
  it('words each reason code, each its own way, under one head', () => {
    const texts = REASONS.map((reason) => noticeText(unreadable(reason)))
    expect(new Set(texts.map(({ head }) => head)).size).toBe(1)
    expect(new Set(texts.map(({ text }) => text)).size).toBe(REASONS.length)
    for (const { text } of texts) {
      expect(text).not.toBe('')
      expect(REASONS).not.toContain(text)
    }
  })

  it('words a reason it does not know, or none, as a file it cannot read', () => {
    const notGpx = noticeText(unreadable('not-gpx'))
    expect(noticeText(unreadable('corrupt-sector'))).toEqual(notGpx)
    expect(noticeText(unreadable())).toEqual(notGpx)
  })
})
