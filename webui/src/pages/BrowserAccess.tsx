// The Connection page's "Settings in a browser" group: Open in browser, which hands the window's settings to a tab;
// the address the settings open at in a browser on this PC, its port (ui.port) a field that applies at the next start;
// a word when ui.port was taken at start and this run serves them on another port; and ui.network, which opens them
// to the local network at this PC's addresses (listed while this run listens on them).
import type { AppState, Backend } from '../api'
import { Group, Message, Row, Switch } from '../shell/controls'
import { Field } from '../shell/fields'
import { BrowserIcon } from '../shell/icons'
import type { Settings } from '../shell/useSettings'
import { Changed, pendingOf } from './parts'

export function BrowserAccess({ state, backend, settings }: { state: AppState; backend: Backend; settings: Settings }) {
  const { port, configuredPort, network, urls } = state.uiServer
  // Only the window holding the settings has them to hand over; a tab is in a browser already.
  const canHandOver = state.embedded && state.lock.mine
  return (
    <>
      <Group title="Settings in a browser" />
      {/* configuredPort 0 (CHAIRPLAY_UI_PORT=0) asks for any free port, so none was taken from it. */}
      {configuredPort > 0 && port !== configuredPort && (
        <Message
          tone="warn"
          head={`Port ${configuredPort} is in use.`}
          text={`This run uses port ${port}. To keep a fixed address, close the program that uses port ${configuredPort} or change ui.port in settings.properties.`}
        />
      )}
      {state.embedded && (
        <Row>
          <button
            type="button"
            className="btn small"
            disabled={!canHandOver}
            title={canHandOver ? 'Open the settings in the default browser' : 'Close the settings in the browser first'}
            onClick={() => void backend.openInBrowser()}
          >
            <BrowserIcon />
            Open in browser
          </button>
        </Row>
      )}
      <Row label="This PC" title="The address that opens the settings in a browser on this PC; a new port applies after restart">
        {/* Text to select and copy, not a link: a link would take the window's own page away. */}
        <code>http://127.0.0.1:</code>
        <Field
          label="Port"
          className="n"
          value={settings.value('ui.port')}
          placeholder="7529"
          error={settings.error('ui.port')}
          onCommit={(text) => settings.save({ 'ui.port': text })}
        />
        <code>/</code>
        <Changed keys={['ui.port']} pending={pendingOf(state)} />
      </Row>
      <Row title="Let phones and other PCs on this network open the settings at this PC's address">
        <Switch
          label="Allow access from the local network"
          checked={settings.value('ui.network') === 'true'}
          onChange={(checked) => settings.save({ 'ui.network': String(checked) })}
        />
        <Changed keys={['ui.network']} pending={pendingOf(state)} />
      </Row>
      {!network && (
        <Row>
          <span className="hint">Only this PC can open the settings</span>
        </Row>
      )}
      {network && urls.length === 0 && (
        <Row label="Local network">
          <span className="hint">No local network address found. Connect this PC to the network</span>
        </Row>
      )}
      {network && urls.map((url, index) => (
        <Row key={url} label={index === 0 ? 'Local network' : undefined}>
          <code>{url}</code>
        </Row>
      ))}
    </>
  )
}
