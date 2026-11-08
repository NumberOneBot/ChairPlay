// Which page this is, for the settings lock (SettingsLock.kt): ChairPlay's window (its address has ?window), the
// controls window (/controls), a shared page (?shared, which neither takes the settings nor obeys the lock), or a
// browser tab, named by an id fresh at each load (no cookie or storage, which a duplicated tab would copy).
export const WINDOW_PAGE = 'window'
export const CONTROLS_PAGE = 'controls'
export const SHARED_PAGE = 'shared'

/** The address's keys, bare (?window) or with a value (?handover=k1). */
function keys(search: string): string[] {
  return search.replace(/^\?/, '').split('&').filter(Boolean)
}

export function pageIdFrom(location: { pathname: string; search: string }, newId: () => string = randomId): string {
  if (location.pathname === '/controls') return CONTROLS_PAGE
  const names = keys(location.search).map((key) => key.split('=')[0])
  if (names.includes(WINDOW_PAGE)) return WINDOW_PAGE
  if (names.includes(SHARED_PAGE)) return SHARED_PAGE
  return newId()
}

/** The pages that take the settings by their presence; the window holds them by the app's state. */
export function isTab(page: string): boolean {
  return page !== WINDOW_PAGE && page !== CONTROLS_PAGE && page !== SHARED_PAGE
}

/** The key Open in browser gave the tab it opened, so that tab takes the settings. */
export function handOverFrom(search: string): string | null {
  return new URLSearchParams(search).get('handover')
}

export function withoutHandOver(search: string): string {
  const rest = keys(search).filter((key) => key.split('=')[0] !== 'handover')
  return rest.length ? `?${rest.join('&')}` : ''
}

// crypto.randomUUID needs a secure context, which http://<this PC's address>/ on a phone is not.
function randomId(): string {
  if (typeof crypto.randomUUID === 'function') return crypto.randomUUID()
  return Array.from(crypto.getRandomValues(new Uint8Array(16)), (byte) => byte.toString(16).padStart(2, '0')).join('')
}
