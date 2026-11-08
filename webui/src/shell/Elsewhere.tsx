import type { AppState } from '../api'

/**
 * What a browser tab shows in place of the settings while another place holds them (SettingsLock.kt): where they are
 * open, and what lets this tab have them. A moment before it takes them, while nobody holds them, it is still loading.
 */
export function Elsewhere({ state }: { state: AppState }) {
  const { holder } = state.lock
  if (holder === null) return <p className="app-message">Loading…</p>
  const [title, detail] =
    holder === 'window'
      ? ["The settings are open in ChairPlay's window", 'To use them here, show CarPlay in that window or click Open in browser on its Connection page.']
      : ['The settings are open in another browser tab', 'Close that tab or switch away from it to use them here.']
  return (
    <div className="app-message elsewhere" role="status">
      <div>
        <p className="title">{title}</p>
        <p className="detail">{detail}</p>
      </div>
    </div>
  )
}
