import type { AppState, Backend } from '../api'
import { Group, Row, Switch, type Choice } from '../shell/controls'
import { Field, PasswordField, Select } from '../shell/fields'
import { InfoIcon } from '../shell/icons'
import type { Settings } from '../shell/useSettings'
import { ErrorMessage, SetupMessages } from './messages'
import { Changed, pendingOf } from './parts'
import { NetworkConnection, showNetworkConnection, WifiHidden } from './NetworkConnection'
import { useSetupChecks } from '../setup/useSetupChecks'

/** wifi.security as DesktopConfig names it, and as the page shows it. */
export const SECURITY_CHOICES: readonly Choice[] = [
  ['WPA_WPA2', 'WPA/WPA2'],
  ['WPA3_TRANSITION', 'WPA2/WPA3'],
  ['WPA3_ONLY', 'WPA3 only'],
  ['WEP', 'WEP'],
  ['NONE', 'Open'],
]

/**
 * Above the network and its password, here and in Get started: ChairPlay makes no network of its own, so these name one
 * that already exists and that both this PC and the iPhone can reach. On this page it is a hint lined up with the
 * fields; in a Get started step ([inStep]), it is the step's own text, in its place, size, and color.
 */
export function ExistingNetworkHint({ inStep = false }: { inStep?: boolean }) {
  const hint = (
    <span className={inStep ? 'icon-hint' : 'hint icon-hint'}>
      <InfoIcon />
      <span>
        Use a Wi-Fi network you already have, one that both this PC and the iPhone can reach. ChairPlay doesn't create
        one.
      </span>
    </span>
  )
  return inStep ? hint : <Row className="hint-row above">{hint}</Row>
}

/** What is wrong, if anything; then the Wi-Fi the iPhone joins, and how the iPhone is called and shown. */
export function ConnectionPage(props: {
  state: AppState
  backend: Backend
  settings: Settings
  onShowGuide?: () => void
}) {
  const { state, backend, settings } = props
  const { value, error, save } = settings
  useSetupChecks(backend)
  const commit = (key: string) => (text: string) => save({ [key]: text })
  // A changed setting that waits for a restart or the next connection is marked beside its control; the others not.
  const pending = pendingOf(state)
  const later = (key: string) => <Changed keys={[key]} pending={pending} />
  const field = (key: string, label: string, placeholder: string, className = 'w200') => (
    <Field
      label={label}
      className={className}
      value={value(key)}
      placeholder={placeholder}
      error={error(key)}
      onCommit={commit(key)}
    />
  )
  return (
    <>
      <ErrorMessage state={state} backend={backend} onShowGuide={props.onShowGuide} />
      <SetupMessages state={state} backend={backend} onShowGuide={props.onShowGuide} />
      {showNetworkConnection(state) && <>
        <Group title="PC connection" note={(state.setup.ethernet?.length ?? 0) > 1
          ? 'Applies at the next connection' : undefined} />
        <NetworkConnection state={state} settings={settings} />
      </>}
      <Group title="Wi-Fi network" note="Sent to the iPhone during Bluetooth pairing" />
      <WifiHidden state={state} backend={backend} />
      <ExistingNetworkHint />
      <Row label="Network">
        {field('wifi.ssid', 'Network', 'Network name')}
        <Select
          label="Security"
          className="w120"
          value={value('wifi.security') || 'WPA_WPA2'}
          options={SECURITY_CHOICES}
          onChange={commit('wifi.security')}
        />
        <Changed keys={['wifi.ssid', 'wifi.security']} pending={pending} />
        {error('wifi.security') && <span className="errtext">{error('wifi.security')}</span>}
      </Row>
      <Row label="Password">
        <PasswordField
          label="Password"
          set={state.wifi.passphraseSet}
          error={error('wifi.passphrase')}
          onCommit={commit('wifi.passphrase')}
          saved={() => backend.savedPassphrase()}
        />
        {later('wifi.passphrase')}
      </Row>
      <Group title="iPhone" />
      {state.setup.pairedIphone ? (
        <Row label="Paired iPhone">
          <span>{state.setup.pairedIphone}</span>
          <button
            type="button"
            className="btn small forget"
            title="Forget this iPhone: it pairs again at its next connection after a restart, and Get started opens"
            onClick={() => backend.forget()}
          >
            Forget
          </button>
        </Row>
      ) : (
        <Row label="Paired iPhone" title="The message above says how to pair one">
          <span className="hint">None</span>
        </Row>
      )}
      <Row
        label="Name on iPhone"
        title="The name the iPhone shows for this car in CarPlay settings and in its Wi-Fi list"
      >
        {field('device.name', 'Name on iPhone', 'ChairPlay')}
        {later('device.name')}
      </Row>
      <Row label="Bluetooth address" title="Only needed when more than one iPhone is paired">
        {field('iphone.address', 'Bluetooth address', 'Any paired iPhone')}
        {later('iphone.address')}
      </Row>
      <Row
        label="Time to join Wi-Fi"
        title="How long a known iPhone has to join the Wi-Fi before ChairPlay tries again"
      >
        {field('iphone.join.seconds', 'Time to join Wi-Fi', '30', 'n')}
        <span className="unit">s</span>
        {later('iphone.join.seconds')}
      </Row>
      <Row>
        <Switch
          label="Connect on startup"
          checked={value('ui.autoconnect') !== 'false'}
          onChange={(checked) => save({ 'ui.autoconnect': String(checked) })}
        />
        {later('ui.autoconnect')}
      </Row>
      <Row>
        <Switch
          label="Show CarPlay on connect"
          checked={value('ui.autoshow') !== 'false'}
          onChange={(checked) => save({ 'ui.autoshow': String(checked) })}
        />
        {later('ui.autoshow')}
      </Row>
    </>
  )
}
