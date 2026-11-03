import { useEffect } from 'react'
import type { Backend } from '../api'

/** A short lease also covers settings opened in a separate browser process. */
export function useKeyboardFocus(backend: Backend) {
  useEffect(() => {
    const client = crypto.randomUUID()
    const report = () => { void backend.keyboardFocus(client, document.hasFocus()).catch(() => {}) }
    report()
    const timer = window.setInterval(report, 500)
    window.addEventListener('focus', report)
    window.addEventListener('blur', report)
    document.addEventListener('visibilitychange', report)
    return () => {
      window.clearInterval(timer)
      window.removeEventListener('focus', report)
      window.removeEventListener('blur', report)
      document.removeEventListener('visibilitychange', report)
      void backend.keyboardFocus(client, false).catch(() => {})
    }
  }, [backend])
}
