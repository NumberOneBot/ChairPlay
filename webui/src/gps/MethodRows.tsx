import type { Backend } from '../api'
import type { GpsOutput } from '../gpsApi'
import { Changed, type Pending } from '../pages/parts'
import { Row, Segmented, Switch, type Choice } from '../shell/controls'
import { UsbIcon } from './icons'

const METHODS: readonly Choice[] = [
  ['carplay', 'CarPlay'],
  ['usb', 'USB'],
]

/** USB saved as the method and the switch as on, with the phone off the cable: the switch holds, checked, until it is back. */
export function waitsForCable(output: GpsOutput): boolean {
  return (
    output.enabledIntent &&
    output.selected === 'usb' &&
    output.availability !== 'ready' &&
    output.reason === 'usb-not-connected'
  )
}

/** The one Enable signal simulation switch, whichever method carries it. */
export function EnableRow({ output, backend, pending }: { output: GpsOutput; backend: Backend; pending: Pending }) {
  // While USB starts or stops the switch waits; the rest of the window does not.
  const waiting = waitsForCable(output)
  const busy = output.operation !== null
  return (
    <Row title={waiting ? 'Connect your iPhone with a USB cable to resume simulation' : undefined}>
      <Switch
        label="Enable signal simulation"
        checked={output.enabledIntent}
        disabled={waiting || busy}
        onChange={(enabled) => void backend.gpsEnabled(enabled)}
      />
      <Changed keys={['gps.enabled']} pending={pending} />
    </Row>
  )
}

/**
 * How the position reaches the iPhone: CarPlay or USB. For USB, the advanced feature's hint, the button that opens
 * its setup, and, until a USB iPhone was ever prepared, what is left to do.
 */
export function MethodRows({ output, backend, pending }: { output: GpsOutput; backend: Backend; pending: Pending }) {
  return (
    <>
      <Row label="GPS method" title="Send the position over the CarPlay connection, or over a USB cable">
        <Segmented
          label="GPS method"
          value={output.selected}
          options={METHODS}
          testId="gps-method"
          onChange={(method) => void backend.gpsMethod(method as GpsOutput['selected'], output.generation)}
        />
        {/* USB's tag beside the choice, as a control's own tag is (AltScreen, Audio); CarPlay's has none. */}
        {output.selected === 'usb' && <span className="exp">Advanced</span>}
        {/* A method chosen applies at the next connection (D8), marked as any such setting. */}
        <Changed keys={['gps.method']} pending={pending} />
      </Row>
      {output.selected === 'carplay' && (
        <Row className="hint-row">
          <span className="hint">
            In a real car, CarPlay GPS refines the iPhone’s position with the car’s stronger antenna, so the iPhone
            accepts it only within about 80 km (50 mi) of its real location.
          </span>
        </Row>
      )}
      {output.selected === 'usb' && (
        <>
          <Row className="hint-row">
            <span className="hint">
              Requires enabling Developer Mode and downloading additional developer tools. CarPlay stays connected
              over Wi-Fi; GPS coordinates are sent over USB.
            </span>
          </Row>
          <Row>
            <button
              type="button"
              className="btn small"
              title="Set up the iPhone for USB GPS"
              onClick={() => void backend.openUsbSetup(output.enabledIntent ? 'resume' : 'prepare')}
            >
              <UsbIcon />
              USB setup
            </button>
          </Row>
        </>
      )}
    </>
  )
}
