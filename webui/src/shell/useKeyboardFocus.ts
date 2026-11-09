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

/** The app takes the lease as lost 2 s after the last renewal: a closed or crashed page cannot hold it. */
const RENEW_MS = 1000

/**
 * While a browser tab has the focus, global shortcuts stay off, so that what is typed there is not also a CarPlay key.
 * The page renews a short lease only while focused, and only while [global] shortcuts are on. The app's own window
 * (?window) needs none: Windows shows it as the app's, whose keys never count as pressed in the background.
 */
export function useKeyboardFocus(backend: Backend, global: boolean) {
  useEffect(() => {
    if (!global || new URLSearchParams(window.location.search).has('window')) return
    const client = clientId()
    let timer: number | undefined
    const send = (focused: boolean) => { void backend.keyboardFocus(client, focused).catch(() => {}) }
    const report = () => {
      const focused = document.hasFocus() && document.visibilityState !== 'hidden'
      if (focused === (timer !== undefined)) return
      send(focused)
      if (focused) timer = window.setInterval(() => send(true), RENEW_MS)
      else { window.clearInterval(timer); timer = undefined }
    }
    report()
    window.addEventListener('focus', report)
    window.addEventListener('blur', report)
    document.addEventListener('visibilitychange', report)
    return () => {
      window.removeEventListener('focus', report)
      window.removeEventListener('blur', report)
      document.removeEventListener('visibilitychange', report)
      if (timer !== undefined) { window.clearInterval(timer); send(false) }
    }
  }, [backend, global])
}
