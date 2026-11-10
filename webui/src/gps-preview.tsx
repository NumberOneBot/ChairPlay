// Development-only entry point: the GPS page, the USB setup and Experiments on the fixtures (gpsFixtures.ts), with no
// app behind them. ?s=<scene>&section=<GPS|Experiments> picks what shows; scenes is the list of them, for the check.
import { useEffect, useMemo, useState } from 'react'
import { createRoot } from 'react-dom/client'
import { FakeBackend } from './fakeBackend'
import type { GpsOutput, UsbSetupView } from './gpsApi'
import { outputs, setups } from './gpsFixtures'
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
}

const later = Date.now() + 3_600_000
const resetSent: GpsOutput = {
  ...outputs.resetSent,
  notices: outputs.resetSent.notices.map((each) => ({ ...each, expiresAt: later })),
}
const returning: GpsOutput = {
  ...outputs.stoppingUsb,
  operation: { ...outputs.stoppingUsb.operation!, kind: 'disable', stage: 'return' },
}

export const SCENES: Record<string, Scene> = {
  carplay: { output: outputs.carplayOn },
  'carplay-off': { output: outputs.carplayOff },
  'usb-unprepared': { output: outputs.usbUnprepared },
  'usb-no-cable': { output: outputs.usbPreparedNoCable },
  'usb-on': { output: outputs.usbOn },
  'usb-no-position': { output: outputs.usbNoPosition },
  connect: { output: outputs.usbUnprepared, setup: setups.connect },
  drivers: { output: outputs.usbUnprepared, setup: setups.drivers },
  'apple-closed': { output: outputs.usbUnprepared, setup: setups.appleDevicesClosed },
  several: { output: outputs.usbUnprepared, setup: setups.several },
  trust: { output: outputs.usbUnprepared, setup: setups.trust },
  'dev-hidden': { output: outputs.usbUnprepared, setup: setups.developerHidden },
  'dev-revealed': { output: outputs.usbUnprepared, setup: setups.developerRevealed },
  'dev-lost': { output: outputs.usbUnprepared, setup: setups.developerLinkLost },
  'tools-missing': { output: outputs.usbUnprepared, setup: setups.toolsMissing },
  downloading: { output: outputs.usbUnprepared, setup: setups.downloading },
  'download-error': { output: outputs.usbUnprepared, setup: setups.downloadError },
  cached: { output: outputs.usbUnprepared, setup: setups.cached },
  connecting: { output: outputs.usbUnprepared, setup: setups.connecting },
  ready: { output: outputs.usbUnprepared, setup: setups.ready },
  'ready-enable': { output: outputs.usbUnprepared, setup: setups.readyEnable },
  'ready-warning': { output: outputs.usbUnprepared, setup: setups.readyWindowsWarning },
  'ready-no-position': { output: outputs.usbUnprepared, setup: setups.readyNoPosition },
  'switch-usb': { output: outputs.switchingToUsb },
  'switch-carplay': { output: outputs.switchingToCarplay },
  returning: { output: returning },
  stopping: { output: outputs.stoppingUsb },
  'reset-sent': { output: resetSent },
  'startup-missing': { output: outputs.usbStartupMissing, connected: false },
  'startup-missing-carplay': { output: outputs.usbStartupMissing, connected: true },
  'startup-missing-experiments': { output: outputs.usbStartupMissing, section: 'Experiments', tools: 16_680_000 },
  'reconnect-needed': { output: outputs.reconnectNeeded },
  'windows-unavailable': { output: outputs.windowsUnavailable },
  'cleanup-unconfirmed': { output: outputs.cleanupUnconfirmed },
  'experiments-empty': { output: outputs.carplayOff, section: 'Experiments' },
  'experiments-cached': { output: outputs.carplayOff, section: 'Experiments', tools: 16_680_000 },
  'experiments-locked': { output: outputs.stoppingUsb, section: 'Experiments', tools: 16_680_000 },
}

function Preview() {
  const query = new URLSearchParams(location.search)
  const name = query.get('s') ?? 'carplay'
  const scene = SCENES[name] ?? SCENES.carplay
  const section = query.get('section') ?? scene.section ?? 'GPS'
  const real = useMemo(
    () => gpsState(scene.output, scene.setup, scene.connected === false ? { phase: 'idle', canShowCarPlay: false } : {}),
    [scene],
  )
  // The page opens its section while the window is free; the scene's own state, locked or not, comes after.
  const free = useMemo(() => gpsState({ ...scene.output, operation: null }, scene.setup, { phase: real.phase, canShowCarPlay: real.canShowCarPlay }), [scene, real])
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
