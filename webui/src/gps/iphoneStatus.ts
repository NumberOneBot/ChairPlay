import type { GpsState } from '../api'
import type { GpsOutput } from '../gpsApi'
import { operationHead } from './noticeText'

const REASON_TEXT: Partial<Record<NonNullable<GpsOutput['reason']>, string>> = {
  'usb-setup-needed': 'Setup required',
  'usb-not-connected': 'Not connected over USB',
  'usb-not-ready': 'Not ready, open USB setup',
  'usb-helper-failed': 'The USB service stopped',
  'no-position': 'Waiting for a position',
}

/** What the iPhone row says for USB: the cable and the setup, not CarPlay's Not listening and sentences. */
function usbStatus(output: GpsOutput): string {
  const phone = output.device?.name
  if (output.running) {
    const sent = `${output.successfulSends} ${output.successfulSends === 1 ? 'update' : 'updates'} sent`
    return `${phone ?? 'iPhone'} over USB; ${sent}`
  }
  if (output.reason && REASON_TEXT[output.reason]) return REASON_TEXT[output.reason]!
  if (!output.usbPrepared) return 'Setup required'
  return phone ? `${phone} connected over USB` : 'Not connected over USB'
}

/** CarPlay's own line: what the iPhone asked for in this connection, and what it was sent. */
function carplayStatus(car: GpsState | null): string {
  if (!car) return 'Not listening'
  const updates = `${car.sent} ${car.sent === 1 ? 'update' : 'updates'} sent`
  return car.listening ? `Listening: ${(car.sentences ?? []).join(', ')}; ${updates}` : 'Not listening'
}

/** The iPhone row, last in the GPS section: the operation while one runs, else by the selected method. */
export function iphoneStatus(output: GpsOutput, car: GpsState | null): string {
  if (output.operation) return operationHead(output.operation)
  return output.selected === 'usb' ? usbStatus(output) : carplayStatus(car)
}
