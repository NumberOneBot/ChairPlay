// The GPS output and USB setup as the app reports them (state.gps.output, state.gps.usbSetup) and the commands that
// change them; the Kotlin side is gps/GpsOutput.kt, UsbSetupView.kt and GpsCommands.kt. Frozen by A0
// (desktop-docs/usb-gps-spec/10-a0-contracts.md): codes, never English, for the page to word.

export type GpsMethod = 'carplay' | 'usb'

export type GpsAvailability = 'ready' | 'unavailable' | 'preparing' | 'error'

export type GpsReason =
  | 'carplay-not-connected'
  | 'carplay-reconnect-needed'
  | 'usb-setup-needed'
  | 'usb-not-connected'
  | 'usb-several'
  | 'usb-not-ready'
  | 'usb-helper-failed'
  | 'no-position'

export type GpsOperationKind = 'switch' | 'enable' | 'disable' | 'resume' | 'restore' | 'shutdown'
export type GpsOperationStage = 'quiesce' | 'locate' | 'return' | 'clear' | 'close' | 'start'

/** The one operation running; while there is one, the whole app is held (S-09). */
export interface GpsOperation {
  id: number
  generation: number
  kind: GpsOperationKind
  stage: GpsOperationStage
  target: GpsMethod | null
  /** Epoch milliseconds. */
  startedAt: number
}

export type GpsCleanup = 'clean' | 'needed' | 'attempting' | 'unconfirmed'

export type NoticeTone = 'info' | 'warn' | 'bad'

export type GpsNoticeCode =
  | 'reset-sent'
  | 'windows-unavailable'
  | 'usb-unavailable'
  | 'cleanup-unconfirmed'
  | 'reconnect-needed'
  | 'start-failed'

export interface GpsNotice {
  id: number
  tone: NoticeTone
  code: GpsNoticeCode
  /** windows-unavailable: reason 'denied' | 'unknown' | 'timeout'; start-failed: reason, a GpsReason or UsbErrorCode. */
  args: Record<string, string>
  /** Epoch milliseconds it goes at; null stays until the app drops it. */
  expiresAt: number | null
}

/** The USB iPhone: [ref] is opaque (never the UDID), [name] the phone's own. */
export interface UsbDevice {
  ref: string
  name: string
}

export interface GpsOutput {
  selected: GpsMethod
  /** The saved Enable signal simulation: kept while the phone is away (checked and disabled, P-06). */
  enabledIntent: boolean
  activeMethod: GpsMethod | null
  running: boolean
  availability: GpsAvailability
  reason: GpsReason | null
  /** A USB iPhone was prepared before: no "complete USB setup" hint. */
  usbPrepared: boolean
  device: UsbDevice | null
  /** Commands the channel took this run, not fixes the iPhone used. */
  successfulSends: number
  rateHz: number
  /** Sent back with gps-method, so a stale page's choice is refused. */
  generation: number
  operation: GpsOperation | null
  cleanup: GpsCleanup
  notices: GpsNotice[]
}

export type SetupStep = 'usb' | 'trust' | 'developer-mode' | 'tools' | 'ready'

export type StepStatus = 'unknown' | 'checking' | 'waiting' | 'action' | 'working' | 'done' | 'error'

/**
 * detail by step — usb: no-device, drivers-missing, apple-devices-closed, several-devices, locked; trust:
 * trust-prompt, locked; developer-mode: hidden, revealed, link-lost; tools: missing, downloading, cached, connecting,
 * unsupported-ios. error: a UsbErrorCode (download-failed, disk-full, mount-failed, service-unavailable, …).
 */
export interface StepState {
  status: StepStatus
  detail: string | null
  error: string | null
}

export type SetupIntent = 'prepare' | 'enable' | 'resume'

export interface UsbSetupView {
  open: boolean
  operationId: number | null
  generation: number
  intent: SetupIntent
  device: UsbDevice | null
  steps: Record<SetupStep, StepState>
  current: SetupStep
  download: { receivedBytes: number; totalBytes: number | null } | null
  /** Windows gave no return point: a warning on Ready; Enable USB GPS still works. */
  windowsWarning: boolean
  /** The car has a valid place; without one, Enable waits for the user to set it. */
  positionValid: boolean
}

export type CommandOutcome = 'accepted' | 'no-change' | 'stale' | 'busy' | 'invalid' | 'setup-opened'

export type SetupAction = 'reveal' | 'tools' | 'retry' | 'enable' | 'done' | 'cancel'

/** The GPS output's commands; the settings endpoint refuses gps.method, gps.enabled and gps.usb.device. */
export interface GpsBackend {
  /** Selects the method; [generation] is the output's as the page saw it. */
  gpsMethod(method: GpsMethod, generation: number): Promise<CommandOutcome>
  /** The Enable signal simulation switch; with USB not ready it opens the setup ('setup-opened'). */
  gpsEnabled(enabled: boolean): Promise<CommandOutcome>
  /** Opens the USB setup wizard. */
  openUsbSetup(intent: SetupIntent): Promise<CommandOutcome>
  /** An action of the open wizard; [step] only for retry. */
  usbSetupAction(operationId: number, action: SetupAction, step?: SetupStep): Promise<CommandOutcome>
}
