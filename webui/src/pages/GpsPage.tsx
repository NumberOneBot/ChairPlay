import { useId } from 'react'
import type { AppState, Backend } from '../api'
import { GpsNotices } from '../gps/GpsNotices'
import { iphoneStatus } from '../gps/iphoneStatus'
import { EnableRow, MethodRows } from '../gps/MethodRows'
import { outputOf, usbGpsReady } from '../gps/output'
import { TrackPlayback } from '../gps/TrackPlayback'
import { trackOwnsCar } from '../gps/trackRules'
import { UsbSetupWizard } from '../gps/UsbSetupWizard'
import { VirtualCar } from '../gps/VirtualCar'
import { Group, Row } from '../shell/controls'
import type { Settings } from '../shell/useSettings'
import { CityPresets } from './CityPresets'
import { Compass, HeadingField, PositionFields, placeOf } from './gpsFields'
import { pendingOf } from './parts'
import { WindowsLocationButton } from './WindowsLocationButton'

/**
 * The spoofed GPS: whether the iPhone navigates by the car ChairPlay drives, and how the position reaches it (CarPlay
 * or a USB cable, which has its own setup); where the car is and which way it faces, following it as it drives; and the
 * car itself with its gauges. While the USB setup is open it takes the page's place.
 */
export function GpsPage({ state, backend, settings }: { state: AppState; backend: Backend; settings: Settings }) {
  const pending = pendingOf(state)
  const { value } = settings
  const output = outputOf(state, settings)
  const usb = output.selected === 'usb'
  const setup = state.gps?.usbSetup
  // Where the car is and which way it faces now, while the app runs; else as saved.
  const heading = state.gps ? String(state.gps.heading) : value('gps.heading') || '0'
  const ids = useId()

  if (setup?.open) {
    return (
      <UsbSetupWizard setup={setup} backend={backend} enabled={output.enabledIntent} />
    )
  }

  // Off, the GPS has nothing to set: the rest waits for it. So does a USB one that is not sending; the car waits for
  // a position too, while the position itself is what is missing, and stays open.
  const off = !output.enabledIntent
  // Until the reconnect the method in force is the old one: a USB that cannot send is the one in force.
  const notSending = usb && output.applied === 'usb' && output.availability !== 'ready'
  // With no place for the car at all, Position is how it gets one: it stays open whatever the switch says (L-09).
  const placed = placeOf(state.gps, value('gps.start')) !== null
  const positionHeld = placed && (off || (notSending && output.reason !== 'no-position'))
  const carHeld = off || notSending
  // The city presets and Track playback show for a ready USB GPS only, held with the rest while the switch is off.
  const usbReady = usbGpsReady(output)
  const holdTitle = (held: boolean) =>
    !held
      ? null
      : off
        ? 'Turn on signal simulation to set it'
        : output.reason === 'no-position'
          ? 'Set a position first'
          : 'Connect the iPhone first'
  // A recording playing or paused drives the car: Position and the car's own driving (power, mass, handling) wait for
  // Stop, only while the section and its Stop are on the page; Drive mode stays free, its keys blocked, its gauges
  // showing the recording (§8, owner).
  const trackDrives =
    usbReady && trackOwnsCar(state.gps?.track) ? 'Stop track playback to move the car yourself' : null
  const positionHold = holdTitle(positionHeld) ?? trackDrives
  // CarPlay's GPS sends the car's speed and heading too: gps.wheel.speed and gps.heading.send, on by default and a
  // hand edit only (owner, 2026-11-11).
  return (
    <>
      <Group title="GPS" />
      <GpsNotices output={output} backend={backend} />
      <>
        <EnableRow output={output} backend={backend} pending={pending} />
        <MethodRows output={output} backend={backend} pending={pending} />
        <Row label="iPhone">{iphoneStatus(output, state.gps)}</Row>
        {/* Where the car is, apart from the car itself, whose own settings grow. */}
        <Group title="Position" />
        <div
          className={positionHold ? 'held' : undefined}
          inert={positionHold !== null}
          title={positionHold ?? undefined}
          data-testid="gps-position-hold"
        >
          <Row>
            <WindowsLocationButton backend={backend} settings={settings} heading={heading} />
          </Row>
          <Row
            label="Coordinates"
            title="Where the car is now, following it as it drives; change it to move the car there, or paste a map link"
          >
            <PositionFields settings={settings} car={state.gps} heading={heading} />
          </Row>
          {usbReady && <CityPresets settings={settings} place={placeOf(state.gps, value('gps.start'))} />}
          {!usb && value('gps.heading.send') !== 'false' && (
            <Row
              label="Heading"
              htmlFor={`${ids}-heading`}
              title="Which way the car faces now: 0° is north, 90° east; change it to turn the car"
            >
              <HeadingField settings={settings} shown={heading} id={`${ids}-heading`} />
              <Compass heading={Number(heading) || 0} />
            </Row>
          )}
        </div>
        {usbReady && <TrackPlayback output={output} track={state.gps?.track ?? null} backend={backend} />}
        <VirtualCar
          state={state}
          backend={backend}
          settings={settings}
          held={carHeld ? holdTitle(true) : null}
          modelHeld={carHeld ? null : trackDrives}
        />
      </>
    </>
  )
}
