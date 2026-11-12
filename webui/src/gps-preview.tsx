// Development-only entry point: the GPS page, the USB setup and Experiments on the fixtures (gpsFixtures.ts), with no
// app behind them. ?s=<scene>&section=<GPS|Experiments> picks what shows; scenes is the list of them, for the check.
import { useEffect, useMemo, useState } from 'react'
import { createRoot } from 'react-dom/client'
import type { AppState } from './api'
import { FakeBackend } from './fakeBackend'
import type { TrackState } from './gps/trackApi'
import type { GpsOutput, UsbSetupView } from './gpsApi'
import { outputs, setups, tracks } from './gpsFixtures'
import { gpsState } from './gps/gpsTesting'
import { Shell } from './shell/Shell'
import './styles/index.css'

interface Scene {
  output: GpsOutput
  setup?: UsbSetupView
  section?: string
  /** The Developer tools folder's size, for Experiments. */
  tools?: number
  connected?: boolean
  /** Settings that wait for the next connection (the state's reconnectPending). */
  pending?: string[]
  /** The recording track playback has loaded. */
  track?: TrackState
}

/** [state] with [track] loaded in its GPS. */
const tracked = (state: AppState, track?: TrackState): AppState =>
  track ? { ...state, gps: { ...state.gps!, track } } : state

const prepared = outputs.usbUnprepared
const starting: GpsOutput = {
  ...outputs.stoppingUsb,
  operation: { ...outputs.stoppingUsb.operation!, kind: 'enable' },
}

/** The states the mock draws, and the few the owner added (reconnect needed, no position, Apple Devices closed, locked). */
export const SCENES: Record<string, Scene> = {
  carplay: { output: outputs.carplayOn },
  'usb-unprepared': { output: outputs.usbUnprepared },
  'usb-no-cable': { output: outputs.usbPreparedNoCable },
  'usb-on': { output: outputs.usbOn },
  'usb-no-position': { output: outputs.usbNoPosition },
  'reconnect-needed': { output: outputs.reconnectNeeded, pending: ['gps.method'] },
  connect: { output: prepared, setup: setups.connect },
  drivers: { output: prepared, setup: setups.drivers },
  'apple-closed': { output: prepared, setup: setups.appleDevicesClosed },
  locked: { output: prepared, setup: setups.locked },
  trust: { output: prepared, setup: setups.trust },
  'dev-hidden': { output: prepared, setup: setups.developerHidden },
  'dev-revealed': { output: prepared, setup: setups.developerRevealed },
  'dev-lost': { output: prepared, setup: setups.developerLinkLost },
  'tools-missing': { output: prepared, setup: setups.toolsMissing },
  downloading: { output: prepared, setup: setups.downloading },
  'download-error': { output: prepared, setup: setups.downloadError },
  cached: { output: prepared, setup: setups.cached },
  connecting: { output: prepared, setup: setups.connecting },
  ready: { output: prepared, setup: setups.ready },
  'ready-enable': { output: prepared, setup: setups.readyEnable },
  starting: { output: starting },
  stopping: { output: outputs.stoppingUsb },
  'startup-missing': { output: outputs.usbStartupMissing, connected: false },
  'startup-missing-carplay': { output: outputs.usbStartupMissing, connected: true },
  'track-empty': { output: outputs.usbOn },
  'track-stopped': { output: outputs.usbOn, track: tracks.stopped },
  'track-playing': { output: outputs.usbOn, track: tracks.playing },
  'track-off': { output: outputs.usbOff, track: tracks.stopped },
  'track-unreadable': { output: outputs.usbTrackUnreadable, track: tracks.stopped },
  'experiments-empty': { output: outputs.carplayOff, section: 'Experiments' },
  'experiments-cached': { output: outputs.carplayOff, section: 'Experiments', tools: 16_680_000 },
}

function Preview() {
  const query = new URLSearchParams(location.search)
  const name = query.get('s') ?? 'carplay'
  const scene = SCENES[name] ?? SCENES.carplay
  const section = query.get('section') ?? scene.section ?? 'GPS'
  // ?update=1.1.1: the release the last update check found, offered on About
  const update = query.get('update')
  const real = useMemo(
    () => tracked(gpsState(scene.output, scene.setup, { reconnectPending: scene.pending ?? [], updateAvailable: update, ...(scene.connected === false ? { phase: 'idle' as const, canShowCarPlay: false } : {}) }), scene.track),
    [scene, update],
  )
  // The page opens its section while the window is free; the scene's own state, locked or not, comes after.
  const free = useMemo(() => tracked(gpsState({ ...scene.output, operation: null }, scene.setup, { phase: real.phase, canShowCarPlay: real.canShowCarPlay, reconnectPending: real.reconnectPending }), scene.track), [scene, real])
  const [state, setState] = useState(free)
  const backend = useMemo(() => {
    const fake = new FakeBackend()
    fake.data = { ...fake.data, values: { ...fake.data.values, 'gps.start': '50.45010, 30.52340', 'gps.heading': '90' } }
    if (scene.tools !== undefined) fake.sizes = { logs: 2_516_582, screenshots: 19_503_000, developerTools: scene.tools }
    else fake.sizes = { logs: 2_516_582, screenshots: 19_503_000 }
    return fake
  }, [scene])
  useEffect(() => {
    const timer = setInterval(() => {
      const tab = document.querySelector<HTMLButtonElement>(`[role=tab][aria-label="${section}"]`)
      if (!tab) return
      tab.click()
      clearInterval(timer)
      setTimeout(() => setState(real), 150)
    }, 50)
    return () => clearInterval(timer)
  }, [real, section])
  return <Shell state={state} backend={backend} />
}
createRoot(document.getElementById('root')!).render(<Preview />)
