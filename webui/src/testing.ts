// Fakes for the tests: app states, and the key bindings of a fresh controls.properties; the backend is fakeBackend.ts.
// Also how the tests find a control: by its data-testid, never by the words it shows (CLAUDE.md).
import { screen } from '@testing-library/react'
import type { AppState, ControlsData } from './api'

/**
 * The element with data-testid [testId], checked to be a [role] the page exposes, so the control stays reachable.
 * Throws rather than calling expect: fakeBackend.ts brings this file into the dev previews, where vitest cannot run.
 */
export function control(testId: string, role: string): HTMLElement {
  const element = screen.getByTestId(testId)
  if (!screen.getAllByRole(role).includes(element)) throw new Error(`${testId} is not a ${role} the page exposes`)
  return element
}

/** Whether the page has no element with data-testid [testId]. */
export function absent(testId: string): boolean {
  return screen.queryByTestId(testId) === null
}

export function appState(overrides: Partial<AppState> = {}): AppState {
  return {
    version: 1,
    settingsVersion: 1,
    phase: 'calling',
    screen: 'start',
    error: null,
    knownIphone: false,
    canShowCarPlay: false,
    autoshow: true,
    waiting: false,
    demo: { playing: null, available: true },
    gps: null,
    bufferedAudio: null,
    embedded: true,
    uiServer: { port: 7529, configuredPort: 7529, network: false, urls: [] },
    wifi: { ssid: 'Home', security: 'WPA_WPA2', passphraseSet: true },
    reconnectPending: [],
    restartPending: [],
    windows: { altscreen: { available: true, visible: true }, controls: { available: true, visible: true } },
    mainWindow: null,
    altscreenWindow: null,
    altscreenWells: [],
    controlsKeys: false,
    setupDone: true,
    accent: '#52c4ff',
    about: {
      version: null,
      app: { commit: '4e6d50df', date: '2026-10-23' },
      core: { commit: '143f4674', date: '2026-10-21' },
      details: 'ChairPlay 4e6d50df (2026-10-23)\nDiPlay core 143f4674 (2026-10-21)\nWindows 11 24H2 (build 26300)',
    },
    controls: {
      keys: {
        'knob.left': 'Left',
        'knob.right': 'Right',
        'knob.up': 'Up',
        'knob.down': 'Down',
        'knob.next': 'Tab',
        'knob.previous': 'Shift+Tab',
        select: 'Enter',
        back: 'Escape',
        home: 'Home',
        siri: 'S',
        'media.previous': 'F7',
        'media.playpause': 'F8',
        'media.next': 'F9',
        'call.answer': 'F3',
        'call.end': 'F4',
        reconnect: 'Ctrl+R',
        screenshot: 'F12',
        'altscreen.content': 'F11',
        'altscreen.zoom.in': 'Equal',
        'altscreen.zoom.out': 'Minus',
        'altscreen.cards': 'Shift+F11',
        'volume.up': 'Ctrl+Up',
        'volume.down': 'Ctrl+Down',
        'volume.mute': 'Ctrl+M',
        drive: 'F10',
      },
      volume: { level: 60, muted: false },
      altscreen: 'map',
      altHeld: false,
      playing: false,
    },
    setup: {
      mfiFolder: 'C:\\ChairPlay\\offline-mfi',
      mfiPresent: true,
      firewall: 'present',
      firewallCommands: 'netsh advfirewall firewall add rule name="ChairPlay CarPlay inbound"',
      pairedIphone: "Kate's iPhone",
      pcWifi: 'Home',
      settingsFile: 'C:\\ChairPlay\\settings.properties',
    },
    ...overrides,
  }
}

/** [state] with the actions in [keys] bound to other keys; null leaves one unbound. */
export function withKeys(state: AppState, keys: Record<string, string | null>): AppState {
  return { ...state, controls: { ...state.controls!, keys: { ...state.controls!.keys, ...keys } } }
}

/** ControlAction's settings and default keys, in its order. */
const DEFAULT_KEYS: [string, string[]][] = [
  ['key.knob.left', ['Left']],
  ['key.knob.right', ['Right']],
  ['key.knob.up', ['Up']],
  ['key.knob.down', ['Down']],
  ['key.knob.next', ['Tab']],
  ['key.knob.previous', ['Shift+Tab']],
  ['key.select', ['Enter', 'KP_Enter', 'Space']],
  ['key.back', ['Escape', 'Backspace']],
  ['key.home', ['Home']],
  ['key.media.previous', ['F7']],
  ['key.media.playpause', ['F8']],
  ['key.media.next', ['F9']],
  ['key.call.answer', ['F3']],
  ['key.call.end', ['F4']],
  ['key.siri', ['S']],
  ['key.reconnect', ['Ctrl+R']],
  ['key.screenshot', ['F12']],
  ['key.fullscreen', ['F']],
  ['key.altscreen.content', ['F11']],
  ['key.altscreen.zoom.in', ['Equal', 'Shift+Equal', 'KP_Add']],
  ['key.altscreen.zoom.out', ['Minus', 'KP_Subtract']],
  ['key.altscreen.cards', ['Shift+F11']],
  ['key.volume.up', ['Ctrl+Up']],
  ['key.volume.down', ['Ctrl+Down']],
  ['key.volume.mute', ['Ctrl+M']],
  ['key.drive', ['F10']],
  ['key.gauges', ['Shift+F10']],
]

/** The bindings as a fresh controls.properties has them. */
export function controlsData(): ControlsData {
  return {
    actions: Object.fromEntries(
      DEFAULT_KEYS.map(([key, keys]) => [
        key,
        {
          keys: [...keys],
          defaults: [...keys],
          fixed: key === 'key.back' ? ['Escape'] : key === 'key.select' ? ['Enter'] : [],
        },
      ]),
    ),
    settings: {
      'wheel.action': 'knob',
      'wheel.invert': 'false',
      'wheel.notches.per.step': '1',
      'key.repeat.every': '3',
      'global.enabled': 'false',
      'key.zoom.repeat.every': '10',
    },
  }
}
