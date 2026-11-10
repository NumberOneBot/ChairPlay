import type { ReactNode } from 'react'
import type { Backend } from '../api'
import type { SetupAction, SetupStep, UsbSetupView } from '../gpsApi'
import { byteSize } from '../pages/byteSize'
import { Message } from '../shell/controls'
import { ErrorIcon, StoreIcon } from './icons'
import { setupError } from './setupErrors'

const Ref = ({ children }: { children: ReactNode }) => <span className="ref">{children}</span>
const Wait = ({ children }: { children: ReactNode }) => <span className="waiting">{children}</span>

/** What a step's body can ask of the app: an action of the open wizard, and the Store link. */
interface Ask {
  run(action: SetupAction, step?: SetupStep): void
  open: Backend['open']
  disabled: boolean
}

function Button({ label, title, ask, action, step, icon }: {
  label: string
  title: string
  ask: Ask
  action: SetupAction
  step?: SetupStep
  icon?: ReactNode
}) {
  return (
    <button type="button" className="btn" title={title} disabled={ask.disabled} onClick={() => ask.run(action, step)}>
      {icon}
      {label}
    </button>
  )
}

/** A step's error: the red triangle and the reason, the instruction under it, and Try again for the same step. */
function StepError({ step, code, ask }: { step: SetupStep; code: string; ask: Ask }) {
  const [reason, instruction] = setupError(code)
  return (
    <>
      <div className="step-error" role="alert">
        <ErrorIcon />
        <span>{reason}</span>
      </div>
      <span>{instruction}</span>
      <div className="acts">
        <Button label="Try again" title="Check this step again" ask={ask} action="retry" step={step} />
      </div>
    </>
  )
}

function Progress({ download }: { download: NonNullable<UsbSetupView['download']> }) {
  const { receivedBytes: received, totalBytes: total } = download
  return (
    <div className="acts">
      <progress aria-label="Download progress" value={total ? received : undefined} max={total ?? undefined} />
      <span className="waiting">
        Downloading… {byteSize(received)}
        {total ? ` of ${byteSize(total)}` : ''}
      </span>
    </div>
  )
}

function UsbBody({ detail, ask }: { detail: string | null; ask: Ask }) {
  if (detail === 'drivers-missing')
    return (
      <>
        <span>
          Install <Ref>Apple Devices</Ref> from <Ref>Microsoft Store</Ref>, or <Ref>iTunes</Ref>, to add the iPhone USB
          drivers. Then reconnect your iPhone.
        </span>
        <div className="acts">
          <button
            type="button"
            className="btn"
            title="Open Apple Devices in Microsoft Store"
            onClick={() => void ask.open('apple-devices')}
          >
            <StoreIcon />
            Get Apple Devices
          </button>
          <Wait>Waiting for the drivers…</Wait>
        </div>
      </>
    )
  if (detail === 'apple-devices-closed')
    return (
      <>
        <span>
          <Ref>Apple Devices</Ref> is installed but not running. Open it, then check again.
        </span>
        <div className="acts">
          <Button label="Check again" title="Check for the iPhone again" ask={ask} action="retry" step="usb" />
        </div>
      </>
    )
  if (detail === 'locked')
    return (
      <>
        <span>Unlock your iPhone.</span>
        <Wait>Waiting for your iPhone…</Wait>
      </>
    )
  return (
    <>
      <span>Connect a data-capable USB cable and unlock your iPhone.</span>
      <Wait>Waiting for your iPhone…</Wait>
    </>
  )
}

function DeveloperModeBody({ detail, ask }: { detail: string | null; ask: Ask }) {
  if (detail === 'hidden')
    return (
      <>
        <span>
          On recent iOS versions, <Ref>Developer Mode</Ref> is hidden until a developer connection is requested. The
          button below makes it appear in <Ref>Settings → Privacy &amp; Security</Ref>. Turn it on and confirm on your
          iPhone.
        </span>
        <div className="acts">
          <Button label="Show Developer Mode" title="Make Developer Mode appear on the iPhone" ask={ask} action="reveal" />
        </div>
      </>
    )
  return (
    <>
      <span>
        Open <Ref>Developer Mode</Ref>, turn it on, and restart your iPhone. After restarting, unlock it and tap{' '}
        <Ref>Turn On</Ref>.
      </span>
      <Wait>
        {detail === 'link-lost'
          ? 'Connection to your iPhone was lost. Waiting to reconnect…'
          : 'Waiting for Developer Mode…'}
      </Wait>
    </>
  )
}

function ToolsBody({ setup, ask }: { setup: UsbSetupView; ask: Ask }) {
  const { detail } = setup.steps.tools
  if (detail === 'downloading')
    return setup.download ? <Progress download={setup.download} /> : <Wait>Downloading…</Wait>
  if (detail === 'connecting') return <Wait>Connecting…</Wait>
  if (detail === 'cached')
    return (
      <>
        <span>
          A compatible package is already downloaded. Connect it to this iPhone to enable USB GPS simulation.
        </span>
        <div className="acts">
          <Button label="Connect" title="Connect the package to the iPhone" ask={ask} action="tools" />
        </div>
      </>
    )
  if (detail === 'unsupported-ios')
    return <span>ChairPlay has no developer tools for this iOS version yet.</span>
  return (
    <>
      <span>
        Download the package for your iOS version. ChairPlay will mount it on your iPhone to enable USB GPS simulation.
      </span>
      <div className="acts">
        <Button label="Download" title="Download the developer tools for this iPhone" ask={ask} action="tools" />
      </div>
    </>
  )
}

function ReadyBody({ setup }: { setup: UsbSetupView }) {
  return (
    <>
      {setup.windowsWarning && (
        <Message
          tone="warn"
          head="Windows location unavailable"
          text="Simulation can start from the current virtual position. Automatic return to the Windows location is unavailable."
        />
      )}
      <span>Your iPhone is prepared.</span>
      <ul className="checks">
        {setup.device && <li>{setup.device.name}</li>}
        <li>Developer Mode on</li>
        <li>Developer tools ready</li>
      </ul>
      {!setup.positionValid && <span>The car needs a position before USB GPS sends. Set one on the GPS page.</span>}
    </>
  )
}

/** What the open step shows and asks for: its instruction and action, or its error in their place. */
export function SetupStepBody({ step, setup, backend }: { step: SetupStep; setup: UsbSetupView; backend: Backend }) {
  const ask: Ask = {
    run: (action, forStep) => void backend.usbSetupAction(setup.operationId!, action, forStep),
    open: (place) => backend.open(place),
    disabled: setup.operationId === null,
  }
  const { status, error } = setup.steps[step]
  if (status === 'error' && error) return <StepError step={step} code={error} ask={ask} />
  switch (step) {
    case 'usb':
      return <UsbBody detail={setup.steps.usb.detail} ask={ask} />
    case 'trust':
      return setup.steps.trust.detail === 'locked' ? (
        <>
          <span>Unlock your iPhone to continue.</span>
          <Wait>Waiting for your iPhone…</Wait>
        </>
      ) : (
        <>
          <span>
            On your iPhone, tap <Ref>Trust</Ref> and enter the device passcode when asked.
          </span>
          <Wait>Waiting for Trust…</Wait>
        </>
      )
    case 'developer-mode':
      return <DeveloperModeBody detail={setup.steps['developer-mode'].detail} ask={ask} />
    case 'tools':
      return <ToolsBody setup={setup} ask={ask} />
    case 'ready':
      return <ReadyBody setup={setup} />
  }
}
