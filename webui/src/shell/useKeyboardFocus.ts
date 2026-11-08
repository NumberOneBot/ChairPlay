import { useEffect } from 'react'
import type { Backend } from '../api'

/**
 * A random id for this page. crypto.randomUUID exists only in a secure context: a page opened at this PC's network
 * address (http://192.168.x.y:7529) has getRandomValues alone.
 */
function clientId(): string {
  if (typeof crypto.randomUUID === 'function') return crypto.randomUUID()
  return Array.from(crypto.getRandomValues(new Uint8Array(16)), (byte) => byte.toString(16).padStart(2, '0')).join('')
}

/** A short lease also covers settings opened in a separate browser process. */
export function useKeyboardFocus(backend: Backend) {
  useEffect(() => {
    const client = clientId()
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
