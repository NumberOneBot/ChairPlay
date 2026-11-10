// What the GPS output's operation and notices say: the app reports codes, never English (gpsApi.ts), so the words are here.
import type { GpsMethod, GpsNotice, GpsOperation, GpsOperationStage } from '../gpsApi'

export interface NoticeText {
  head: string
  text: string
}

const STAGE_TEXT: Record<GpsOperationStage, string> = {
  quiesce: 'Pausing the virtual car…',
  locate: 'Reading the Windows location…',
  return: 'Sending the Windows location before ending the simulation…',
  clear: 'Sending the reset command…',
  close: 'Closing the developer connection…',
  start: 'Starting the simulation…',
}

function operationHead(operation: GpsOperation, selected: GpsMethod): string {
  if (operation.stage === 'return') return 'Returning to the Windows location'
  switch (operation.kind) {
    case 'switch':
      return operation.target === 'usb'
        ? 'Switching to USB GPS'
        : operation.target === 'carplay'
          ? 'Switching to CarPlay GPS'
          : 'Switching the GPS method'
    case 'enable':
      return 'Starting the simulation'
    case 'resume':
      return 'Resuming USB GPS'
    case 'restore':
      return 'Restoring the Windows location'
    case 'shutdown':
      return 'Closing the USB simulation'
    case 'disable':
      return selected === 'usb' ? 'Stopping USB simulation' : 'Stopping the simulation'
  }
}

/** The progress block of the operation running: its kind says what, its stage how far. */
export function operationText(operation: GpsOperation, selected: GpsMethod): NoticeText {
  return { head: operationHead(operation, selected), text: STAGE_TEXT[operation.stage] }
}

const START_FAILED: Record<string, string> = {
  'usb-not-connected': 'Connect your iPhone with a USB cable, then try again.',
  'usb-several': 'Connect one iPhone only, then try again.',
  'usb-setup-needed': 'Complete USB setup, then try again.',
  'usb-not-ready': 'Complete USB setup, then try again.',
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
    case 'cleanup-unconfirmed':
      return {
        head: 'Reset not confirmed',
        text: 'ChairPlay could not confirm that your iPhone’s location was reset. Connect your iPhone with a USB cable to try again.',
      }
    case 'reconnect-needed':
      return { head: 'Reconnect needed', text: 'Reconnect CarPlay to use the new GPS method.' }
    case 'start-failed':
      return {
        head: 'GPS did not start',
        text: START_FAILED[notice.args.reason ?? ''] ?? 'Check the iPhone connection, then try again.',
      }
  }
}
