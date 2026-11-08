import { describe, expect, it } from 'vitest'
import { devApi } from '../devApi'

type Listener = (proxyRequest: { removeHeader(name: string): void }, request: { headers: Record<string, string> }) => void

/** The headers the proxy drops from a request the dev server got with [headers]. */
function dropped(headers: Record<string, string>): string[] {
  const listeners: Listener[] = []
  devApi('7529').proxy['/api'].configure({ on: (_event: 'proxyReq', listener: Listener) => listeners.push(listener) })
  const removed: string[] = []
  for (const listener of listeners) listener({ removeHeader: (name) => removed.push(name) }, { headers })
  return removed
}

describe('the dev server', () => {
  // With Vite's default, a page on another localhost port could read the dev server's (so the app's) answers.
  it('lets no other origin read its answers', () => {
    expect(devApi('7529').cors).toBe(false)
  })

  it('sends the API to the app', () => {
    expect(devApi('7529').proxy['/api'].target).toBe('http://127.0.0.1:7529')
  })

  // The app takes only its own origin; the dev server's page is its stand-in, another site's page is not.
  it("drops the Origin of its own page only, so the app refuses other sites' POSTs", () => {
    expect(dropped({ host: 'localhost:5173', origin: 'http://localhost:5173' })).toEqual(['origin'])
    expect(dropped({ host: '127.0.0.1:5173', origin: 'http://127.0.0.1:5173' })).toEqual(['origin'])
    expect(dropped({ host: 'localhost:5173', origin: 'http://evil.example' })).toEqual([])
    expect(dropped({ host: 'localhost:5173', origin: 'http://localhost:3000' })).toEqual([])
    expect(dropped({ host: 'localhost:5173' })).toEqual([])
  })
})
