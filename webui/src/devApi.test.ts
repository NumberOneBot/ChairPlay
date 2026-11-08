import { describe, expect, it } from 'vitest'
import { devApi } from '../devApi'

describe('the dev server', () => {
  // With Vite's default, a page on another localhost port could read the dev server's (so the app's) answers.
  it('lets no other origin read its answers', () => {
    expect(devApi('7529').cors).toBe(false)
  })

  it('sends the API to the app', () => {
    expect(devApi('7529').proxy['/api'].target).toBe('http://127.0.0.1:7529')
  })

  // The app refuses a request naming another host than its own.
  it('names the app as the host', () => {
    expect(devApi('7529').proxy['/api'].changeOrigin).toBe(true)
  })
})
