import { useEffect } from 'react'
import type { Backend } from '../api'

/**
 * Asks the app to read the setup again (Bluetooth, the network, the firewall, and the MFi files) as the page opens and
 * each time the window has the focus back, as the user returns from Windows Settings or File Explorer. The app reads
 * nothing on its own; what it finds comes with the next app state.
 */
export function useSetupChecks(backend: Backend) {
  useEffect(() => {
    const check = () => {
      backend.checkSetup().catch(() => {})
    }
    check()
    window.addEventListener('focus', check)
    return () => window.removeEventListener('focus', check)
  }, [backend])
}
