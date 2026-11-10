// A backend for the tests that records what the screens ask for; the states it starts from are in testing.ts.
import type {
  AltscreenContent,
  Backend,
  ControlsData,
  DemoKind,
  FolderSizes,
  KeyPhase,
  PictureWindow,
  SaveOutcome,
  Screen,
  SettingsData,
  WindowName,
  WindowsLocation,
  WindowsPlace,
} from './api'
import type { CommandOutcome, GpsMethod, SetupAction, SetupIntent, SetupStep } from './gpsApi'
import { controlsData } from './testing'

export class FakeBackend implements Backend {
  updateChecks = 0
  async checkUpdates(): Promise<boolean> { this.updateChecks++; return true }
  saved: Record<string, string>[] = []
  shown: Screen[] = []
  windows: { name: WindowName; visible: boolean }[] = []
  connects = 0
  reconnects = 0
  nextOutcome: SaveOutcome | null = null
  data: SettingsData = {
    values: {
      'wifi.ssid': 'Home',
      'wifi.security': 'WPA_WPA2',
      'ui.autoconnect': 'true',
      'ui.autoshow': 'true',
      'altscreen.enabled': 'false',
      'window.width': '',
      'window.height': '',
      'altscreen.width': '1440',
      'altscreen.height': '540',
    },
    passphraseSet: true,
    applies: {
      'audio.music.mode': 'reconnect',
      'wifi.ssid': 'reconnect',
      'wifi.passphrase': 'reconnect',
      'wifi.security': 'reconnect',
      'ui.autoconnect': 'restart',
      'ui.autoshow': 'now',
      'altscreen.enabled': 'restart',
      'window.width': 'restart',
      'window.height': 'restart',
      'altscreen.width': 'restart',
      'altscreen.height': 'restart',
      'log.carplay.data': 'restart',
    },
    display: { width: 1280, height: 720 },
    audio: {
      outputs: ['Speakers (Realtek Audio)', 'Headphones (USB Audio)'],
      inputs: ['Microphone (Realtek Audio)'],
      defaultOutput: 'Speakers (Realtek(R) Audio)',
      defaultInput: 'Microphone (Realtek(R) Audio)',
    },
  }

  async settings() {
    return this.data
  }

  /** The password settings.properties holds, which only the eye asks for. */
  passphrase: string | null = 'secret123'

  async savedPassphrase() {
    return this.passphrase
  }

  /** The HUD demos asked for, null for a stop. */
  demos: (DemoKind | null)[] = []

  async demo(kind: DemoKind | null) {
    this.demos.push(kind)
    return true
  }

  /** The volume levels the Audio page set. */
  volumes: number[] = []

  async volume(level: number) {
    this.volumes.push(level)
    return true
  }

  /** What Windows answers for where this PC is. */
  location: WindowsLocation = { error: 'unknown' }

  async windowsLocation() {
    return this.location
  }

  /** The GPS output's commands as sent, "gps-method usb 3", "gps-enabled true", "usb-setup open enable", "usb-setup 7 retry tools". */
  gpsCommands: string[] = []
  /** What the next GPS command answers. */
  gpsOutcome: CommandOutcome = 'accepted'

  async gpsMethod(method: GpsMethod, generation: number) {
    this.gpsCommands.push(`gps-method ${method} ${generation}`)
    return this.gpsOutcome
  }

  async gpsEnabled(enabled: boolean) {
    this.gpsCommands.push(`gps-enabled ${enabled}`)
    return this.gpsOutcome
  }

  async openUsbSetup(intent: SetupIntent) {
    this.gpsCommands.push(`usb-setup open ${intent}`)
    return this.gpsOutcome
  }

  async usbSetupAction(operationId: number, action: SetupAction, step?: SetupStep) {
    this.gpsCommands.push(['usb-setup', operationId, action, step].filter((part) => part !== undefined).join(' '))
    return this.gpsOutcome
  }

  /** Drive mode as the GPS page turned it. */
  driveModes: boolean[] = []

  async driveMode(on: boolean) {
    this.driveModes.push(on)
    return true
  }

  async save(changes: Record<string, string>): Promise<SaveOutcome> {
    this.saved.push(changes)
    const outcome = this.nextOutcome
    this.nextOutcome = null
    if (outcome) return outcome
    return { ok: true, applies: Object.fromEntries(Object.keys(changes).map((key) => [key, this.data.applies[key]])) }
  }

  async connect() {
    this.connects++
    return null
  }

  async reconnect() {
    this.reconnects++
  }

  disconnects = 0

  async disconnect() {
    this.disconnects++
  }

  async show(screen: Screen) {
    this.shown.push(screen)
    return true
  }

  async window(name: WindowName, visible: boolean) {
    this.windows.push({ name, visible })
    return true
  }

  /** The controls window's buttons, as "action down" or "action up". */
  controls: string[] = []
  altscreens: AltscreenContent[] = []

  async control(action: string, down: boolean) {
    this.controls.push(`${action} ${down ? 'down' : 'up'}`)
  }

  /** Keys handed on from a page, as "name phase settings" or "name phase everything". */
  keys: string[] = []
  keyboardFocusCalls: boolean[] = []
  async keyboardFocus(_client: string, focused: boolean) { this.keyboardFocusCalls.push(focused) }

  async key(name: string, phase: KeyPhase, everything: boolean) {
    this.keys.push(`${name} ${phase} ${everything ? 'everything' : 'settings'}`)
  }

  async altscreen(content: AltscreenContent) {
    this.altscreens.push(content)
    return true
  }

  opened: WindowsPlace[] = []
  sizes: FolderSizes = {}
  folderSizeReads = 0

  async folderSizes() {
    this.folderSizeReads++
    return this.sizes
  }

  /** How many times a page asked the app to read the setup again. */
  setupChecks = 0

  async checkSetup() {
    this.setupChecks++
  }

  async open(place: WindowsPlace) {
    this.opened.push(place)
    return true
  }

  framesShown = 0

  /** The views whose frames were asked for, by Show frame under View areas. */
  viewFrames: string[] = []

  async showAltscreenFrame(view?: string) {
    if (view) this.viewFrames.push(view)
    else this.framesShown++
    return true
  }

  forgets = 0

  async forget() {
    this.forgets++
    return true
  }

  firewallRules = 0
  consent = true

  async addFirewallRule() {
    this.firewallRules++
    return this.consent
  }

  /** The windows Actual size was asked of, in order. */
  actualSized: PictureWindow[] = []

  async actualSize(window: PictureWindow) {
    this.actualSized.push(window)
    return true
  }

  /** The local network addresses asked to open in the browser. */
  browserOpened: string[] = []

  async openInBrowser(url?: string) {
    if (url !== undefined) this.browserOpened.push(url)
    return true
  }

  bindings: ControlsData | null = controlsData()
  controlsSaved: Record<string, string>[] = []
  nextControlsOutcome: SaveOutcome | null = null

  async keyBindings() {
    return this.bindings && structuredClone(this.bindings)
  }

  /** Saves as the app does: a key taken from another action leaves it. */
  async saveKeyBindings(changes: Record<string, string>): Promise<SaveOutcome> {
    this.controlsSaved.push(changes)
    const outcome = this.nextControlsOutcome
    this.nextControlsOutcome = null
    if (outcome) return outcome
    const data = this.bindings!
    const applies: Record<string, 'now'> = {}
    for (const [key, value] of Object.entries(changes)) {
      applies[key] = 'now'
      if (!data.actions[key]) {
        data.settings[key] = value
        continue
      }
      const keys = value
        .split(',')
        .map((name) => name.trim())
        .filter(Boolean)
      data.actions[key].keys = keys
      for (const [other, action] of Object.entries(data.actions)) {
        if (other === key || !action.keys.some((name) => keys.includes(name))) continue
        action.keys = action.keys.filter((name) => !keys.includes(name))
        applies[other] = 'now'
      }
    }
    return { ok: true, applies }
  }
}
