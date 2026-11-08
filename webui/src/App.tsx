import { useEffect, useMemo, useState } from 'react'
import { applyAccent } from './accent'
import type { AppState } from './api'
import { Api } from './apiClient'
import { ControlsScreen } from './ControlsScreen'
import { Shell } from './shell/Shell'

export function App() {
  const api = useMemo(() => new Api(), [])
  const [state, setState] = useState<AppState | null>(null)
  const [lost, setLost] = useState(false)
  // ChairPlay's window opens the page at ?window (ChairPlayUi.kt). Any other page is a browser tab: never in the
  // window, nor waiting there, black. Esc, the fade as CarPlay covers the window, and Open (the browser) are the window's.
  const windowPage = useMemo(() => new URLSearchParams(window.location.search).has('window'), [])

  useEffect(
    () => api.subscribe((next) => setState(windowPage ? next : { ...next, embedded: false, waiting: false }), setLost),
    [api, windowPage],
  )
  useEffect(() => applyAccent(state?.accent), [state?.accent])

  // The controls window opens /controls; everything else is the main window's settings.
  const controlsPage = window.location.pathname === '/controls'
  if (controlsPage) {
    return state ? <ControlsScreen state={state} backend={api} /> : <div className="controls-screen" />
  }
  if (!state) {
    return <p className="app-message">{lost ? 'The app is not answering.' : 'Loading…'}</p>
  }
  return (
    <>
      <Shell state={state} backend={api} />
      {lost && (
        <div className="lost" role="status">
          Lost contact with the app. Trying again…
        </div>
      )}
    </>
  )
}
