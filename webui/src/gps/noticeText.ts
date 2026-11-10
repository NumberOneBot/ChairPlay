// What the GPS output's operation and notices say: the app reports codes, never English (gpsApi.ts), so the words are here.
import type { GpsNotice, GpsOperation } from '../gpsApi'

export interface NoticeText {
  head: string
  text: string
}

/** The one status line of an operation under way: what it is, with no stages. */
export function operationHead(operation: GpsOperation): string {
  switch (operation.kind) {
    case 'enable':
      return 'Starting USB GPS'
    case 'resume':
      return 'Resuming USB GPS'
    case 'disable':
    case 'shutdown':
      return 'Stopping USB GPS'
    case 'apply':
      return 'Applying the GPS method'
  }
}

/** A notice's words. */
export function noticeText(notice: GpsNotice): NoticeText {
  switch (notice.code) {
    case 'reset-sent':
      return {
        head: 'Reset command sent',
        text: 'Your iPhone and navigation apps may take time to update their location. If a navigation app still shows the simulated position, close and reopen it.',
      }
    case 'windows-unavailable':
      return {
        head: 'Windows location unavailable',
        text: 'Simulation can start from the current virtual position. Automatic return to the Windows location is unavailable.',
      }
    case 'usb-unavailable':
      return { head: 'USB GPS unavailable', text: 'Connect your iPhone with a USB cable to resume simulation.' }
  }
}
