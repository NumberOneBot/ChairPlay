import type { AppState, Backend } from '../api'
import { Message, Row } from '../shell/controls'
import { Select } from '../shell/fields'
import type { Settings } from '../shell/useSettings'
import { Changed, pendingOf } from './parts'

/** The PC is on Wi-Fi Windows hides, with no cable: said under the Wi-Fi hint rather than as a PC connection. */
function wifiHidden(state: AppState): boolean {
  const { setup } = state
  return !setup.pcWifi && !!setup.wifiHidden && !setup.networkError && setup.ethernet?.length === 0
}

/** Under the Wi-Fi hint: Windows hides this PC's Wi-Fi network, and where to let ChairPlay see it. */
export function WifiHidden({ state, backend }: { state: AppState; backend: Backend }) {
  if (!wifiHidden(state)) return null
  return (
    <Message
      tone="warn"
      head="Windows hides this PC's Wi-Fi network from ChairPlay."
      text="On this PC, open Settings › Privacy & security › Location and turn on Let desktop apps access your location."
    >
      <div className="acts">
        <button type="button" className="btn small" title="Open location settings" onClick={() => void backend.open('location')}>
          Open location settings
        </button>
      </div>
    </Message>
  )
}

export function showNetworkConnection(state: AppState): boolean {
  const { setup } = state
  if (setup.pcWifi || setup.ethernet === undefined || wifiHidden(state)) return false
  if (setup.networkError) return true
  return setup.ethernet.length !== 1 || setup.ethernetSelected !== setup.ethernet[0].id
}

/** The same adapter selection in Get started and Connection; credentials belong to the iPhone below it. */
export function NetworkConnection({ state, settings }: { state: AppState; settings: Settings }) {
  const { setup } = state
  if (!showNetworkConnection(state)) return null
  const adapters = setup.ethernet ?? []
  const selected = setup.ethernetSelected ?? ''
  return <>
    {adapters.length > 0 ? (
      <Row label="Ethernet">
        <Select label="Ethernet adapter" className="ethernet-select" value={selected}
          options={[["", "Choose an adapter"], ...adapters.map(a => [a.id, `${a.label} · ${a.addresses.join(', ')}`] as const)]}
          onChange={(id) => settings.save({ 'network.ethernet.adapter': id })} />
        <Changed keys={['network.ethernet.adapter']} pending={pendingOf(state)} />
      </Row>
    ) : <Row><span className="errtext">{setup.networkError
      ? 'Could not read Ethernet adapters. Check the adapter in Windows network settings.'
      : 'No Ethernet address is available. Connect this PC to the router with a network cable.'}</span></Row>}
  </>
}

export function networkWaits(state: AppState): string | null {
  if (state.setup.pcWifi || state.setup.ethernet === undefined) return null
  if (state.setup.networkError) return 'Check the network adapter in Windows first'
  if (!state.setup.ethernet.length) {
    return state.setup.wifiHidden ? 'Turn on location for desktop apps first' : 'Connect a network cable first'
  }
  if (!state.setup.ethernetSelected) return 'Select an Ethernet adapter first'
  return null
}
