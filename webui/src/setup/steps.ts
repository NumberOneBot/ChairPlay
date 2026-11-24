import type { AppState } from '../api'
import { errorKind } from '../status'
import { networkWaits } from '../pages/NetworkConnection'
import { t } from '../i18n/texts'

export type StepState = 'done' | 'now' | 'todo'

export interface Step {
  state: StepState
  /** What the step's line says at its right end. */
  note: string
}

/** What the Wi-Fi step has to say instead of its explanation, when something is wrong. */
export type WifiProblem = { kind: 'failed'; ssid: string } | { kind: 'other-network'; pcWifi: string } | null

export interface Steps {
  /** Null when both MFi files are already in place: no user action is needed. */
  mfi: Step | null
  /** Null while the firewall is off: there is nothing to let ChairPlay through, and the guide is a step shorter. */
  firewall: Step | null
  pair: Step
  /** The iPhone Forget was pressed for, while Windows still has it paired: the pair step asks to remove it there. */
  pairForgotten: string | null
  wifi: Step
  wifiProblem: WifiProblem
  allDone: boolean
  /** Why Connect waits, for its tooltip; null once every step is done. */
  connectWaits: string | null
}

/**
 * Get started's checks: each done, open now (the first one not done), or still to do.
 * Present MFi files and a disabled firewall need no visible step.
 * A join that timed out reopens the Wi-Fi step, since the password is the likely cause, until a password is saved
 * after it ([joinFailureSeen] is that error's time).
 * The iPhone [forgotten] by Forget does not count as paired while Windows still lists it: its pairing is stale.
 */
export function setupSteps(state: AppState, joinFailureSeen: string | null, forgotten: string | null = null): Steps {
  const { setup, wifi, error } = state
  const pairForgotten = forgotten !== null && setup.pairedIphone === forgotten ? forgotten : null
  const firewallDone = setup.firewall !== 'missing' && setup.firewall !== 'blocked'
  const failed =
    error !== null && state.phase !== 'active' && errorKind(error.message) === 'join' && error.at !== joinFailureSeen
  const passwordNeeded = wifi.security !== 'NONE' && !wifi.passphraseSet
  const otherNetwork = wifi.ssid !== '' && setup.pcWifi !== null && setup.pcWifi !== wifi.ssid
  const networkProblem = networkWaits(state)
  const wifiDone = wifi.ssid !== '' && !passwordNeeded && !otherNetwork && !failed && !networkProblem
  const paired = setup.pairedIphone !== null
  const done = [setup.mfiPresent, firewallDone, paired && pairForgotten === null, wifiDone]
  const open = done.indexOf(false)
  // Connect stays open to a forgotten iPhone Windows lists: it may be paired again already, which only a call shows.
  const connectOpen = [setup.mfiPresent, firewallDone, paired, wifiDone].indexOf(false)
  const stateOf = (index: number): StepState => (done[index] ? 'done' : index === open ? 'now' : 'todo')
  const firewallNote = {
    present: 'Rule added',
    unknown: 'Not checked',
    missing: 'Rule missing',
    // The iPhone gets in; only another device's way to the settings waits for the rule (the Connection page says so).
    partial: 'Rule added',
    blocked: 'Blocked by a rule',
    off: 'Firewall off',
  }[setup.firewall]
  const wifiNote = wifiDone
    ? wifi.ssid
    : networkProblem ? 'Connection needed' : failed
      ? 'iPhone didn’t join'
      : otherNetwork
        ? 'Not this PC’s network'
        : wifi.ssid === ''
          ? 'Not set'
          : 'Password needed'
  const waits = [
    'Put the MFi files in place first',
    'Let ChairPlay through the firewall first',
    'Pair the iPhone first',
    networkProblem ?? (failed ? 'Check the Wi-Fi password first' : 'Enter the iPhone Wi-Fi network first'),
  ]
  return {
    mfi: setup.mfiPresent ? null : { state: stateOf(0), note: 'Not found' },
    firewall: setup.firewall === 'off' ? null : { state: stateOf(1), note: firewallNote },
    pair: { state: stateOf(2), note: pairForgotten ? t('pair.stillPaired') : setup.pairedIphone ?? 'No iPhone paired' },
    pairForgotten,
    wifi: { state: stateOf(3), note: wifiNote },
    wifiProblem: failed
      ? { kind: 'failed', ssid: wifi.ssid }
      : otherNetwork
        ? { kind: 'other-network', pcWifi: setup.pcWifi! }
        : null,
    allDone: open === -1,
    connectWaits: connectOpen === -1 ? null : waits[connectOpen],
  }
}
