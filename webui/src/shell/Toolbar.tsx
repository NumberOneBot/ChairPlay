import type { ReactNode } from 'react'
import type { AppState, Backend, WindowName } from '../api'
import { keyLabel } from '../ControlsScreen'
import type { Steps } from '../setup/steps'
import { describe, type Status } from '../status'
import { AltScreenIcon, CarPlayIcon, ControlsIcon, DisconnectIcon, ReconnectIcon } from './icons'
import { PendingMark } from './PendingMark'
import { SECTIONS, sectionsOf } from './sections'
import { networkWaits } from '../pages/NetworkConnection'

const DOT = { ok: 'ok', busy: 'busy', warn: 'warn', idle: '' } as const

/**
 * The bar over every page: CarPlay and the app's other windows at the left, which act on the app's own windows only
 * (a tab never shows CarPlay itself); at the right what waits for a restart, where the connection stands, and Connect,
 * or Reconnect and Disconnect once connecting has begun. In a narrow window the window buttons show their icons alone,
 * and the status gives way before the buttons do.
 */
export function Toolbar({
  state,
  backend,
  guide,
  locked,
}: {
  state: AppState
  backend: Backend
  guide?: Steps | null
  /** An operation holds the window: the bar neither takes the pointer nor the keys. */
  locked?: boolean
}) {
  const session = state.canShowCarPlay
  const titles = (keys: string[]) => sectionsOf(keys).map((id) => SECTIONS.find((section) => section.id === id)!.title)
  const restart = titles(state.restartPending)
  const reconnect = titles(state.reconnectPending)
  const idle = state.phase === 'setup' || state.phase === 'idle'
  const reconnectKey = state.controls?.keys.reconnect
  // Under Get started the status says where the steps stand, and Connect waits for the last of them, then lights up.
  const status: Status =
    guide && idle
      ? guide.allDone
        ? { title: 'Not connected', detail: 'Every step is done.', tone: 'idle' }
        : { title: 'Not set up', detail: 'Finish the steps below.', tone: 'warn' }
      : describe(state)
  const connectWaits = networkWaits(state) ?? (guide ? guide.connectWaits : state.phase === 'setup' ? 'Enter the iPhone Wi-Fi network first' : null)
  return (
    <header className="tb" inert={locked} aria-busy={locked || undefined}>
      <button
        type="button"
        className="btn"
        aria-label="CarPlay"
        disabled={!session}
        title={!session ? 'Connect the iPhone first' : state.embedded ? 'Show CarPlay (Esc)' : "Show CarPlay in ChairPlay's window"}
        onClick={() => backend.show('carplay')}
      >
        <CarPlayIcon />
        <span className="label">CarPlay</span>
      </button>
      <WindowButton state={state} backend={backend} name="altscreen" label="AltScreen" icon={<AltScreenIcon />} />
      <WindowButton state={state} backend={backend} name="controls" label="Controls" icon={<ControlsIcon />} />
      <span className="grow" />
      {/* The signs alone: at 640 px "Restart needed" and the connection's status never shared the bar whole */}
      {reconnect.length > 0 && (
        <PendingMark
          what="reconnect"
          className="pending"
          label="Reconnect needed"
          title={`Reconnect to apply: ${reconnect.join(', ')}`}
        />
      )}
      {restart.length > 0 && (
        <PendingMark
          what="restart"
          className="pending"
          label="Restart needed"
          title={`Restart ChairPlay to apply: ${restart.join(', ')}`}
        />
      )}
      {(reconnect.length > 0 || restart.length > 0) && <span className="sep" />}
      <span className="chip" title={status.detail}>
        <span className={`dot ${DOT[status.tone]}`} />
        <b>{status.title}</b>
      </span>
      <span className="sep" />
      {idle ? (
        <button
          type="button"
          className={`btn primary ${guide?.allDone ? 'lit' : ''}`}
          disabled={connectWaits !== null}
          title={connectWaits ?? 'Connect to the iPhone'}
          onClick={() => backend.connect()}
        >
          Connect
        </button>
      ) : (
        <>
          <button
            type="button"
            className="btn icon"
            aria-label="Reconnect"
            title={`Reconnect: end the session and connect again${reconnectKey ? ` (${keyLabel(reconnectKey)})` : ''}`}
            onClick={() => backend.reconnect()}
          >
            <ReconnectIcon />
          </button>
          <button
            type="button"
            className="btn icon"
            aria-label="Disconnect"
            title="Disconnect: end the CarPlay session; connect again with Connect"
            onClick={() => backend.disconnect()}
          >
            <DisconnectIcon />
          </button>
        </>
      )}
    </header>
  )
}

/**
 * Shows or hides one of the app's other windows, lit while it is shown; off without a session, or while its feature
 * is turned off. It changes only when the app's state says the window did, not on the click.
 */
function WindowButton(props: { state: AppState; backend: Backend; name: WindowName; label: string; icon: ReactNode }) {
  const { available, visible } = props.state.windows[props.name]
  const session = props.state.canShowCarPlay
  const shown = session && available && visible
  const title = !session
    ? 'Connect the iPhone first'
    : !available
      ? `Turn on the ${props.label} on its page first`
      : `Show or hide the ${props.name === 'controls' ? 'controls' : props.label} window`
  return (
    <button
      type="button"
      className={`btn ${shown ? 'on' : ''}`}
      aria-label={props.label}
      aria-pressed={shown}
      disabled={!session || !available}
      title={title}
      onClick={() => props.backend.window(props.name, !visible)}
    >
      {props.icon}
      <span className="label">{props.label}</span>
    </button>
  )
}
