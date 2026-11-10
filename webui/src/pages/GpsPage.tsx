import { useId } from 'react'
import type { AppState, Backend, GpsState } from '../api'
import { keyLabel } from '../ControlsScreen'
import { Group, Row, Segmented, Switch, type Choice } from '../shell/controls'
import type { Settings } from '../shell/useSettings'
import { Compass, HeadingField, PositionFields } from './gpsFields'
import { Changed, parts, pendingOf } from './parts'
import { WindowsLocationButton } from './WindowsLocationButton'

/** What the iPhone learns as it connects: whether there is a GPS, and what it sends besides the position. */
const LINK_KEYS = ['gps.enabled', 'gps.wheel.speed']

/** gps.rate: fixes a second on the tunnel; the iPhone asks for one every 250 ms. */
const RATES: readonly Choice[] = [
  ['1', '1', "DiPlay's own rate"],
  ['4', '4', 'As the iPhone asks'],
  ['10', '10'],
]

/** gps.car.handling: how the car turns. */
const HANDLING: readonly Choice[] = [
  ['gentle', 'Gentle'],
  ['normal', 'Normal'],
  ['sharp', 'Sharp'],
]

/** gps.gauges: how large drive mode's gauges show. */
const GAUGES: readonly Choice[] = [
  ['large', 'Large'],
  ['small', 'Small'],
]

/** gps.gauges.style: how the dials are graduated and their needles colored. */
const GAUGE_STYLES: readonly Choice[] = [
  ['german', 'German', 'Both dials clockwise over the top, the needles in the accent color'],
  ['italian', 'Italian', 'Both dials from 6 o’clock to 3, with yellow needles'],
  ['british', 'British', 'The dials turn apart, the speedometer from 5 o’clock to 1 and the tachometer back from 7 to 11, with green needles'],
]

/** gps.wheel: what the gauges' steering wheel shows. */
const WHEEL: readonly Choice[] = [
  ['steering', 'Steering'],
  ['heading', 'Heading'],
]

/**
 * What the iPhone takes, for the iPhone row under the fixes; and how the car goes and what drives it, for the Car
 * group's note (where it is and which way it faces are in the fields).
 */
function status(car: GpsState, imperial: boolean) {
  const updates = `${car.sent} ${car.sent === 1 ? 'update' : 'updates'} sent`
  const speed = imperial ? `${Math.round(car.speed / 1.609344)} mph` : `${car.speed} km/h`
  return {
    iphone: car.listening ? `Listening: ${(car.sentences ?? []).join(', ')}; ${updates}` : 'Not listening',
    going: [speed, `${car.rpm} rpm`, car.gear, car.gamepad ? 'Gamepad connected' : 'No gamepad'].join(
      ' · ',
    ),
  }
}

/**
 * The spoofed GPS: whether the iPhone navigates by the car ChairPlay drives, and what it is told besides the position;
 * where the car is and which way it faces, following it as it drives; the car itself and its gauges; and how it goes
 * and what the iPhone takes. The city presets (CityPresets) are off the page for now.
 */
export function GpsPage({ state, backend, settings }: { state: AppState; backend: Backend; settings: Settings }) {
  const pending = pendingOf(state)
  const { field, note } = parts(settings, pending)
  const { value, save } = settings
  const off = value('gps.enabled') !== 'true'
  const driveKey = state.controls?.keys.drive
  const gaugesKey = state.controls?.keys.gauges
  const now = state.gps && status(state.gps, value('gps.units') === 'imperial')
  // Where the car is and which way it faces now, while the app runs; else as saved.
  const heading = state.gps ? String(state.gps.heading) : value('gps.heading') || '0'
  const ids = useId()
  /** A switch for [key]; [on] is what a blank value means, as the app reads it. */
  const check = (key: string, label: string, title?: string, on = false, experimental = false) => (
    <Row title={title}>
      <Switch
        label={label}
        checked={on ? value(key) !== 'false' : value(key) === 'true'}
        onChange={(checked) => save({ [key]: String(checked) })}
      />
      {experimental && <span className="exp">Experimental</span>}
      <Changed keys={[key]} pending={pending} />
    </Row>
  )
  return (
    <>
      <Group title="GPS" note={note(LINK_KEYS)} />
      {check('gps.enabled', 'Enable spoofing', undefined, false, true)}
      <Row className="hint-row">
        <span className="hint">
          The iPhone gets a fake GPS signal in place of the car's: the position of a car ChairPlay drives, with a
          gamepad or with the arrows in drive mode{driveKey ? ` (${keyLabel(driveKey)})` : ''}
        </span>
      </Row>
      {/* Off, the GPS has nothing to set: the rest waits for it. */}
      <div className={off ? 'held' : undefined} inert={off} title={off ? 'Turn on the GPS to set it' : undefined}>
        {check(
          'gps.wheel.speed',
          'Send the vehicle speed',
          "The car's speed ten times a second, as a car's wheel sensors give it, so the iPhone moves on smoothly between position updates",
          true,
        )}
        {check(
          'gps.heading.send',
          'Send the heading',
          'The car heading as the course in the position sentences, so the iPhone can face its arrow the way the car goes',
          true,
        )}
        {/* Experiment in what the iPhone takes, from the next fix: how often the position goes. */}
        <Row label="Updates" title="How often the car's position goes to the iPhone while it listens">
          <Segmented
            label="Updates"
            value={value('gps.rate') || '4'}
            options={RATES}
            onChange={(rate) => save({ 'gps.rate': rate })}
            className="half"
          />
          <span className="unit">a second</span>
        </Row>
        {now && <Row label="iPhone">{now.iphone}</Row>}
        {/* Where the car is, apart from the car itself, whose own settings grow. */}
        <Group title="Position" />
        <Row>
          <WindowsLocationButton backend={backend} settings={settings} heading={heading} />
        </Row>
        <Row
          label="Coordinates"
          title="Where the car is now, following it as it drives; change it to move the car there, or paste a map link"
        >
          <PositionFields settings={settings} car={state.gps} heading={heading} />
        </Row>
        {value('gps.heading.send') !== 'false' && (
          <Row
            label="Heading"
            htmlFor={`${ids}-heading`}
            title="Which way the car faces now: 0° is north, 90° east; change it to turn the car"
          >
            <HeadingField settings={settings} shown={heading} id={`${ids}-heading`} />
            <Compass heading={Number(heading) || 0} />
          </Row>
        )}
        <Group title="Virtual car" note={now?.going} />
        <Row
          title={`Drive the GPS car with the arrow keys and show its speedometer and tachometer on the AltScreen${driveKey ? ` (${keyLabel(driveKey)})` : ''}. Up and Down are the pedals, Left and Right steer, and the arrows stop moving through CarPlay until drive mode is off.`}
        >
          <Switch
            label="Drive mode"
            checked={state.gps?.keyboard ?? false}
            onChange={(on) => void backend.driveMode(on)}
          />
          <span className="exp">Experimental</span>
          <span className="exp fun">Extra fun</span>
        </Row>
        <Row label="Power and mass" title="The car's power and mass, which set how it pulls and how fast it goes">
          {field('gps.car.power.hp', 'Power', '300')}
          <span className="unit">hp</span>
          {field('gps.car.mass.kg', 'Mass', '1500')}
          <span className="unit">kg</span>
        </Row>
        <Row label="Handling" title="How fast the car turns standing and slow, and how well it holds a fast corner">
          <Segmented
            label="Handling"
            value={value('gps.car.handling') || 'normal'}
            options={HANDLING}
            onChange={(handling) => save({ 'gps.car.handling': handling })}
          />
        </Row>
        <Row label="Speed units" title="Choose kilometers or miles per hour for the gauges and speed readings">
          <Segmented
            label="Speed units"
            value={value('gps.units') || 'metric'}
            options={[
              ['metric', 'km/h'],
              ['imperial', 'mph'],
            ]}
            onChange={(units) => save({ 'gps.units': units })}
          />
        </Row>
        <Row
          label="Wheel"
          title="Which way the gauges' steering wheel points: the car's heading (north up) or its steering input (track up)"
        >
          <Segmented
            label="Wheel"
            value={value('gps.wheel') || 'steering'}
            options={WHEEL}
            onChange={(wheel) => save({ 'gps.wheel': wheel })}
          />
        </Row>
        <Row className="hint-row">
          <span className="hint">
            Steering — track-up maps (Google Maps): the wheel follows input, car fixed up.
            <br />
            Heading — north-up maps (Apple Maps): the wheel shows the car's course.
          </span>
        </Row>
        <Row
          label="Gauges"
          title={`The speedometer and tachometer in drive mode: as tall as the AltScreen, or small in its lower corners${gaugesKey ? ` (${keyLabel(gaugesKey)})` : ''}`}
        >
          <Segmented
            label="Gauges"
            value={value('gps.gauges') || 'large'}
            options={GAUGES}
            onChange={(size) => save({ 'gps.gauges': size })}
          />
        </Row>
        <Row label="Gauge style" title="How drive mode's dials are graduated and their needles colored">
          <Segmented
            label="Gauge style"
            value={value('gps.gauges.style') || 'german'}
            options={GAUGE_STYLES}
            onChange={(style) => save({ 'gps.gauges.style': style })}
          />
        </Row>
      </div>
    </>
  )
}
