// The app's API (UiServer.kt): what it sends and takes, and what the screens need from it; the client is apiClient.ts.

export type Phase = 'setup' | 'idle' | 'starting' | 'calling' | 'joining' | 'active' | 'retrying'
export type Screen = 'start' | 'settings' | 'carplay'
/**
 * Whether Windows Firewall lets the iPhone in; 'off' when it is off for the networks the PC is on; 'partial' when our
 * rule lets the iPhone in but, with ui.network on, does not open the settings' port to other devices.
 */
export type Firewall = 'present' | 'missing' | 'partial' | 'blocked' | 'unknown' | 'off'
export type Applies = 'now' | 'reconnect' | 'restart'
/** The app's windows besides the main one: the AltScreen and the on-screen car controls. */
export type WindowName = 'altscreen' | 'controls'
/** A key going down, repeating while held, or coming up. */
export type KeyPhase = 'press' | 'repeat' | 'release'

/** What the AltScreen shows (AltscreenContent in DesktopConfig.kt). */
export type AltscreenContent = 'hud' | 'map' | 'map-card' | 'card'

/** The Windows pages, folder, and web pages a link can open (UiController.PLACES). */
/** The HUD's demos the Experiments page plays: a made-up route, and the lanes switching under one maneuver. */
export type DemoKind = 'maneuvers' | 'lanes'

export type WindowsPlace =
  | 'bluetooth'
  | 'wifi'
  | 'firewall'
  | 'firewall-rules'
  | 'mfi-folder'
  | 'issues'
  | 'discussions'
  | 'pull-requests'
  | 'author'
  | 'buy-me-a-coffee'
  | 'source'
  | 'releases'
  | 'notices'
  | 'location'
  | 'diplay'
  | 'xcertplay'
  | 'livi'
  | 'rgi'
  | 'logs'
  | 'screenshots'

/** What the controls window shows beside its buttons (UiControls in Main.kt). */
export interface ControlsState {
  /** Each action's first key as controls.properties names it, by its name there without "key."; null when unbound. */
  keys: Record<string, string | null>
  volume: { level: number; muted: boolean }
  /** What the AltScreen shows; null without an AltScreen. */
  altscreen: AltscreenContent | null
  /** Alt is held in one of ChairPlay's windows: the buttons show their keys. */
  altHeld: boolean
  globalKeyboard?: { enabled: boolean; error: string | null }
  /** The iPhone plays (its now playing says so): the play/pause button shows pause. */
  playing: boolean
}

/** A window's client area, the picture it shows, and the smallest it gets, in screen pixels, for Actual size. */
export interface WindowSizes {
  width: number
  height: number
  pictureWidth: number
  pictureHeight: number
  minWidth: number
  minHeight: number
}

/** A window that has an Actual size. */
export type PictureWindow = 'main' | 'altscreen'

/** A commit by its short id and the day it was made. */
export interface BuildCommit {
  commit: string
  date: string
}

/** The GPS car as the GPS page shows it, and what the iPhone takes from it. */
export interface GpsState {
  /** Latitude and longitude; null until the car has a start. */
  position: [number, number] | null
  /** Degrees, 0 north, 90 east. */
  heading: number
  /** km/h. */
  speed: number
  rpm: number
  /** 'D1' to 'D5', or 'R'. */
  gear: string
  /** The pedals, from 0 to 1. */
  throttle: number
  brake: number
  /** Drive mode: the arrows drive the car. */
  keyboard: boolean
  gamepad: boolean
  /** The iPhone asked for the position in this connection. */
  listening: boolean
  /** The sentences it asked for: GGA, RMC, GSV, PASCD; null before it asked. */
  sentences: string[] | null
  /** Position updates sent in this connection. */
  sent: number
}

/** A window is available while its feature is on; only then can it be shown or hidden. */
export interface WindowState {
  available: boolean
  visible: boolean
}

export interface AppState {
  bufferedAudio: {
    state: 'loading' | 'playing' | 'paused' | 'empty'; seconds: number; bytes: number
    multichannelOutput?: 'surround' | 'downmix'
    spatial?: true
  } | null
  version: number
  /** Moves on with each settings save; the page re-reads /api/settings when it changes, since the state omits them. */
  settingsVersion: number
  phase: Phase
  screen: Screen
  error: { message: string; at: string } | null
  knownIphone: boolean
  canShowCarPlay: boolean
  autoshow: boolean
  /** The app connects on its own (ui.autoconnect, or a session lost from CarPlay): the page waits, black, until it fails. */
  waiting: boolean
  /** The HUD's demos on the AltScreen (Experiments): the one playing, and whether one can (the AltScreen is on). */
  demo: { playing: DemoKind | null; available: boolean } | null
  /** The GPS car (the GPS page); null without one. */
  gps: GpsState | null
  /** This page is in ChairPlay's window (WebView2) rather than a browser; a tab never is (App.tsx). */
  embedded: boolean
  /**
   * The port the page is served on, and ui.port's, which another program had at start when the two differ; whether
   * this run listens on the network (ui.network at start), and then the addresses another device opens it at.
   */
  uiServer: { port: number; configuredPort: number; network: boolean; urls: string[] }
  wifi: { ssid: string; security: string; passphraseSet: boolean }
  reconnectPending: string[]
  restartPending: string[]
  windows: Record<WindowName, WindowState>
  /**
   * The main window's client area, the picture's size, and the smallest the window gets (Windows' display scale grows
   * it), in screen pixels; null without a main window.
   */
  mainWindow: WindowSizes | null
  /** The AltScreen's window, the same way; null without an AltScreen. */
  altscreenWindow: WindowSizes | null
  /** The virtual car's two wells in the AltScreen's frame (left first), empty while the AltScreen is off. */
  altscreenWells: { x: number; y: number; radius: number }[]
  /** Null when the app has no controls window. */
  controls: ControlsState | null
  /** The controls window shows each button's key always, not only while Alt is held (ui.controls.keys). */
  controlsKeys: boolean
  /** Get started is done: an iPhone has connected since the last Forget this iPhone. */
  setupDone: boolean
  /** ui.accent, #rrggbb: the colour every accent shade of the pages is made from. */
  accent: string
  /** Which build this is: this repository's commit and the DiPlay core's; null for one the build did not record. */
  about: {
    /** The release, X.Y.Z; null for a development build. */
    version: string | null
    app: BuildCommit | null
    core: BuildCommit | null
    /** What a report needs, for Copy details and New issue: the build, the core, and Windows. */
    details: string
  }
  setup: {
    mfiFolder: string
    mfiPresent: boolean
    firewall: Firewall
    firewallCommands: string
    /** The paired iPhone's name, as Windows lists it; null when none is paired. Read again every few seconds. */
    pairedIphone: string | null
    /** The Wi-Fi network this PC is on; null when it is on none. */
    pcWifi: string | null
    ethernet?: { id: string; label: string; addresses: string[] }[]
    ethernetSelected?: string | null
    networkError?: boolean
    /** The PC is on Wi-Fi, but Windows hides the network: location is off for desktop apps. */
    wifiHidden?: boolean
    settingsFile: string
  }
}

export interface SettingsData {
  /** Every setting the screens change, except the Wi-Fi password, which never leaves the app. */
  values: Record<string, string>
  passphraseSet: boolean
  applies: Record<string, Applies>
  display: { width: number; height: number }
  /**
   * The sound devices Java Sound lists, by name; empty when it fails. The defaults name the devices Windows default
   * stands for, as Windows names them; null when there is none, or Windows did not say.
   */
  audio: { outputs: string[]; inputs: string[]; defaultOutput?: string | null; defaultInput?: string | null }
}

/** One action of controls.properties, by its setting ("key.home"): its keys as the file names them, in order. */
export interface ActionKeys {
  keys: string[]
  defaults: string[]
  /** The keys it has whatever the file says: Escape for Back, Enter for Select. */
  fixed: string[]
}

/** The Keyboard page's data (GET /api/controls): the actions in the file's order, then the wheel and repeat. */
export interface ControlsData {
  actions: Record<string, ActionKeys>
  settings: Record<string, string>
}

export type SaveOutcome = { ok: true; applies: Record<string, Applies> } | { ok: false; errors: Record<string, string> }

/** Where Windows locates this PC (WindowsLocation.kt), or why it does not say. */
export type WindowsLocation = { latitude: number; longitude: number } | { error: 'denied' | 'unknown' }

/** The logs and screenshots folders' sizes in bytes, by folder. */
export type FolderSizes = Partial<Record<'logs' | 'screenshots', number>>

/** What the screens need from the app; tests pass a fake. */
export interface Backend {
  settings(): Promise<SettingsData>
  save(changes: Record<string, string>): Promise<SaveOutcome>
  /** The saved Wi-Fi password, for the eye beside it; null when none is saved. Nothing else carries it. */
  savedPassphrase(): Promise<string | null>
  /** Plays a HUD demo on the AltScreen, or stops it (null); false when none can play. */
  demo(kind: DemoKind | null): Promise<boolean>
  /** Sets the app's volume to [level] percent, unmuting it. */
  volume(level: number): Promise<boolean>
  /** Where Windows locates this PC, or why it does not say: location off or not let ("denied"), or no fix. */
  windowsLocation(): Promise<WindowsLocation>
  /** Turns drive mode on or off, as F10 does; false while the GPS is off. */
  driveMode(on: boolean): Promise<boolean>
  /** Null when connecting started, else why not. */
  connect(): Promise<string | null>
  reconnect(): Promise<void>
  /** Ends the session or the bring-up; the app then waits for Connect. */
  disconnect(): Promise<void>
  show(screen: Screen): Promise<boolean>
  /** Shows or hides a window; false when it is not available. Its new state comes with the next app state. */
  window(name: WindowName, visible: boolean): Promise<boolean>
  /** Presses ([down]) or releases a controls window button: a key of controls.properties without "key.". */
  control(action: string, down: boolean): Promise<void>
  /**
   * Hands a key pressed on the page on to the app, as pressed on the window: its name as controls.properties writes
   * it. [everything] over the controls window; over the settings the app takes only its own keys (F11, F12 and such).
   */
  key(name: string, phase: KeyPhase, everything: boolean): Promise<void>
  keyboardFocus(client: string, focused: boolean): Promise<void>
  /** Shows [content] on the AltScreen; false when it cannot be shown now. */
  altscreen(content: AltscreenContent): Promise<boolean>
  /** Opens a Windows page or the MFi folder; false when Windows did not open it. */
  open(place: WindowsPlace): Promise<boolean>
  /** Checks for an update and keeps the request pending until its Windows dialog is dismissed. */
  checkUpdates(): Promise<boolean>
  /** The logs and screenshots folders' sizes in bytes; 0 for an empty or missing one. */
  folderSizes(): Promise<FolderSizes>
  /** Resizes [window] to one screen pixel per picture pixel; false when there is no such window. */
  actualSize(window: PictureWindow): Promise<boolean>
  /** Adds the firewall rule with the consent Windows asks for; false when it was not given or the rule failed. */
  addFirewallRule(): Promise<boolean>
  /** Asks the app to read the setup again: Bluetooth, the network, the firewall, and the MFi files. */
  checkSetup(): Promise<void>
  /** Forget this iPhone: ChairPlay's pairing records go, and Get started opens. */
  forget(): Promise<boolean>
  /** Frames the AltScreen's saved safe area on its window for a moment; false without an AltScreen. */
  /** Draws the AltScreen's safe area for a moment, or [view]'s, inside its part of the frame. */
  showAltscreenFrame(view?: string): Promise<boolean>
  /** The key bindings; null when the app has none to change. */
  keyBindings(): Promise<ControlsData | null>
  /** Saves controls.properties changes; the actions a key moved from are among those saved. */
  saveKeyBindings(changes: Record<string, string>): Promise<SaveOutcome>
  /**
   * Opens the settings in the PC's default browser, at their address on this PC or at [url], one of uiServer.urls;
   * false when none opened.
   */
  openInBrowser(url?: string): Promise<boolean>
}
