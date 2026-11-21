import type { Backend } from '../api'
import { Message } from './controls'

/** Ukraine's flag, drawn: Windows has no flag emoji, and WebView2 shows the letters "UA" for it. */
function Flag() {
  return (
    <svg className="flag" width="18" height="12" viewBox="0 0 3 2" aria-hidden="true">
      <rect width="3" height="1" fill="#0057B7" />
      <rect y="1" width="3" height="1" fill="#FFD700" />
    </svg>
  )
}

/**
 * The coffee ask on top of every settings page while the app says it is due (AppUiApi.kt): after two hours of CarPlay,
 * until the person supported, and again three days after Later (owner, 2026-11-21).
 */
export function SupportCard({ backend }: { backend: Backend }) {
  const coffee = () => {
    backend.open('buy-me-a-coffee', 'support-card')
    backend.support('done')
  }
  return (
    <Message
      tone="info"
      testId="support-card"
      head={<><Flag /> Made in Kyiv, Ukraine</>}
      text="One developer, between air-raid alerts and power cuts. It's free and always will be. If it helps, a coffee keeps it going."
    >
      <div className="acts">
        <button type="button" className="btn small" title="Open Buy Me a Coffee in your browser" data-testid="support-coffee"
          onClick={coffee}>
          ☕ Buy me a coffee
        </button>
        <button type="button" className="btn small" title="Ask again in three days" data-testid="support-later"
          onClick={() => backend.support('later')}>
          Later
        </button>
        <button type="button" className="btn small" title="Don't ask again" data-testid="support-done"
          onClick={() => backend.support('done')}>
          I already did
        </button>
      </div>
    </Message>
  )
}
