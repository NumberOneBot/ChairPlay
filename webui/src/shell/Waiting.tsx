import icon from '@icons/chairplay-256.png'
import type { AppState, Backend } from '../api'
import { describe } from '../status'

/**
 * What the main window shows while the app connects on its own: black, as CarPlay's picture will be, with the app's
 * icon and where the connection stands. The settings come when an attempt fails, or at once from Settings.
 */
export function Waiting({ state, backend }: { state: AppState; backend: Backend }) {
  const status = describe(state)
  // As the settings do, it fades to black in step with the window as CarPlay covers it.
  const away = state.embedded && state.screen === 'carplay'
  return (
    <div className={`shell waiting ${away ? 'away' : ''}`}>
      <img src={icon} alt="" />
      <div role="status">
        <p className="title">{status.title}</p>
        <p className="detail">{status.detail}</p>
      </div>
      <button type="button" className="btn small" title="Open the settings" onClick={() => backend.show('settings')}>
        Settings
      </button>
    </div>
  )
}
