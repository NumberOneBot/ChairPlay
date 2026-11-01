import type { ReactNode } from 'react'
import type { AppState, Backend } from '../api'
import { SECURITY_CHOICES } from '../pages/ConnectionPage'
import { AddRule, CodeLine, CopyButton, OpenFolder } from '../pages/messages'
import { Row } from '../shell/controls'
import { Field, PasswordField, Select } from '../shell/fields'
import { ShieldIcon } from '../shell/icons'
import type { Settings } from '../shell/useSettings'
import type { Steps, StepState } from './steps'
import { NetworkConnection, showNetworkConnection } from '../pages/NetworkConnection'

function Tick() {
  return (
    <svg viewBox="0 0 12 12" aria-hidden="true">
      <path
        d="M2.5 6.3l2.4 2.4 4.6-5"
        fill="none"
        stroke="currentColor"
        strokeWidth="1.6"
        strokeLinecap="round"
        strokeLinejoin="round"
      />
    </svg>
  )
}

/** One step: its number, or a tick once done, its title and note; what to do shows while it is the open one. */
function Step(props: { n: number; state: StepState; title: string; note: string; children: ReactNode }) {
  return (
    <div className={`step ${props.state}`}>
      <span className="mark">{props.state === 'done' ? <Tick /> : props.n}</span>
      <div className="st">
        <span>{props.title}</span>
        <span className="note">{props.note}</span>
      </div>
      {props.state === 'now' && <div className="bd">{props.children}</div>}
    </div>
  )
}

function Waiting({ children }: { children: string }) {
  return <span className="waiting">{children}</span>
}

/**
 * The main window before the first connection: steps the app checks on its own, the first one not done open
 * with what to do. Once all are done, You're all set says what comes next, and the toolbar's Connect lights up.
 */
export function GetStarted(props: {
  state: AppState
  backend: Backend
  settings: Settings
  steps: Steps
  /** A password saved: a join that timed out before it no longer reopens the Wi-Fi step. */
  onPasswordSaved: () => void
  onSkip: () => void
}) {
  const { state, backend, settings, steps } = props
  const { setup } = state
  // The network starts as the one this PC is on, and is saved with the password.
  const network = settings.value('wifi.ssid') || setup.pcWifi || ''
  const problem = steps.wifiProblem
  // Omit checks that need no action, and number only the visible steps.
  const mfi = steps.mfi
  const firewall = steps.firewall
  const mfiCount = mfi ? 1 : 0
  const optionalCount = mfiCount + (firewall ? 1 : 0)
  const stepCount = ['Two', 'Three', 'Four'][optionalCount]
  return (
    <div className="setup">
      <h2>Get started</h2>
      <div className="sub">{stepCount} steps, once. ChairPlay checks each one on its own.</div>
      {mfi && (
        <Step n={1} title="MFi files" {...mfi}>
          <span>Put identity.pk8 and certificate.p7b in this folder. ChairPlay finds them on its own.</span>
          <div className="acts">
            <CodeLine text={setup.mfiFolder}>
              <OpenFolder backend={backend} />
            </CodeLine>
          </div>
          <Waiting>Waiting for the files…</Waiting>
        </Step>
      )}
      {firewall && (
        <Step n={1 + mfiCount} title="Firewall" {...firewall}>
          {setup.firewall === 'blocked' ? (
            <>
              <span>
                A rule in Windows Defender Firewall blocks ChairPlay, so the iPhone cannot reach it. Delete the rule, or
                set it to Allow, then come back.
              </span>
              <div className="acts">
                <button
                  type="button"
                  className="btn"
                  title="Open Windows Defender Firewall with Advanced Security"
                  onClick={() => backend.open('firewall-rules')}
                >
                  <ShieldIcon />
                  Open the firewall's rules
                </button>
                <Waiting>Waiting for the rule to change…</Waiting>
              </div>
            </>
          ) : (
            <>
              <span>
                Windows Firewall is on and has no rule for ChairPlay, so it may drop the iPhone's connections.
              </span>
              <AddRule backend={backend} />
              <div className="acts">
                <CodeLine text={setup.firewallCommands}>
                  <CopyButton text={setup.firewallCommands} />
                </CodeLine>
              </div>
            </>
          )}
        </Step>
      )}
      <Step n={1 + optionalCount} title="Pair the iPhone" {...steps.pair}>
        <span>
          Turn on Bluetooth on the iPhone, then pair it with this PC in Settings › Bluetooth &amp; devices › Add device.
        </span>
        <div className="acts">
          <button
            type="button"
            className="btn"
            title="Open Bluetooth settings"
            onClick={() => backend.open('bluetooth')}
          >
            Open Bluetooth settings
          </button>
          <Waiting>Waiting for a paired iPhone…</Waiting>
        </div>
      </Step>
      <Step n={2 + optionalCount} title={showNetworkConnection(state) ? 'Connection' : 'iPhone Wi-Fi'} {...steps.wifi}>
        <NetworkConnection state={state} settings={settings} />
        {problem?.kind === 'failed' ? (
          <span className="errtext">
            The iPhone didn’t join “{problem.ssid}”. Check the password and save it again.
          </span>
        ) : problem?.kind === 'other-network' ? (
          <span className="errtext">
            This PC is on “{problem.pcWifi}”. The iPhone has to join the network this PC is on.
          </span>
        ) : (
          <span>
            Enter the router’s Wi-Fi network for the iPhone. ChairPlay sends these details to the phone when
            CarPlay connects.
          </span>
        )}
        <Row label="Network">
          <Field
            label="Network"
            className={`w200 ${problem?.kind === 'other-network' ? 'err' : ''}`}
            value={network}
            placeholder="Network name"
            error={settings.error('wifi.ssid')}
            onCommit={(text) => settings.save({ 'wifi.ssid': text })}
          />
          <Select
            label="Security"
            className="w120"
            value={settings.value('wifi.security') || 'WPA_WPA2'}
            options={SECURITY_CHOICES}
            onChange={(chosen) => settings.save({ 'wifi.security': chosen })}
          />
        </Row>
        <Row label="Password">
          <PasswordField
            label="Password"
            set={state.wifi.passphraseSet}
            error={settings.error('wifi.passphrase')}
            saved={() => backend.savedPassphrase()}
            onCommit={async (text) => {
              const changes: Record<string, string> = { 'wifi.passphrase': text }
              if (!settings.value('wifi.ssid') && network) changes['wifi.ssid'] = network
              const saved = await settings.save(changes)
              if (saved) props.onPasswordSaved()
              return saved
            }}
          />
        </Row>
      </Step>
      {steps.allDone && (
        <div className="step done all-set">
          <span className="mark">
            <Tick />
          </span>
          <div className="st">
            <span>You’re all set</span>
          </div>
          <div className="bd">
            <span>
              Keep the iPhone unlocked and nearby, then press <span className="ref">Connect</span> at the top right.
            </span>
            <span>When the iPhone asks to use CarPlay with ChairPlay, tap Use CarPlay.</span>
          </div>
        </div>
      )}
      <button
        type="button"
        className="skip"
        title="Open the settings; this guide comes back until an iPhone has connected"
        onClick={props.onSkip}
      >
        Skip to settings
      </button>
    </div>
  )
}
