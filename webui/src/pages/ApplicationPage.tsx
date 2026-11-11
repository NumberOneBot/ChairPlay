import type { AppState, Backend } from '../api'
import { Group, Row, Segmented, type Choice } from '../shell/controls'
import type { Settings } from '../shell/useSettings'
import { About, AboutHead } from './About'
import { BrowserAccess } from './BrowserAccess'
import { UpdateSettings } from './UpdateSettings'
import { WindowSettings } from './WindowSettings'

/** ui.tint's palettes (styles/tints.css), black the default. */
const TINTS: readonly Choice[] = [
  ['black', 'Black'],
  ['graphite', 'Graphite'],
  ['slate', 'Slate'],
  ['teal', 'Teal'],
  ['warm', 'Warm'],
]

/**
 * The app's own settings and what it is: its look, its windows, the settings browser and updates, then the build,
 * the credits, and where to get help. These settings all apply at once but the settings browser's, which mark their
 * own. The settings wait for the app's answer; which build this is, and help, never do.
 */
export function ApplicationPage({
  state,
  backend,
  settings,
  onShowGuide,
}: {
  state: AppState
  backend: Backend
  settings: Settings
  onShowGuide: () => void
}) {
  return (
    <>
      <AboutHead />
      {settings.data && (
        <>
          <Group title="Appearance" />
          <Row label="Tint">
            <Segmented
              label="Tint"
              value={settings.value('ui.tint') || 'black'}
              options={TINTS}
              onChange={(tint) => settings.save({ 'ui.tint': tint })}
              testId="app-tint"
            />
          </Row>
          <WindowSettings settings={settings} />
          <BrowserAccess state={state} backend={backend} settings={settings} />
        </>
      )}
      <Group title="Updates" />
      <UpdateSettings backend={backend} settings={settings} />
      <About state={state} backend={backend} onShowGuide={onShowGuide} />
    </>
  )
}
