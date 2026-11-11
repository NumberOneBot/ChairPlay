// What the GPS output's operation and notices say: the app reports codes, never English (gpsApi.ts), so the words are here.
import type { GpsNotice } from '../gpsApi'

export interface NoticeText {
  head: string
  text: string
}

/** A notice's words. */
export function noticeText(notice: GpsNotice): NoticeText {
  switch (notice.code) {
    case 'usb-unavailable':
      return { head: 'USB GPS unavailable', text: 'Connect your iPhone with a USB cable to resume simulation.' }
  }
}
