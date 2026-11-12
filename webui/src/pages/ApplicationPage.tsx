import { DEFAULT_TINT } from '../accent'
import type { AppState, Backend } from '../api'
import { Group, Row, Segmented, type Choice } from '../shell/controls'
import type { Settings } from '../shell/useSettings'
import { BrowserAccess } from './BrowserAccess'
import { ExperimentsPage } from './ExperimentsPage'
import { WindowSettings } from './WindowSettings'

/** ui.tint's palettes (styles/tints.css), shown as the Theme: the light one first, slate the default (DEFAULT_TINT). */
const TINTS: readonly Choice[] = [
  ['white', 'White'],
  ['black', 'Black'],
  ['graphite', 'Graphite'],
  ['slate', 'Slate'],
  ['teal', 'Teal'],
  ['dracula', 'Dracula'],
]

/**
 * The app's own settings: its windows, its look, the settings browser, then what was the Experiments page, its logs,
 * integrations and HUD demo (owner, 2026-11-11). Which build this is and help are on About.
 */
export function ApplicationPage({ state, backend, settings }: { state: AppState; backend: Backend; settings: Settings }) {
  return (
    <>
      <WindowSettings settings={settings} />
      <Group title="Appearance" />
      <Row label="Theme">
        <Segmented
          label="Theme"
          value={settings.value('ui.tint') || DEFAULT_TINT}
          options={TINTS}
          onChange={(tint) => settings.save({ 'ui.tint': tint })}
          testId="app-tint"
        />
      </Row>
      <BrowserAccess state={state} backend={backend} settings={settings} />
      <ExperimentsPage state={state} backend={backend} settings={settings} />
    </>
  )
}
