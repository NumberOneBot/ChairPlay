// What is wrong, above the Connection page's settings: the last error, and the setup problems the app finds. Each
// names the problem, then what to do, then a button for whatever there is to open or copy.
import { useState, type ReactNode } from 'react'
import type { AppState, Backend } from '../api'
import { Message } from '../shell/controls'
import { ShieldIcon } from '../shell/icons'
import { errorKind, type ErrorKind } from '../status'

/** A path or command to read or copy, and its button beside it. */
export function CodeLine({ text, children }: { text: string; children: ReactNode }) {
  return (
    <>
      <div className="code" title={text}>
        {text}
      </div>
      {children}
    </>
  )
}

export function CopyButton({
  text,
  label = 'Copy',
  title = 'Copy the command',
  testId,
}: {
  text: string
  label?: string
  title?: string
  testId?: string
}) {
  const [copied, setCopied] = useState(false)
  return (
    <button
      type="button"
      className="btn small"
      title={title}
      data-testid={testId}
      onClick={async () => {
        await navigator.clipboard.writeText(text)
        setCopied(true)
      }}
    >
      {copied ? 'Copied' : label}
    </button>
  )
}

/** The way to Get started from a message about pairing: a link, as the only thing in a message that is not a button. */
function GuideLink({ onShowGuide }: { onShowGuide?: () => void }) {
  if (!onShowGuide) return null
  return (
    <span>
      or go through{' '}
      <button type="button" className="link" title="Go through the setup steps again" onClick={onShowGuide}>
        Get started
      </button>
    </span>
  )
}

/** The bring-up's last error, until a session is active; the raw message is its tooltip. */
export function ErrorMessage({
  state,
  backend,
  onShowGuide,
}: {
  state: AppState
  backend: Backend
  onShowGuide?: () => void
}) {
  const { error, setup } = state
  if (!error || state.phase === 'active') return null
  const kind = errorKind(error.message)
  // Files that are not there, and an iPhone that is not paired, have their own message.
  if (kind === 'mfi' && !setup.mfiPresent) return null
  if (kind === 'bluetooth' && !setup.pairedIphone) return null
  const messages: Record<ErrorKind, [head: string, text: string, actions: ReactNode]> = {
    join: [
      "The iPhone didn't join the Wi-Fi.",
      'It answered over Bluetooth but did not come onto the network. Check the network and the password below, and that this PC is on that network.',
      null,
    ],
    bluetooth: [
      "Couldn't reach the iPhone.",
      'No paired iPhone answered over Bluetooth. Make sure the iPhone is paired, nearby, and has Bluetooth turned on.',
      <div className="acts">
        <button
          type="button"
          className="btn small"
          title="Open Bluetooth settings"
          onClick={() => backend.open('bluetooth')}
        >
          Open Bluetooth settings
        </button>
        <GuideLink onShowGuide={onShowGuide} />
      </div>,
    ],
    wifi: [
      "Couldn't open the Wi-Fi.",
      'Check the network below, and that this PC is connected to it.',
      <div className="acts">
        <button type="button" className="btn small" title="Open Wi-Fi settings" onClick={() => backend.open('wifi')}>
          Open Wi-Fi settings
        </button>
      </div>,
    ],
    mfi: [
      'The MFi files were refused.',
      'Check identity.pk8 and certificate.p7b in this folder:',
      <CodeLine text={setup.mfiFolder}>
        <OpenFolder backend={backend} />
      </CodeLine>,
    ],
    other: ["Couldn't connect.", error.message, null],
  }
  const [head, text, actions] = messages[kind]
  return (
    <Message tone="bad" head={head} text={text} at={error.at} title={error.message}>
      {actions}
    </Message>
  )
}

export function OpenFolder({ backend }: { backend: Backend }) {
  return (
    <button
      type="button"
      className="btn small"
      title="Open the folder in File Explorer"
      onClick={() => backend.open('mfi-folder')}
    >
      Open
    </button>
  )
}

/**
 * Add the rule, with Windows' shield as it asks for the administrator's consent; the copied command is the other way.
 * The message goes once the live checks find the rule; a refusal says so.
 */
export function AddRule({ backend }: { backend: Backend }) {
  const [refused, setRefused] = useState(false)
  // Windows' consent prompt can take a while; a second click meanwhile would ask again.
  const [waiting, setWaiting] = useState(false)
  return (
    <div className="acts">
      <button
        type="button"
        className="btn small"
        title={
          waiting ? 'Waiting for your consent' : 'Add an inbound rule for ChairPlay.exe; Windows asks for your consent'
        }
        disabled={waiting}
        onClick={async () => {
          setWaiting(true)
          try {
            setRefused(!(await backend.addFirewallRule()))
          } finally {
            setWaiting(false)
          }
        }}
      >
        <ShieldIcon />
        Add the rule
      </button>
      {refused ? (
        <span className="errtext">The rule wasn't added. Run the command in Terminal (Admin) instead.</span>
      ) : (
        <span>or run this in Terminal (Admin):</span>
      )}
    </div>
  )
}

/** The setup problems the app finds: the MFi files, and the firewall; each goes once it is fixed. */
export function SetupMessages({
  state,
  backend,
  onShowGuide,
}: {
  state: AppState
  backend: Backend
  onShowGuide?: () => void
}) {
  const { setup } = state
  return (
    <>
      {!setup.pairedIphone && (
        <Message
          tone="warn"
          head="No iPhone is paired with this PC."
          text="On the iPhone, open Settings › Bluetooth. On this PC, open Settings › Bluetooth & devices › Add device › Bluetooth, choose the iPhone, and confirm the code on both."
        >
          <div className="acts">
            <button
              type="button"
              className="btn small"
              title="Open Bluetooth settings"
              onClick={() => backend.open('bluetooth')}
            >
              Open Bluetooth settings
            </button>
            <GuideLink onShowGuide={onShowGuide} />
          </div>
        </Message>
      )}
      {!setup.mfiPresent && (
        <Message tone="warn" head="MFi files not found." text="Put identity.pk8 and certificate.p7b in this folder:">
          <CodeLine text={setup.mfiFolder}>
            <OpenFolder backend={backend} />
          </CodeLine>
        </Message>
      )}
      {/* Only our rule is missing: another one (Windows' own, from its first prompt) may still let the iPhone in. */}
      {setup.firewall === 'missing' && (
        <Message
          tone="warn"
          head="No firewall rule for ChairPlay."
          text="If the iPhone cannot connect, let ChairPlay in through Windows Firewall."
        >
          <AddRule backend={backend} />
          <CodeLine text={setup.firewallCommands}>
            <CopyButton text={setup.firewallCommands} />
          </CodeLine>
        </Message>
      )}
      {/* ui.network is on, and our rule, added before it, lets the iPhone in but not another device to the settings. */}
      {setup.firewall === 'partial' && (
        <Message
          tone="warn"
          head="ChairPlay's firewall rule doesn't open the settings' port."
          text="To open the settings from another device, add the rule again."
        >
          <AddRule backend={backend} />
          <CodeLine text={setup.firewallCommands}>
            <CopyButton text={setup.firewallCommands} />
          </CodeLine>
        </Message>
      )}
      {setup.firewall === 'unknown' && (
        <Message
          tone="warn"
          head="The firewall's rules could not be read."
          text="If the iPhone cannot connect, run this in Terminal (Admin):"
        >
          <CodeLine text={setup.firewallCommands}>
            <CopyButton text={setup.firewallCommands} />
          </CodeLine>
        </Message>
      )}
      {setup.firewall === 'blocked' && (
        <Message
          tone="warn"
          head="A firewall rule blocks ChairPlay."
          text="Delete the rule, or set it to Allow, in the firewall's Inbound Rules."
        >
          <div className="acts">
            <button
              type="button"
              className="btn small"
              title="Open Windows Defender Firewall with Advanced Security"
              onClick={() => backend.open('firewall-rules')}
            >
              <ShieldIcon />
              Open the firewall's rules
            </button>
          </div>
        </Message>
      )}
    </>
  )
}
