// The Connection page's "Settings browser" group: the address the settings open at on this PC, its port (ui.port)
// applying at the next start, and Open (the window's page only), which opens the default browser there;
// a word when ui.port was taken at start and this run serves them on another port; and ui.network, which opens them
// to the local network at this PC's addresses (listed, as links, while this run listens on them).
import type { AppState, Backend } from '../api'
import { Group, Message, Row, Switch } from '../shell/controls'
import { BrowserIcon } from '../shell/icons'
import { PrefixField } from '../shell/PrefixField'
import type { Settings } from '../shell/useSettings'
import { Changed, pendingOf } from './parts'

export function BrowserAccess({ state, backend, settings }: { state: AppState; backend: Backend; settings: Settings }) {
  const { port, configuredPort, network, urls } = state.uiServer
  return (
    <>
      <Group title="Settings browser" />
      {/* configuredPort 0 (CHAIRPLAY_UI_PORT=0) asks for any free port, so none was taken from it. */}
      {configuredPort > 0 && port !== configuredPort && (
        <Message
          tone="warn"
          head={`Port ${configuredPort} is in use.`}
          text={`This run uses port ${port}. To keep a fixed address, close the program that uses port ${configuredPort} or change the port below.`}
        />
      )}
      <Row label="This PC" title="The address that opens the settings in a browser on this PC; a new port applies after restart">
        {/* A field, not a link: a link would take the window's own page away. */}
        <PrefixField
          prefix="http://127.0.0.1:"
          label="Port"
          value={settings.value('ui.port')}
          placeholder="7529"
          error={settings.error('ui.port')}
          onCommit={(text) => settings.save({ 'ui.port': text })}
        />
        <Changed keys={['ui.port']} pending={pendingOf(state)} />
        {/* A tab is in a browser already. */}
        {state.embedded && (
          <button
            type="button"
            className="btn small"
            title="Open the settings in the default browser"
            onClick={() => void backend.openInBrowser()}
          >
            <BrowserIcon />
            Open
          </button>
        )}
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
          {/* In the window a link would take its own page away: the app opens the default browser there instead. */}
          <a
            href={url}
            target="_blank"
            rel="noreferrer"
            title={state.embedded ? 'Open the settings at this address in the default browser' : 'Open the settings at this address in a new tab'}
            onClick={(event) => {
              if (!state.embedded) return
              event.preventDefault()
              void backend.openInBrowser(url)
            }}
          >
            {url}
          </a>
        </Row>
      ))}
    </>
  )
}
