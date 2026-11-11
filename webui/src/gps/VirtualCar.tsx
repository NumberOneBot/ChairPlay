import type { AppState, Backend } from '../api'
import { keyLabel } from '../ControlsScreen'
import { Group, Row, Segmented, Switch, type Choice } from '../shell/controls'
import type { Settings } from '../shell/useSettings'
import { parts } from '../pages/parts'

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
  [
    'british',
    'British',
    'The dials turn apart, the speedometer from 5 o’clock to 1 and the tachometer back from 7 to 11, with green needles',
  ],
]

/** gps.wheel: what the gauges' steering wheel shows. */
const WHEEL: readonly Choice[] = [
  ['steering', 'Steering'],
  ['heading', 'Heading'],
]

/** The hint under Wheel, for the choice made only, as GPS method's is. */
const WHEEL_HINTS: Readonly<Record<string, string>> = {
  steering: 'Track-up maps (Google Maps): the wheel follows input, car fixed up.',
  heading: 'North-up maps (Apple Maps): the wheel shows the car’s course.',
}

/** How the car goes and what drives it, for the Virtual car group's note. */
function going(state: AppState, imperial: boolean): string | undefined {
  const car = state.gps
  if (!car) return undefined
  const speed = imperial ? `${Math.round(car.speed / 1.609344)} mph` : `${car.speed} km/h`
  return [speed, `${car.rpm} rpm`, car.gear, car.gamepad ? 'Gamepad connected' : 'No gamepad'].join(' · ')
}

/** The car itself and its gauges. [held] says why it waits, or null when it does not. */
export function VirtualCar({
  state,
  backend,
  settings,
  held,
}: {
  state: AppState
  backend: Backend
  settings: Settings
  held: string | null
}) {
  const { value, save } = settings
  const { field } = parts(settings)
  const driveKey = state.controls?.keys.drive
  const gaugesKey = state.controls?.keys.gauges
  const wheel = value('gps.wheel') || 'steering'
  return (
    <>
      <Group title="Virtual car" note={going(state, value('gps.units') === 'imperial')} />
      <div className={held ? 'held' : undefined} inert={held !== null} title={held ?? undefined}>
        <Row
          title={`Drive the GPS car with the arrow keys and show its speedometer and tachometer on the AltScreen${driveKey ? ` (${keyLabel(driveKey)})` : ''}. Up and Down are the pedals, Left and Right steer, and the arrows stop moving through CarPlay until drive mode is off.`}
        >
          <Switch
            label="Drive mode"
            checked={state.gps?.keyboard ?? false}
            onChange={(on) => void backend.driveMode(on)}
          />
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
            value={wheel}
            options={WHEEL}
            onChange={(choice) => save({ 'gps.wheel': choice })}
          />
        </Row>
        <Row className="hint-row">
          <span className="hint">{WHEEL_HINTS[wheel] ?? WHEEL_HINTS.steering}</span>
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
