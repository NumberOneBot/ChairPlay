// The Connection page's "Browser access" group: the address the settings open at in a browser on this PC, a word
// when ui.port was taken at start and this run serves them on another port, and ui.network, which opens them to the
// home network at this PC's addresses (listed while this run listens on them).
import type { AppState } from '../api'
import { Group, Message, Row, Switch } from '../shell/controls'
import type { Settings } from '../shell/useSettings'
import { Changed, pendingOf } from './parts'

export function BrowserAccess({ state, settings }: { state: AppState; settings: Settings }) {
  const { port, configuredPort, network, urls } = state.uiServer
  return (
    <>
      <Group title="Browser access" />
      {/* configuredPort 0 (CHAIRPLAY_UI_PORT=0) asks for any free port, so none was taken from it. */}
      {configuredPort > 0 && port !== configuredPort && (
        <Message
          tone="warn"
          head={`Port ${configuredPort} is in use.`}
          text={`This run uses port ${port}. To keep a fixed address, close the program that uses port ${configuredPort} or change ui.port in settings.properties.`}
        />
      )}
      <Row label="This PC">
        {/* Text to select and copy, not a link: a link would take the window's own page away. */}
        <code>{`http://127.0.0.1:${port}/`}</code>
      </Row>
      <Row title="Let phones and other PCs on this network open the settings at this PC's address">
        <Switch
          label="Allow access from the home network"
          checked={settings.value('ui.network') === 'true'}
          onChange={(checked) => settings.save({ 'ui.network': String(checked) })}
        />
        <Changed keys={['ui.network']} pending={pendingOf(state)} />
      </Row>
      {network && urls.length === 0 && (
        <Row label="Home network">
          <span className="hint">No home network address found</span>
        </Row>
      )}
      {network && urls.map((url, index) => (
        <Row key={url} label={index === 0 ? 'Home network' : undefined}>
          <code>{url}</code>
        </Row>
      ))}
    </>
  )
}
