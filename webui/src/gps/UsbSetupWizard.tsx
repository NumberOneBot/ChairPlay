import type { Backend } from '../api'
import type { SetupStep, UsbSetupView } from '../gpsApi'
import { Step } from '../setup/GetStarted'
import type { StepState } from '../setup/steps'
import { BackIcon } from './icons'
import { SetupStepBody } from './SetupStepBody'

const STEPS: readonly (readonly [SetupStep, title: string])[] = [
  ['usb', 'Connect iPhone'],
  ['trust', 'Trust this PC'],
  ['developer-mode', 'Developer Mode'],
  ['tools', 'Developer tools'],
  ['ready', 'Ready'],
]

/**
 * Getting an iPhone ready for USB GPS, in the GPS page's place: its header is the way back, the five steps keep their
 * titles whatever happens in them (the one the app is at is open, those before it are done, those after it wait), and
 * Cancel and, once ready, the final action stay at the bottom. Everything shown is the app's state; the buttons only ask.
 */
export function UsbSetupWizard({ setup, backend }: { setup: UsbSetupView; backend: Backend }) {
  const at = STEPS.findIndex(([name]) => name === setup.current)
  const ask = (action: 'cancel' | 'enable' | 'done') => {
    if (setup.operationId !== null) void backend.usbSetupAction(setup.operationId, action)
  }
  const ready = setup.current === 'ready'
  return (
    <div className="usb-setup">
      <h3 className="grp back">
        <button type="button" title="Cancel the setup and go back" onClick={() => ask('cancel')}>
          <BackIcon />
          USB GPS setup
        </button>
      </h3>
      {STEPS.map(([name, title], index) => {
        const state: StepState = index < at || (name === 'ready' && ready) ? 'done' : index === at ? 'now' : 'todo'
        const note = state === 'done' ? (name === 'usb' ? (setup.device?.name ?? 'Ready') : 'Ready') : ''
        return (
          <Step key={name} n={index + 1} state={state} title={title} note={note} open={name === 'ready' && ready}>
            <SetupStepBody step={name} setup={setup} backend={backend} />
          </Step>
        )
      })}
      <div className="setup-foot">
        <button type="button" className="btn" title="Close the setup" onClick={() => ask('cancel')}>
          Cancel
        </button>
        {ready && (
          <button
            type="button"
            className="btn primary"
            title={setup.intent === 'prepare' ? 'Finish the setup without starting the simulation' : 'Start sending the position over USB'}
            onClick={() => ask(setup.intent === 'prepare' ? 'done' : 'enable')}
          >
            {setup.intent === 'prepare' ? 'Done' : 'Enable USB GPS'}
          </button>
        )}
      </div>
    </div>
  )
}
