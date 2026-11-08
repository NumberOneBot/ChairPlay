import { useEffect, useMemo, useState } from 'react'
import { applyAccent } from './accent'
import type { AppState } from './api'
import { Api } from './apiClient'
import { ControlsScreen } from './ControlsScreen'
import { handOverFrom, pageIdFrom, withoutHandOver } from './page'
import { Shell } from './shell/Shell'
import { usePresence } from './shell/usePresence'

export function App() {
  // Once per load: which page this is (a tab's id is fresh at each load), and the key Open in browser gave it.
  const api = useMemo(() => new Api(pageIdFrom(window.location), handOverFrom(window.location.search)), [])
  const [state, setState] = useState<AppState | null>(null)
  const [lost, setLost] = useState(false)

  // A reload must not claim the hand-over again.
  useEffect(() => {
    const { pathname, search, hash } = window.location
    if (handOverFrom(search) !== null) history.replaceState(null, '', pathname + withoutHandOver(search) + hash)
  }, [])
  useEffect(() => api.subscribe(setState, setLost), [api])
  usePresence(api, api.page)
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
