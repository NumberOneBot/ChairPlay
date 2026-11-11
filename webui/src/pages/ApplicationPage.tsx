import type { AppState, Backend } from '../api'
import { Group, Row, Segmented, type Choice } from '../shell/controls'
import type { Settings } from '../shell/useSettings'
import { About, AboutHead, Version } from './About'
import { BrowserAccess } from './BrowserAccess'
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
 * What the app is and its own settings: the build with the update checks, its windows, its look, and the settings
 * browser, then the credits and where to get help. These settings all apply at once but the settings browser's, which
 * mark their own. The settings wait for the app's answer; which build this is, and help, never do (the update switch
 * is off until the settings are in).
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
      <Version state={state} backend={backend} settings={settings} />
      {settings.data && (
        <>
          <WindowSettings settings={settings} />
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
          <BrowserAccess state={state} backend={backend} settings={settings} />
        </>
      )}
      <About state={state} backend={backend} onShowGuide={onShowGuide} />
    </>
  )
}
