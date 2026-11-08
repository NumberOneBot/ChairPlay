import { describe, expect, it, vi } from 'vitest'
import { handOverFrom, isTab, pageIdFrom, withoutHandOver } from './page'

describe('page', () => {
  it('names the window, the controls window and a shared page, and gives a tab a fresh id', () => {
    expect(pageIdFrom({ pathname: '/controls', search: '' })).toBe('controls')
    expect(pageIdFrom({ pathname: '/', search: '?window' })).toBe('window')
    expect(pageIdFrom({ pathname: '/', search: '?shared' })).toBe('shared')
    expect(pageIdFrom({ pathname: '/', search: '' }, () => 'abc')).toBe('abc')
  })

  // A phone on the home network opens http://<address>/, no secure context: crypto.randomUUID is not there.
  it('makes an id the app takes, with or without a secure context', () => {
    const ids = [pageIdFrom({ pathname: '/', search: '' })]
    const random = globalThis.crypto
    vi.stubGlobal('crypto', { getRandomValues: random.getRandomValues.bind(random) })
    ids.push(pageIdFrom({ pathname: '/', search: '' }), pageIdFrom({ pathname: '/', search: '' }))
    vi.unstubAllGlobals()
    for (const id of ids) expect(id).toMatch(/^[A-Za-z0-9-]{1,64}$/)
    expect(ids[1]).not.toBe(ids[2])
  })

  it('tells tabs from the other pages', () => {
    expect(isTab('abc')).toBe(true)
    expect(isTab('window')).toBe(false)
    expect(isTab('shared')).toBe(false)
    expect(isTab('controls')).toBe(false)
  })

  it('reads the hand-over key, and takes it out of the address', () => {
    expect(handOverFrom('?handover=k1')).toBe('k1')
    expect(handOverFrom('')).toBeNull()
    expect(withoutHandOver('?handover=k1')).toBe('')
    expect(withoutHandOver('?shared&handover=k1')).toBe('?shared')
  })
})
