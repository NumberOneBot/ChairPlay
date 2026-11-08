import { useEffect } from 'react'
import type { Backend } from '../api'
import { isTab } from '../page'

const HEARTBEAT_MILLIS = 2000

/**
 * A browser tab's presence for the settings lock: it reports at once, on every change of its visibility, every two
 * seconds while visible, and that it goes as it closes or reloads. A hidden tab says so once and stops, so it lets
 * the settings go. The window, the controls window and shared pages never report.
 */
export function usePresence(backend: Pick<Backend, 'presence'>, page: string): void {
  useEffect(() => {
    if (!isTab(page)) return
    let heartbeat: number | undefined
    const report = () => {
      window.clearInterval(heartbeat)
      heartbeat = undefined
      const visible = document.visibilityState !== 'hidden'
      void backend.presence(visible)
      if (visible) heartbeat = window.setInterval(() => void backend.presence(true), HEARTBEAT_MILLIS)
    }
    // A page kept in the back/forward cache may come back; going hidden has already let the settings go.
    const leave = (event: PageTransitionEvent) => {
      if (event.persisted) return
      window.clearInterval(heartbeat)
      void backend.presence(false, true)
    }
    report()
    document.addEventListener('visibilitychange', report)
    window.addEventListener('pagehide', leave)
    return () => {
      window.clearInterval(heartbeat)
      document.removeEventListener('visibilitychange', report)
      window.removeEventListener('pagehide', leave)
    }
  }, [backend, page])
}
