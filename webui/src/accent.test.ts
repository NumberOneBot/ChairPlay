import { afterEach, describe, expect, it } from 'vitest'
import { applyAccent, applyTint } from './accent'

afterEach(() => {
  document.documentElement.style.removeProperty('--accent')
  document.documentElement.removeAttribute('data-tint')
})

describe('applyTint', () => {
  // ui.tint picks the palette of greys the stylesheet keeps for each data-tint (tints.css).
  it('names the tint where the stylesheet reads it', () => {
    applyTint('slate')

    expect(document.documentElement.dataset.tint).toBe('slate')
  })

  it('takes black for a tint no palette has', () => {
    applyTint('slate')
    applyTint('pink')

    expect(document.documentElement.dataset.tint).toBe('black')
  })
})

describe('applyAccent', () => {
  // ui.accent colours both pages: every shade in the stylesheet is made from --accent.
  it('puts the accent colour where the stylesheet reads it', () => {
    applyAccent('#30d158')

    expect(document.documentElement.style.getPropertyValue('--accent')).toBe('#30d158')
  })

  it('keeps the stylesheet own colour for anything that is not #rrggbb', () => {
    applyAccent('#30d158')
    applyAccent('url(evil)')
    applyAccent(undefined)

    expect(document.documentElement.style.getPropertyValue('--accent')).toBe('#30d158')
  })
})
