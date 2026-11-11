import type { AppState, Backend } from '../api'
import type { Settings } from '../shell/useSettings'
import { About, AboutHead, Version } from './About'

/**
 * What the app is: its head, the build with the update checks, the credits, and where to get help (owner, 2026-11-11).
 * It never waits for the settings; the update switch is off until they are in.
 */
export function AboutPage({
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
      <About state={state} backend={backend} onShowGuide={onShowGuide} />
    </>
  )
}
