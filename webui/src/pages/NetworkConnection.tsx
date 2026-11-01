import type { AppState } from '../api'
import { Row } from '../shell/controls'
import { Select } from '../shell/fields'
import type { Settings } from '../shell/useSettings'
import { Changed, pendingOf } from './parts'

export function showNetworkConnection(state: AppState): boolean {
  const { setup } = state
  if (setup.pcWifi || setup.ethernet === undefined) return false
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
  if (!state.setup.ethernet.length) return 'Connect a network cable first'
  if (!state.setup.ethernetSelected) return 'Select an Ethernet adapter first'
  return null
}
