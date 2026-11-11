// Fixtures of the GPS output and the USB setup for the tests and the preview, one per screen the mock draws
// (desktop-docs/mockups/usb-gps), and the few the owner added. Add a state here rather than a local copy of the types.
import type { GpsNotice, GpsOutput, SetupStep, StepState, UsbSetupView } from './gpsApi'

const PHONE = { ref: 'phone-1', name: 'iPhone' }
const NOW = 1_762_800_000_000

export function gpsOutput(overrides: Partial<GpsOutput> = {}): GpsOutput {
  return {
    selected: 'carplay', applied: 'carplay', enabledIntent: false, activeMethod: null, running: false, availability: 'ready',
    reason: null, usbPrepared: false, device: null, successfulSends: 0, rateHz: 4, generation: 1, operation: null,
    notices: [], ...overrides,
  }
}

const step = (status: StepState['status'], detail: string | null = null, error: string | null = null): StepState =>
  ({ status, detail, error })

const DONE = step('done')

function steps(current: SetupStep, state: StepState): Record<SetupStep, StepState> {
  const order: SetupStep[] = ['usb', 'trust', 'developer-mode', 'tools', 'ready']
  const at = order.indexOf(current)
  return Object.fromEntries(order.map((name, index) =>
    [name, index < at ? DONE : index === at ? state : step('unknown')])) as Record<SetupStep, StepState>
}

export function usbSetup(current: SetupStep, state: StepState, overrides: Partial<UsbSetupView> = {}): UsbSetupView {
  return {
    open: true, operationId: 7, generation: 1, intent: 'prepare', device: current === 'usb' ? null : PHONE,
    steps: steps(current, state), current, download: null, positionValid: true, ...overrides,
  }
}

export const closedSetup: UsbSetupView = { ...usbSetup('usb', step('unknown')), open: false, operationId: null }

const notice = (code: GpsNotice['code'], tone: GpsNotice['tone'], extra: Partial<GpsNotice> = {}): GpsNotice =>
  ({ id: 1, code, tone, args: {}, expiresAt: null, ...extra })

/** The output states, by the screen they make. */
export const outputs = {
  carplayOff: gpsOutput(),
  carplayOn: gpsOutput({ enabledIntent: true, activeMethod: 'carplay', running: true, successfulSends: 412 }),
  /**
   * USB chosen but CarPlay not reconnected since: CarPlay still sends, and the settings' reconnect mark (the state's
   * reconnectPending, gps.method) says the choice waits (D8).
   */
  reconnectNeeded: gpsOutput({
    selected: 'usb', applied: 'carplay', enabledIntent: true, activeMethod: 'carplay', running: true, usbPrepared: true,
    successfulSends: 412, generation: 2,
  }),
  usbUnprepared: gpsOutput({ selected: 'usb', applied: 'usb', availability: 'unavailable', reason: 'usb-setup-needed', rateHz: 10 }),
  usbPreparedNoCable: gpsOutput({
    selected: 'usb', applied: 'usb', availability: 'unavailable', reason: 'usb-not-connected', usbPrepared: true, rateHz: 10,
  }),
  usbOn: gpsOutput({
    selected: 'usb', applied: 'usb', enabledIntent: true, activeMethod: 'usb', running: true, usbPrepared: true,
    device: PHONE, successfulSends: 567, rateHz: 10,
  }),
  /** usb + enabled saved, started without the phone: checked and disabled, the warning above Settings (L-04). */
  usbStartupMissing: gpsOutput({
    selected: 'usb', applied: 'usb', enabledIntent: true, availability: 'unavailable', reason: 'usb-not-connected',
    usbPrepared: true, rateHz: 10, notices: [notice('usb-unavailable', 'warn')],
  }),
  usbNoPosition: gpsOutput({
    selected: 'usb', applied: 'usb', enabledIntent: true, availability: 'unavailable', reason: 'no-position',
    usbPrepared: true, device: PHONE, rateHz: 10,
  }),
  stoppingUsb: gpsOutput({
    selected: 'usb', applied: 'usb', usbPrepared: true, device: PHONE, rateHz: 10,
    operation: { id: 5, generation: 1, kind: 'disable', stage: 'close', target: null, startedAt: NOW },
  }),
} satisfies Record<string, GpsOutput>

/** The wizard's states, step by step (02 §3). */
export const setups = {
  connect: usbSetup('usb', step('waiting', 'no-device')),
  drivers: usbSetup('usb', step('action', 'drivers-missing')),
  appleDevicesClosed: usbSetup('usb', step('action', 'apple-devices-closed')),
  locked: usbSetup('usb', step('waiting', 'locked')),
  trust: usbSetup('trust', step('waiting', 'trust-prompt')),
  developerHidden: usbSetup('developer-mode', step('action', 'hidden')),
  developerRevealed: usbSetup('developer-mode', step('waiting', 'revealed')),
  developerLinkLost: usbSetup('developer-mode', step('waiting', 'link-lost')),
  toolsMissing: usbSetup('tools', step('action', 'missing')),
  downloading: usbSetup('tools', step('working', 'downloading'), { download: { receivedBytes: 6_300_000, totalBytes: 15_900_000 } }),
  downloadError: usbSetup('tools', step('error', 'missing', 'download-failed')),
  cached: usbSetup('tools', step('action', 'cached')),
  connecting: usbSetup('tools', step('working', 'connecting')),
  ready: usbSetup('ready', step('done')),
  readyEnable: usbSetup('ready', step('done'), { intent: 'enable' }),
  readyNoPosition: usbSetup('ready', step('done'), { intent: 'enable', positionValid: false }),
} satisfies Record<string, UsbSetupView>
