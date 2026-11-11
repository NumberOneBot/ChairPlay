// What the GPS output's operation and notices say: the app reports codes, never English (gpsApi.ts), so the words are here.
import type { GpsNotice } from '../gpsApi'
import type { TrackFormatReason } from './trackApi'

export interface NoticeText {
  head: string
  text: string
}

/** Why Open…'s file cannot be played, by the code the app sends (track-unreadable's args.reason). */
const TRACK_REASON_TEXT: Record<TrackFormatReason, string> = {
  'not-gpx': "It isn't a readable GPX file.",
  'no-track': 'It has no GPS track.',
  'no-times': 'Its GPS points have no times.',
  'too-short': 'Its GPS track is too short.',
}

/** A notice's words. */
export function noticeText(notice: GpsNotice): NoticeText {
  switch (notice.code) {
    case 'usb-unavailable':
      return { head: 'USB GPS unavailable', text: 'Connect your iPhone with a USB cable to resume simulation.' }
    case 'track-unreadable': {
      // A reason this page does not know yet reads as a file it cannot read.
      const reason = notice.args.reason
      const known = reason !== undefined && Object.prototype.hasOwnProperty.call(TRACK_REASON_TEXT, reason)
      return { head: "Can't play this file", text: TRACK_REASON_TEXT[known ? (reason as TrackFormatReason) : 'not-gpx'] }
    }
  }
}
