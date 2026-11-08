// The Connection page's "Browser access" group: the address the settings open at in a browser on this PC, and a word
// when ui.port was taken at start and this run serves them on another port.
import type { AppState } from '../api'
import { Group, Message, Row } from '../shell/controls'

export function BrowserAccess({ state }: { state: AppState }) {
  const { port, configuredPort } = state.uiServer
  return (
    <>
      <Group title="Browser access" />
      {port !== configuredPort && (
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
    </>
  )
}
