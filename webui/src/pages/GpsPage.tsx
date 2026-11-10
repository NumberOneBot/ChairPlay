import { useId } from 'react'
import type { AppState, Backend } from '../api'
import { GpsNotices } from '../gps/GpsNotices'
import { iphoneStatus } from '../gps/iphoneStatus'
import { Locked } from '../gps/Locked'
import { EnableRow, MethodRows } from '../gps/MethodRows'
import { isLocked, outputOf } from '../gps/output'
import { UsbSetupWizard } from '../gps/UsbSetupWizard'
import { VirtualCar } from '../gps/VirtualCar'
import { Group, Row, Switch } from '../shell/controls'
import type { Settings } from '../shell/useSettings'
import { CityPresets } from './CityPresets'
import { Compass, HeadingField, PositionFields, placeOf } from './gpsFields'
import { Changed, parts, pendingOf } from './parts'
import { WindowsLocationButton } from './WindowsLocationButton'

/** What the iPhone learns as it connects: what it is sent besides the position. */
const LINK_KEYS = ['gps.wheel.speed']

/**
 * The spoofed GPS: whether the iPhone navigates by the car ChairPlay drives, and how the position reaches it (CarPlay
 * or a USB cable, which has its own setup); where the car is and which way it faces, following it as it drives; and the
 * car itself with its gauges. While the USB setup is open it takes the page's place.
 */
export function GpsPage({ state, backend, settings }: { state: AppState; backend: Backend; settings: Settings }) {
  const pending = pendingOf(state)
  const { note } = parts(settings, pending)
  const { value, save } = settings
  const output = outputOf(state, settings)
  const usb = output.selected === 'usb'
  const locked = isLocked(output)
  const setup = state.gps?.usbSetup
  // Where the car is and which way it faces now, while the app runs; else as saved.
  const heading = state.gps ? String(state.gps.heading) : value('gps.heading') || '0'
  const ids = useId()

  if (setup?.open) {
    return (
      <Locked locked={locked}>
        <UsbSetupWizard setup={setup} backend={backend} />
      </Locked>
    )
  }

  // Off, the GPS has nothing to set: the rest waits for it. So does a USB one that is not sending; the car waits for
  // a position too, while the position itself is what is missing, and stays open.
  const off = !output.enabledIntent
  const notSending = usb && output.availability !== 'ready'
  const positionHeld = off || (notSending && output.reason !== 'no-position')
  const carHeld = off || notSending
  const holdTitle = (held: boolean) =>
    !held
      ? null
      : off
        ? 'Turn on signal simulation to set it'
        : output.reason === 'no-position'
          ? 'Set a position first'
          : 'Connect the iPhone first'
  const sendRow = (key: string, label: string, title: string) => (
    <Row title={title}>
      <Switch label={label} checked={value(key) !== 'false'} onChange={(checked) => save({ [key]: String(checked) })} />
      <Changed keys={[key]} pending={pending} />
    </Row>
  )
  return (
    <>
      <Group title="GPS" note={note(LINK_KEYS)} />
      <GpsNotices output={output} backend={backend} />
      <Locked locked={locked}>
        <EnableRow output={output} backend={backend} />
        <MethodRows output={output} backend={backend} />
        {!usb && (
          <div className={off ? 'held' : undefined} inert={off} title={holdTitle(off) ?? undefined}>
            {sendRow(
              'gps.wheel.speed',
              'Send the vehicle speed',
              "The car's speed ten times a second, as a car's wheel sensors give it, so the iPhone moves on smoothly between position updates",
            )}
            {sendRow(
              'gps.heading.send',
              'Send the heading',
              'The car heading as the course in the position sentences, so the iPhone can face its arrow the way the car goes',
            )}
          </div>
        )}
        <Row label="iPhone">{iphoneStatus(output, state.gps)}</Row>
        {/* Where the car is, apart from the car itself, whose own settings grow. */}
        <Group title="Position" />
        <div
          className={positionHeld ? 'held' : undefined}
          inert={positionHeld}
          title={holdTitle(positionHeld) ?? undefined}
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
          {usb && <CityPresets settings={settings} place={placeOf(state.gps, value('gps.start'))} />}
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
        <VirtualCar state={state} backend={backend} settings={settings} held={carHeld ? holdTitle(true) : null} />
      </Locked>
    </>
  )
}
